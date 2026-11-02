package com.legalsuite.inbox;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppNotification;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.MatterMessage;
import com.legalsuite.domain.TaskItem;
import com.legalsuite.domain.Tenant;
import com.legalsuite.litigation.ClientSummaries;
import com.legalsuite.litigation.LitigationCatalog;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.mail.SmtpProperties;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.MatterMessageRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.PracticeService;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Client inbox per matter and the plain-language stage summary. Staff routes act inside the signed-in firm;
 * portal routes act only on matters whose client is the signed-in portal client.
 */
@Service
public class MatterInboxService {
    private static final Logger log = LoggerFactory.getLogger(MatterInboxService.class);
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int MAX_BODY = 4000;
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final MatterMessageRepository messages;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final DocumentFileRepository documents;
    private final TaskItemRepository tasks;
    private final AppNotificationRepository notifications;
    private final PracticeService practice;
    private final AuditService audit;
    private final ClientSummaries summaries;
    private final LitigationCatalog catalog;
    private final MailGateway mail;
    private final SmtpProperties smtp;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    private final String appUrl;

    public MatterInboxService(MatterMessageRepository messages, LegalCaseRepository cases, ClientRepository clients,
                              AppUserRepository users, TenantRepository tenants, DocumentFileRepository documents,
                              TaskItemRepository tasks, AppNotificationRepository notifications, PracticeService practice,
                              AuditService audit, ClientSummaries summaries, LitigationCatalog catalog, MailGateway mail,
                              SmtpProperties smtp, org.springframework.security.crypto.password.PasswordEncoder encoder,
                              @Value("${legalsuite.app-url:http://localhost:3000}") String appUrl) {
        this.messages = messages;
        this.cases = cases;
        this.clients = clients;
        this.users = users;
        this.tenants = tenants;
        this.documents = documents;
        this.tasks = tasks;
        this.notifications = notifications;
        this.practice = practice;
        this.audit = audit;
        this.summaries = summaries;
        this.catalog = catalog;
        this.mail = mail;
        this.smtp = smtp;
        this.encoder = encoder;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    // ---------- plain-language summary ----------

    public Map<String, Object> summary(LegalCase c, String locale) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locale", locale == null ? "en" : locale);
        m.put("labels", summaries.labels(locale));
        m.put("disclaimer", summaries.disclaimer(locale));
        var t = catalog.track(c.getLitigationTrack());
        m.put("track", c.getLitigationTrack());
        m.put("trackLabel", t == null ? null : t.label());
        m.put("stage", c.getLitigationStage());
        var s = t == null ? null : t.stage(c.getLitigationStage());
        m.put("stageLabel", s == null ? null : s.label());
        Map<String, String> text = summaries.stage(locale, c.getLitigationTrack(), c.getLitigationStage());
        m.put("means", text == null ? null : text.get("means"));
        m.put("next", text == null ? null : text.get("next"));
        m.put("needFromYou", text == null ? null : text.get("needFromYou"));
        m.put("firmNote", blankToNull(c.getClientNote()));
        m.put("nextDate", nextDate(c));
        return m;
    }

    /** The nearest open stage deadline, in client words. Null when there is none. */
    Map<String, Object> nextDate(LegalCase c) {
        LocalDate today = LocalDate.now(SAST);
        TaskItem best = null;
        for (TaskItem task : tasks.findByTenantIdAndCaseId(c.getTenantId(), c.getId())) {
            if (task.getSourceKey() == null || !task.getSourceKey().startsWith("stage:") || task.getDueDate() == null) continue;
            if ("completed".equals(task.getStatus()) || "done".equals(task.getStatus())) continue;
            if (task.getDueDate().atZone(SAST).toLocalDate().isBefore(today)) continue;
            if (best == null || task.getDueDate().isBefore(best.getDueDate())) best = task;
        }
        if (best == null) return null;
        LocalDate due = best.getDueDate().atZone(SAST).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, due);
        String what = best.getTitle() == null ? "" : best.getTitle().replaceFirst(":\\s*\\S+$", "");
        String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", due);
        m.put("what", what);
        m.put("text", LONG.format(due) + " (" + when + "): " + what + ".");
        return m;
    }

    public Map<String, Object> staffSummary(UUID caseId) {
        return summary(staffCase(caseId), "en");
    }

    @Transactional
    public Map<String, Object> setClientNote(UUID caseId, Object raw) {
        LegalCase c = staffCase(caseId);
        String note = raw == null ? null : String.valueOf(raw).trim();
        if (note != null && note.length() > 2000) throw ApiException.badRequest("Keep the note under 2,000 characters.");
        c.setClientNote(blankToNull(note));
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        audit.record("case.client_note", "case", c.getId().toString(), c.getCaseNumber() + (c.getClientNote() == null ? " cleared" : " set"));
        return summary(c, "en");
    }

    /** Text for the automatic inbox message when a matter reaches {@code stage}. Staff may edit it before sending. */
    public String stageMessageText(LegalCase c, String track, String stage) {
        var t = catalog.track(track);
        var s = t == null ? null : t.stage(stage);
        Map<String, String> text = summaries.stage("en", track, stage);
        Map<String, String> labels = summaries.labels("en");
        StringBuilder b = new StringBuilder();
        b.append("Update on your matter ").append(c.getCaseNumber()).append(": ").append(s == null ? stage : s.label()).append(".");
        if (text != null) {
            b.append("\n\n").append(labels.get("means")).append(": ").append(text.get("means"));
            b.append("\n\n").append(labels.get("next")).append(": ").append(text.get("next"));
            if (text.get("needFromYou") != null) b.append("\n\n").append(labels.get("needFromYou")).append(": ").append(text.get("needFromYou"));
        }
        if (blankToNull(c.getClientNote()) != null) b.append("\n\n").append(c.getClientNote().trim());
        b.append("\n\n").append(summaries.disclaimer("en"));
        return b.toString();
    }

    public Map<String, Object> previewStageMessage(UUID caseId, String stage) {
        LegalCase c = staffCase(caseId);
        var t = catalog.track(c.getLitigationTrack());
        if (t == null || t.stage(stage) == null) throw ApiException.badRequest("Choose a stage on this matter's track.");
        return Map.of("body", stageMessageText(c, t.id(), stage));
    }

    /** Called by the stage flow after a stage is saved. */
    @Transactional
    public Map<String, Object> postStageUpdate(LegalCase c, String stage, String edited) {
        String body = edited == null || edited.isBlank() ? stageMessageText(c, c.getLitigationTrack(), stage) : edited.trim();
        if (body.length() > MAX_BODY) body = body.substring(0, MAX_BODY);
        MatterMessage m = newMessage(c, "staff", "stage_update", body);
        messages.save(m);
        audit.record("inbox.stage_update", "case", c.getId().toString(), c.getCaseNumber() + " " + stage);
        emailClient(c);
        return view(m, true);
    }

    // ---------- staff side ----------

    @Transactional
    public Map<String, Object> staffThread(UUID caseId) {
        LegalCase c = staffCase(caseId);
        List<MatterMessage> all = messages.findByTenantIdAndCaseIdOrderByCreatedAtAsc(c.getTenantId(), c.getId());
        Instant now = Instant.now();
        for (MatterMessage m : all) {
            if ("client".equals(m.getSenderType()) && m.getReadByStaffAt() == null) {
                m.setReadByStaffAt(now);
                m.setReadByStaffId(TenantContext.getUserId());
                messages.save(m);
            }
        }
        Client client = c.getClientId() == null ? null : clients.findByIdAndTenantId(c.getClientId(), c.getTenantId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", c.getId());
        out.put("clientId", c.getClientId());
        out.put("clientName", client == null ? null : client.displayName());
        out.put("clientEmail", client == null ? null : client.getEmail());
        out.put("portalEnabled", client != null && client.isPortalEnabled() && client.getPortalPasswordHash() != null);
        out.put("emailConfigured", mail.configured());
        out.put("messages", all.stream().map(m -> view(m, true)).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> staffSend(UUID caseId, String body, MultipartFile file) {
        LegalCase c = staffCase(caseId);
        if (c.getClientId() == null) throw ApiException.badRequest("This matter has no client to message.");
        MatterMessage m = newMessage(c, "staff", "message", cleanBody(body, file));
        attach(m, c, file);
        messages.save(m);
        audit.record("inbox.send", "case", c.getId().toString(), c.getCaseNumber() + (m.getDocumentId() == null ? "" : " with file " + m.getAttachmentName()));
        emailClient(c);
        return view(m, true);
    }

    public Map<String, Object> staffUnread() {
        UUID tid = TenantContext.requireTenant();
        Map<UUID, Long> byCase = new LinkedHashMap<>();
        Map<UUID, Instant> last = new LinkedHashMap<>();
        for (MatterMessage m : messages.findByTenantIdAndSenderTypeAndReadByStaffAtIsNull(tid, "client")) {
            byCase.merge(m.getCaseId(), 1L, Long::sum);
            last.merge(m.getCaseId(), m.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (var e : byCase.entrySet()) {
            LegalCase c = cases.findByIdAndTenantId(e.getKey(), tid).orElse(null);
            if (c == null) continue;
            total += e.getValue();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("caseId", c.getId());
            r.put("caseNumber", c.getCaseNumber());
            r.put("title", c.getTitle());
            r.put("unread", e.getValue());
            r.put("lastAt", last.get(e.getKey()));
            rows.add(r);
        }
        rows.sort((a, b) -> String.valueOf(b.get("lastAt")).compareTo(String.valueOf(a.get("lastAt"))));
        return Map.of("total", total, "matters", rows);
    }

    /** Give or remove the client's portal sign-in. Attorneys and above. */
    @Transactional
    public Map<String, Object> setPortalAccess(UUID clientId, Map<String, Object> body) {
        if (Roles.rank(Roles.current()) < Roles.rank("associate")) throw ApiException.forbidden("Ask an attorney or the firm admin to set up portal access.");
        Client c = clients.findByIdAndTenantId(clientId, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Client not found"));
        boolean enabled = !"false".equals(String.valueOf(body.getOrDefault("enabled", "true")));
        if (enabled) {
            if (c.getEmail() == null || c.getEmail().isBlank()) throw ApiException.badRequest("Add the client's email first. They sign in with it.");
            Object pw = body.get("password");
            String p = pw == null ? "" : String.valueOf(pw);
            if (p.length() < 10) throw ApiException.badRequest("Use a portal password of at least 10 characters.");
            c.setPortalPasswordHash(encoder.encode(p));
            c.setPortalEnabled(true);
        } else {
            c.setPortalEnabled(false);
        }
        c.setUpdatedAt(Instant.now());
        clients.save(c);
        audit.record(enabled ? "client.portal_on" : "client.portal_off", "client", c.getId().toString(), c.displayName());
        Tenant t = tenants.findById(c.getTenantId()).orElseThrow();
        return Map.of("portalEnabled", c.isPortalEnabled(), "email", c.getEmail() == null ? "" : c.getEmail(),
                "firmSlug", t.getSlug(), "loginUrl", appUrl + "/portal/login");
    }

    // ---------- client (portal) side ----------

    public Map<String, Object> portalUnreadFor(UUID tenantId, UUID caseId) {
        return Map.of("unread", messages.countByTenantIdAndCaseIdAndSenderTypeNotAndReadByClientAtIsNull(tenantId, caseId, "client"));
    }

    @Transactional
    public Map<String, Object> portalMatter(UUID caseId) {
        LegalCase c = portalCase(caseId);
        List<MatterMessage> all = messages.findByTenantIdAndCaseIdOrderByCreatedAtAsc(c.getTenantId(), c.getId());
        Instant now = Instant.now();
        for (MatterMessage m : all) {
            if (!"client".equals(m.getSenderType()) && m.getReadByClientAt() == null) {
                m.setReadByClientAt(now);
                messages.save(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("case", Map.of("id", c.getId(), "caseNumber", String.valueOf(c.getCaseNumber()), "title", String.valueOf(c.getTitle()),
                "status", String.valueOf(c.getStatus())));
        out.put("summary", summary(c, "en"));
        out.put("messages", all.stream().map(m -> view(m, false)).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> portalSend(UUID caseId, String body, MultipartFile file) {
        LegalCase c = portalCase(caseId);
        MatterMessage m = newMessage(c, "client", "message", cleanBody(body, file));
        attach(m, c, file);
        messages.save(m);
        audit.record("inbox.client_send", "case", c.getId().toString(), c.getCaseNumber() + (m.getDocumentId() == null ? "" : " with file " + m.getAttachmentName()));
        notifyStaff(c, m);
        return view(m, false);
    }

    public DocumentFile portalAttachmentMeta(UUID caseId, UUID messageId) {
        LegalCase c = portalCase(caseId);
        MatterMessage m = messages.findByIdAndTenantIdAndCaseId(messageId, c.getTenantId(), c.getId())
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        if (m.getDocumentId() == null) throw ApiException.notFound("This message has no file.");
        return documents.findByIdAndTenantId(m.getDocumentId(), c.getTenantId()).orElseThrow(() -> ApiException.notFound("File not found"));
    }

    public Resource portalAttachment(UUID caseId, UUID messageId) {
        DocumentFile d = portalAttachmentMeta(caseId, messageId);
        return practice.download(d.getId());
    }

    // ---------- helpers ----------

    private MatterMessage newMessage(LegalCase c, String senderType, String kind, String body) {
        MatterMessage m = new MatterMessage();
        m.setTenantId(c.getTenantId());
        m.setCaseId(c.getId());
        m.setClientId(c.getClientId());
        m.setSenderType(senderType);
        m.setKind(kind);
        m.setBody(body);
        m.setSenderId(TenantContext.getUserId());
        if ("client".equals(senderType)) {
            m.setSenderName(clients.findByIdAndTenantId(c.getClientId(), c.getTenantId()).map(Client::displayName).orElse("Client"));
        } else {
            m.setSenderName(TenantContext.getUserId() == null ? "Your attorney"
                    : users.findByIdAndTenantId(TenantContext.getUserId(), c.getTenantId()).map(AppUser::getFullName).orElse("Your attorney"));
        }
        return m;
    }

    private String cleanBody(String body, MultipartFile file) {
        String b = body == null ? "" : body.trim();
        boolean hasFile = file != null && !file.isEmpty();
        if (b.isEmpty() && !hasFile) throw ApiException.badRequest("Write a message or attach a file.");
        if (b.length() > MAX_BODY) throw ApiException.badRequest("Keep the message under 4,000 characters.");
        return b;
    }

    /** Saves the file through the normal document upload (same 50 MB limit and type checks), linked to the matter. */
    private void attach(MatterMessage m, LegalCase c, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        try {
            Map<String, Object> doc = practice.upload(file, c.getId(), c.getClientId(), "client_inbox");
            m.setDocumentId(UUID.fromString(String.valueOf(doc.get("id"))));
            m.setAttachmentName(String.valueOf(doc.get("name")));
            m.setAttachmentSize(file.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be saved. Please try again.");
        }
    }

    private Map<String, Object> view(MatterMessage m, boolean staff) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("senderType", m.getSenderType());
        v.put("senderName", m.getSenderName());
        v.put("kind", m.getKind());
        v.put("body", m.getBody());
        v.put("createdAt", m.getCreatedAt());
        v.put("readByClientAt", m.getReadByClientAt());
        v.put("readByStaffAt", m.getReadByStaffAt());
        if (m.getDocumentId() != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("name", m.getAttachmentName());
            a.put("size", m.getAttachmentSize());
            if (staff) a.put("documentId", m.getDocumentId());
            v.put("attachment", a);
        }
        return v;
    }

    private void notifyStaff(LegalCase c, MatterMessage m) {
        Set<UUID> to = new LinkedHashSet<>();
        if (c.getLeadAttorneyId() != null) to.add(c.getLeadAttorneyId());
        clients.findByIdAndTenantId(c.getClientId(), c.getTenantId()).map(Client::getAssignedAttorneyId).ifPresent(to::add);
        if (to.isEmpty()) {
            for (AppUser u : users.findByTenantIdOrderByLastNameAsc(c.getTenantId())) if (Roles.isTenantAdmin(u.getRole())) to.add(u.getId());
        }
        for (UUID uid : to) {
            AppUser u = users.findByIdAndTenantId(uid, c.getTenantId()).orElse(null);
            if (u == null) continue;
            AppNotification n = new AppNotification();
            n.setTenantId(c.getTenantId());
            n.setUserId(uid);
            n.setTitle("New client message");
            n.setBody(m.getSenderName() + " on " + c.getCaseNumber() + (m.getDocumentId() == null ? "" : " (with a file)"));
            n.setType("client_message");
            n.setLink("/cases/" + c.getId() + "#inbox");
            notifications.save(n);
            sendQuietly(u.getEmail(), "New client message on " + c.getCaseNumber(),
                    m.getSenderName() + " sent a message on " + c.getCaseNumber() + " " + c.getTitle() + ".\n\n"
                            + "Open it in Owlaw: " + appUrl + "/cases/" + c.getId() + "#inbox\n");
        }
    }

    private void emailClient(LegalCase c) {
        Client client = clients.findByIdAndTenantId(c.getClientId(), c.getTenantId()).orElse(null);
        if (client == null || client.getEmail() == null || !client.isPortalEnabled()) return;
        String firm = tenants.findById(c.getTenantId()).map(Tenant::getFirmName).orElse("Your attorneys");
        // The email never carries the message itself: the client reads it after signing in.
        sendQuietly(client.getEmail(), "New message from " + firm + " about " + c.getCaseNumber(),
                "Hello " + client.displayName() + ",\n\n" + firm + " has sent you a message about your matter " + c.getCaseNumber() + ".\n\n"
                        + "Sign in to your client portal to read it: " + appUrl + "/portal/login\n\n" + firm + "\n");
    }

    private void sendQuietly(String to, String subject, String body) {
        if (to == null || to.isBlank() || !mail.configured()) return;
        try {
            mail.send(smtp.getFrom(), to, subject, body);
        } catch (RuntimeException e) {
            log.warn("Inbox email not sent: {}", e.getClass().getSimpleName());
        }
    }

    private LegalCase staffCase(UUID id) {
        return cases.findByIdAndTenantId(id, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Matter not found"));
    }

    private LegalCase portalCase(UUID id) {
        UUID tid = TenantContext.requireTenant();
        UUID clientId = TenantContext.requireUser();
        LegalCase c = cases.findByIdAndTenantId(id, tid).orElseThrow(() -> ApiException.notFound("Matter not found"));
        if (!clientId.equals(c.getClientId())) throw ApiException.notFound("Matter not found");
        return c;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
