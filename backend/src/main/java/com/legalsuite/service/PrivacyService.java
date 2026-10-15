package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DataSubjectRequest;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DataSubjectRequestRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.RefreshTokenRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POPIA self-service for data subjects.
 * Access (s 23): a client or firm user downloads their own personal information at once, as JSON or a ZIP with their files.
 * Deletion (s 24): a request is queued for a firm admin. On approval the person is anonymised. Records the firm must keep
 * (matters, invoices, payments, trust ledger, audit log; Legal Practice Act s 86 and 87, Tax Administration Act s 29,
 * FICA s 22 and 23) stay, with the person's identifying details removed. Every step is in the audit log.
 */
@Service
public class PrivacyService {
    public static final String RETENTION_NOTE =
            "Matters, invoices, payments, trust ledger entries and the audit log are kept for the periods the law requires "
                    + "(Legal Practice Act s 86-87, Tax Administration Act s 29, FICA s 22-23). They stay linked to an "
                    + "anonymised record. Contact details, notes and portal access are removed.";

    private final ClientRepository clients;
    private final AppUserRepository users;
    private final LegalCaseRepository cases;
    private final InvoiceRepository invoices;
    private final TrustTransactionRepository trust;
    private final DocumentFileRepository documents;
    private final NoteRepository notes;
    private final DataSubjectRequestRepository requests;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    @org.springframework.beans.factory.annotation.Autowired
    private com.legalsuite.storage.FileStorage storage;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    public PrivacyService(ClientRepository clients, AppUserRepository users, LegalCaseRepository cases,
                          InvoiceRepository invoices, TrustTransactionRepository trust, DocumentFileRepository documents,
                          NoteRepository notes, DataSubjectRequestRepository requests, RefreshTokenRepository refreshTokens,
                          AuditService audit) {
        this.clients = clients;
        this.users = users;
        this.cases = cases;
        this.invoices = invoices;
        this.trust = trust;
        this.documents = documents;
        this.notes = notes;
        this.requests = requests;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    // ---------- who is asking ----------

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private boolean isClient() {
        return "client".equalsIgnoreCase(TenantContext.getRole());
    }

    private Client client(UUID id) {
        return clients.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
    }

    private AppUser user(UUID id) {
        return users.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    // ---------- access (export) ----------

    public Map<String, Object> exportClient(UUID clientId) {
        Client c = client(clientId);
        UUID t = tid();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("about", "Personal information Owlaw holds for you on behalf of your law firm (POPIA s 23).");
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("id", c.getId());
        person.put("type", c.getType());
        person.put("firstName", c.getFirstName());
        person.put("lastName", c.getLastName());
        person.put("companyName", c.getCompanyName());
        person.put("email", c.getEmail());
        person.put("phone", c.getPhone());
        person.put("mobile", c.getMobile());
        person.put("address", c.getAddressLine1());
        person.put("city", c.getCity());
        person.put("province", c.getState());
        person.put("postalCode", c.getZip());
        person.put("dateOfBirth", c.getDateOfBirth());
        person.put("portalEnabled", c.isPortalEnabled());
        person.put("createdAt", c.getCreatedAt());
        out.put("client", person);
        out.put("matters", cases.findByTenantIdAndClientId(t, c.getId()).stream().map(m -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId());
            v.put("caseNumber", m.getCaseNumber());
            v.put("title", m.getTitle());
            v.put("status", m.getStatus());
            v.put("practiceArea", m.getPracticeArea());
            return v;
        }).toList());
        out.put("invoices", invoices.findByTenantIdAndClientId(t, c.getId()).stream().map(i -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("invoiceNumber", i.getInvoiceNumber());
            v.put("dateIssued", i.getDateIssued());
            v.put("status", i.getStatus());
            v.put("total", i.getTotal());
            v.put("amountPaid", i.getAmountPaid());
            return v;
        }).toList());
        out.put("trustLedger", trust.findByTenantIdAndClientId(t, c.getId()).stream().map(x -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("date", x.getCreatedAt());
            v.put("type", x.getType());
            v.put("amount", x.getAmount());
            v.put("description", x.getDescription());
            return v;
        }).toList());
        out.put("documents", clientDocuments(t, c.getId()).stream().map(d -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", d.getId());
            v.put("name", d.getName());
            v.put("category", d.getCategory());
            v.put("sizeBytes", d.getSizeBytes());
            v.put("createdAt", d.getCreatedAt());
            return v;
        }).toList());
        if (!isClient()) {
            // Firm notes are work product; they are shared with the firm admin's export, not with the portal.
            out.put("notes", notes.findByTenantIdAndClientId(t, c.getId()).stream()
                    .map(n -> Map.of("title", String.valueOf(n.getTitle()), "body", String.valueOf(n.getBody()))).toList());
        }
        out.put("requests", requestViews(requests.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(t, "client", c.getId())));
        audit.record("privacy.export", "client", c.getId().toString(), isClient() ? "by the client" : "by the firm");
        return out;
    }

    private List<DocumentFile> clientDocuments(UUID t, UUID clientId) {
        List<DocumentFile> all = new ArrayList<>(documents.findByTenantIdAndClientId(t, clientId));
        for (var m : cases.findByTenantIdAndClientId(t, clientId)) {
            for (DocumentFile d : documents.findByTenantIdAndCaseId(t, m.getId())) {
                if (all.stream().noneMatch(x -> x.getId().equals(d.getId()))) all.add(d);
            }
        }
        // The portal export only carries files that are not marked privileged.
        if (isClient()) all.removeIf(DocumentFile::isPrivileged);
        return all;
    }

    public Map<String, Object> exportUser(UUID userId) {
        AppUser u = user(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("about", "Personal information Owlaw holds about you as a member of this firm (POPIA s 23).");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", u.getId());
        p.put("email", u.getEmail());
        p.put("firstName", u.getFirstName());
        p.put("lastName", u.getLastName());
        p.put("role", u.getRole());
        p.put("title", u.getTitle());
        p.put("phone", u.getPhone());
        p.put("barNumber", u.getBarNumber());
        p.put("bio", u.getBio());
        p.put("publicProfile", u.getPublicProfile());
        p.put("lastLoginAt", u.getLastLoginAt());
        p.put("totpEnabled", u.isTotpEnabled());
        p.put("createdAt", u.getCreatedAt());
        out.put("user", p);
        out.put("requests", requestViews(requests.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(tid(), "user", u.getId())));
        audit.record("privacy.export", "user", u.getId().toString(), "self-service");
        return out;
    }

    /** ZIP with personal-information.json and the client's own (non-privileged for the portal) uploaded files. */
    public byte[] exportClientZip(UUID clientId) throws IOException {
        Map<String, Object> data = exportClient(clientId);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("personal-information.json"));
            zip.write(json.writeValueAsBytes(data));
            zip.closeEntry();
            int n = 0;
            for (DocumentFile d : clientDocuments(tid(), clientId)) {
                if (d.getStoragePath() == null || d.getStoragePath().startsWith("seed://")) continue;
                byte[] content;
                try { content = storage.read(d.getStoragePath()); } catch (IOException | RuntimeException missing) { continue; }
                String name = "documents/" + (++n) + "-" + com.legalsuite.common.UploadPolicy.safeName(d.getName(), "document");
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    // ---------- deletion requests ----------

    @Transactional
    public Map<String, Object> requestErasure(String subjectType, UUID subjectId, String reason) {
        UUID t = tid();
        String name;
        if ("client".equals(subjectType)) {
            Client c = client(subjectId);
            if (c.getStatus() != null && c.getStatus().equals("anonymised")) throw ApiException.badRequest("This client is already anonymised.");
            name = display(c);
        } else if ("user".equals(subjectType)) {
            AppUser u = user(subjectId);
            if ("erased".equals(u.getStatus())) throw ApiException.badRequest("This user is already anonymised.");
            name = (u.getFirstName() + " " + u.getLastName()).trim();
        } else {
            throw ApiException.badRequest("Unknown subject type.");
        }
        boolean open = requests.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(t, subjectType, subjectId)
                .stream().anyMatch(r -> "pending".equals(r.getStatus()));
        if (open) throw ApiException.conflict("A deletion request is already waiting for the firm.");
        DataSubjectRequest r = new DataSubjectRequest();
        r.setTenantId(t);
        r.setSubjectType(subjectType);
        r.setSubjectId(subjectId);
        r.setSubjectName(name);
        r.setKind("erasure");
        r.setRequestedBy(TenantContext.getUserId());
        r.setRequestedByRole(TenantContext.getRole());
        r.setReason(reason == null ? null : reason.length() > 2000 ? reason.substring(0, 2000) : reason);
        requests.save(r);
        audit.record("privacy.erasure.requested", subjectType, subjectId.toString(), "request " + r.getId());
        return view(r);
    }

    public List<Map<String, Object>> listRequests() {
        Roles.requireTenantAdmin("Only a firm admin can see privacy requests.");
        return requestViews(requests.findByTenantIdOrderByCreatedAtDesc(tid()));
    }

    public List<Map<String, Object>> myRequests(String subjectType, UUID subjectId) {
        return requestViews(requests.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(tid(), subjectType, subjectId));
    }

    @Transactional
    public Map<String, Object> decide(UUID requestId, boolean approve, String note) {
        Roles.requireTenantAdmin("Only a firm admin can decide privacy requests.");
        DataSubjectRequest r = requests.findByIdAndTenantId(requestId, tid())
                .orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!"pending".equals(r.getStatus())) throw ApiException.badRequest("This request was already decided.");
        r.setDecidedBy(TenantContext.getUserId());
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(note);
        if (!approve) {
            if (note == null || note.isBlank()) throw ApiException.badRequest("Give the reason for refusing (POPIA s 24(3)).");
            r.setStatus("rejected");
            requests.save(r);
            audit.record("privacy.erasure.rejected", r.getSubjectType(), r.getSubjectId().toString(), note);
            return view(r);
        }
        String outcome = "client".equals(r.getSubjectType()) ? anonymiseClient(r.getSubjectId()) : anonymiseUser(r.getSubjectId());
        r.setStatus("completed");
        r.setOutcome(outcome);
        requests.save(r);
        audit.record("privacy.erasure.completed", r.getSubjectType(), r.getSubjectId().toString(), outcome);
        return view(r);
    }

    private String anonymiseClient(UUID id) {
        Client c = client(id);
        String tag = id.toString().substring(0, 8);
        c.setFirstName("Anonymised");
        c.setLastName("client " + tag);
        if (c.getCompanyName() != null) c.setCompanyName("Anonymised client " + tag);
        c.setEmail(null);
        c.setPhone(null);
        c.setMobile(null);
        c.setAddressLine1(null);
        c.setCity(null);
        c.setState(null);
        c.setZip(null);
        c.setDateOfBirth(null);
        c.setNotes(null);
        c.setTagsJson(null);
        c.setPortalEnabled(false);
        c.setPortalPasswordHash(null);
        c.setStatus("anonymised");
        c.setUpdatedAt(Instant.now());
        clients.save(c);
        int notesRemoved = 0;
        for (var n : notes.findByTenantIdAndClientId(tid(), id)) {
            notes.delete(n);
            notesRemoved++;
        }
        int matters = cases.findByTenantIdAndClientId(tid(), id).size();
        int inv = invoices.findByTenantIdAndClientId(tid(), id).size();
        int ledger = trust.findByTenantIdAndClientId(tid(), id).size();
        return "Contact details, date of birth, notes (" + notesRemoved + ") and portal access removed. Kept and anonymised: "
                + matters + " matters, " + inv + " invoices, " + ledger + " trust ledger entries. " + RETENTION_NOTE;
    }

    private String anonymiseUser(UUID id) {
        AppUser u = user(id);
        if ("owner".equalsIgnoreCase(u.getRole())) {
            long owners = users.findByTenantIdOrderByLastNameAsc(tid()).stream()
                    .filter(x -> "owner".equalsIgnoreCase(x.getRole()) && "active".equals(x.getStatus())).count();
            if (owners <= 1) throw ApiException.badRequest("Make someone else the owner before this owner is anonymised.");
        }
        String tag = id.toString().substring(0, 8);
        u.setFirstName("Former");
        u.setLastName("member " + tag);
        u.setEmail("erased-" + id + "@invalid.owlaw");
        u.setPhone(null);
        u.setTitle(null);
        u.setBio(null);
        u.setBarNumber(null);
        u.setBarState(null);
        u.setPracticeAreasJson(null);
        u.setPublicProfile(false);
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setPasswordHash("!erased");
        u.setStatus("erased");
        users.save(u);
        refreshTokens.deleteByUserId(id);
        return "Name, email, phone, profile and sign-in removed. Time entries, matter history and the audit log keep the "
                + "anonymised name. " + RETENTION_NOTE;
    }

    // ---------- views ----------

    private static String display(Client c) {
        if (c.getCompanyName() != null && !c.getCompanyName().isBlank()) return c.getCompanyName();
        return ((c.getFirstName() == null ? "" : c.getFirstName()) + " " + (c.getLastName() == null ? "" : c.getLastName())).trim();
    }

    private List<Map<String, Object>> requestViews(List<DataSubjectRequest> list) {
        return list.stream().map(this::view).toList();
    }

    private Map<String, Object> view(DataSubjectRequest r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("subjectType", r.getSubjectType());
        v.put("subjectId", r.getSubjectId());
        v.put("subjectName", r.getSubjectName());
        v.put("kind", r.getKind());
        v.put("status", r.getStatus());
        v.put("requestedByRole", r.getRequestedByRole());
        v.put("reason", r.getReason());
        v.put("decisionNote", r.getDecisionNote());
        v.put("outcome", r.getOutcome());
        v.put("createdAt", r.getCreatedAt());
        v.put("decidedAt", r.getDecidedAt());
        v.put("retention", RETENTION_NOTE);
        return v;
    }
}
