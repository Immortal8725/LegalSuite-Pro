package com.legalsuite.litigation;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppNotification;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.MatterStageEvent;
import com.legalsuite.domain.TaskItem;
import com.legalsuite.domain.Tenant;
import com.legalsuite.litigation.LitigationCatalog.Rule;
import com.legalsuite.litigation.LitigationCatalog.Stage;
import com.legalsuite.litigation.LitigationCatalog.Track;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.MatterStageEventRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LitigationService {
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final Set<Long> REMIND_AT = Set.of(7L, 3L, 1L, 0L);

    private final LitigationCatalog catalog;
    private final LegalCaseRepository cases;
    private final MatterStageEventRepository events;
    private final TaskItemRepository tasks;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final AppNotificationRepository notifications;
    private final AuditService audit;
    private final org.springframework.beans.factory.ObjectProvider<com.legalsuite.inbox.MatterInboxService> inbox;
    private final org.springframework.beans.factory.ObjectProvider<com.legalsuite.push.PushService> push;

    public LitigationService(LitigationCatalog catalog, LegalCaseRepository cases, MatterStageEventRepository events,
                             TaskItemRepository tasks, ClientRepository clients, TenantRepository tenants,
                             AppNotificationRepository notifications, AuditService audit,
                             org.springframework.beans.factory.ObjectProvider<com.legalsuite.inbox.MatterInboxService> inbox,
                             org.springframework.beans.factory.ObjectProvider<com.legalsuite.push.PushService> push) {
        this.push = push;
        this.inbox = inbox;
        this.catalog = catalog;
        this.cases = cases;
        this.events = events;
        this.tasks = tasks;
        this.clients = clients;
        this.tenants = tenants;
        this.notifications = notifications;
        this.audit = audit;
    }

    public Map<String, Object> catalogView() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("disclaimer", catalog.disclaimer());
        out.put("courtLevels", catalog.courtLevels());
        out.put("tracks", catalog.tracks());
        return out;
    }

    public Map<String, Object> courtDays(String from, int amount, String unit, boolean diesNon) {
        LocalDate start = parse(from, "from");
        LocalDate due;
        try {
            due = CourtDays.due(start, amount, unit == null ? "court_days" : unit, diesNon);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        return Map.of("from", start, "amount", amount, "unit", unit == null ? "court_days" : unit, "diesNon", diesNon, "due", due);
    }

    public Map<String, Object> matterView(UUID caseId) {
        LegalCase c = requireCase(caseId);
        return view(c);
    }

    @Transactional
    public Map<String, Object> setTrack(UUID caseId, Map<String, Object> body) {
        LegalCase c = requireCase(caseId);
        String trackId = body.get("track") == null ? null : String.valueOf(body.get("track")).trim();
        if (trackId == null || trackId.isEmpty() || "none".equals(trackId)) {
            if (!history(c).isEmpty()) throw ApiException.badRequest("This matter already has stages on its timeline. Keep the track.");
            c.setLitigationTrack(null);
            c.setLitigationStage(null);
            c.setCourtLevel("none");
        } else {
            Track t = catalog.track(trackId);
            if (t == null) throw ApiException.badRequest("Unknown litigation track \"" + trackId + "\".");
            if (c.getLitigationTrack() != null && !trackId.equals(c.getLitigationTrack()) && !history(c).isEmpty()) {
                throw ApiException.badRequest("This matter already has stages on the " + catalog.track(c.getLitigationTrack()).label()
                        + " track. Open a new matter for a different track.");
            }
            String level = body.get("courtLevel") == null ? null : String.valueOf(body.get("courtLevel")).trim();
            if (level == null || level.isEmpty()) level = c.getCourtLevel() != null && t.courtLevels().contains(c.getCourtLevel()) ? c.getCourtLevel() : t.courtLevels().get(0);
            if (!t.courtLevels().contains(level)) {
                throw ApiException.badRequest(t.label() + " runs in: " + String.join(", ", t.courtLevels()) + ".");
            }
            c.setLitigationTrack(trackId);
            c.setCourtLevel(level);
        }
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        audit.record("case.track", "case", c.getId().toString(), c.getCaseNumber() + " " + c.getLitigationTrack() + " " + c.getCourtLevel());
        return view(c);
    }

    @Transactional
    public Map<String, Object> addStage(UUID caseId, Map<String, Object> body) {
        LegalCase c = requireCase(caseId);
        Track t = catalog.track(c.getLitigationTrack());
        if (t == null) throw ApiException.badRequest("Choose a litigation track for this matter first.");
        String stage = body.get("stage") == null ? "" : String.valueOf(body.get("stage")).trim();
        LocalDate on = body.get("date") == null || String.valueOf(body.get("date")).isBlank()
                ? LocalDate.now(SAST) : parse(String.valueOf(body.get("date")), "date");
        if (on.isAfter(LocalDate.now(SAST).plusDays(1))) throw ApiException.badRequest("A stage date cannot be in the future.");
        List<MatterStageEvent> hist = history(c);
        List<String> ids = hist.stream().map(MatterStageEvent::getStage).toList();
        String problem = LitigationCatalog.transitionProblem(t, ids, stage);
        if (problem != null) throw ApiException.badRequest(problem);
        if (!hist.isEmpty() && on.isBefore(hist.get(hist.size() - 1).getEnteredOn())) {
            throw ApiException.badRequest("The date is before the previous stage (" + hist.get(hist.size() - 1).getEnteredOn() + ").");
        }
        MatterStageEvent e = new MatterStageEvent();
        e.setTenantId(c.getTenantId());
        e.setCaseId(c.getId());
        e.setTrack(t.id());
        e.setStage(stage);
        e.setEnteredOn(on);
        Object note = body.get("note");
        if (note != null) {
            String n = String.valueOf(note).trim();
            e.setNote(n.length() > 250 ? n.substring(0, 250) : n);
        }
        e.setCreatedBy(TenantContext.getUserId());
        events.save(e);
        c.setLitigationStage(stage);
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        List<Map<String, Object>> made = new ArrayList<>();
        for (Rule r : t.rules()) {
            if (!r.trigger().equals(stage)) continue;
            made.add(taskView(createDeadline(c, t, r, e)));
        }
        audit.record("case.stage", "case", c.getId().toString(), c.getCaseNumber() + " -> " + t.id() + ":" + stage + " on " + on);
        Map<String, Object> out = view(c);
        out.put("createdDeadlines", made);
        // Optional client update. Off unless the caller asks, so imports and matter creation stay quiet.
        if (Boolean.parseBoolean(String.valueOf(body.get("notifyClient"))) && c.getClientId() != null) {
            Object msg = body.get("clientMessage");
            out.put("clientMessage", inbox.getObject().postStageUpdate(c, stage, msg == null ? null : String.valueOf(msg)));
        }
        return out;
    }

    private TaskItem createDeadline(LegalCase c, Track t, Rule r, MatterStageEvent e) {
        LocalDate due = CourtDays.due(e.getEnteredOn(), r.amount(), r.unit(), r.diesNon());
        TaskItem task = new TaskItem();
        task.setTenantId(c.getTenantId());
        task.setCaseId(c.getId());
        task.setCreatedBy(TenantContext.getUserId());
        task.setAssignedTo(c.getLeadAttorneyId() == null ? TenantContext.getUserId() : c.getLeadAttorneyId());
        task.setTitle(r.title() + ": " + c.getCaseNumber());
        String unit = r.unit().replace('_', ' ');
        String desc = r.amount() + " " + unit + " from " + t.stage(r.trigger()).label().toLowerCase() + " on " + e.getEnteredOn()
                + (r.diesNon() ? " (16 Dec to 15 Jan not counted)" : "") + ". Source: " + r.source() + "."
                + (r.note() == null ? "" : " " + r.note())
                + (r.needsReview() ? " Check this period against the rule before relying on it." : "")
                + " Worked out by Owlaw, not legal advice.";
        task.setDescription(desc.length() > 3900 ? desc.substring(0, 3900) : desc);
        long days = ChronoUnit.DAYS.between(LocalDate.now(SAST), due);
        task.setStatus("todo");
        task.setPriority(days < 0 ? "urgent" : days <= 7 ? "high" : "medium");
        // Noon UTC keeps the date the same in South Africa.
        task.setDueDate(due.atTime(12, 0).toInstant(ZoneOffset.UTC));
        task.setSourceKey("stage:" + c.getId() + ":" + r.id() + ":" + e.getId());
        return tasks.save(task);
    }

    public Map<String, Object> pipeline(String trackId) {
        UUID tid = TenantContext.requireTenant();
        List<LegalCase> all = cases.findByTenantIdOrderByUpdatedAtDesc(tid);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> tracksOut = new ArrayList<>();
        for (Track t : catalog.tracks()) {
            if (trackId != null && !trackId.isBlank() && !trackId.equals(t.id())) continue;
            List<LegalCase> mine = all.stream().filter(c -> t.id().equals(c.getLitigationTrack())).toList();
            if (mine.isEmpty() && (trackId == null || trackId.isBlank())) continue;
            List<Map<String, Object>> columns = new ArrayList<>();
            List<Map<String, Object>> notStarted = new ArrayList<>();
            for (LegalCase c : mine) if (c.getLitigationStage() == null) notStarted.add(card(c));
            if (!notStarted.isEmpty()) columns.add(Map.of("stage", "not_started", "label", "Not started", "matters", notStarted));
            for (Stage s : t.stages()) {
                List<Map<String, Object>> cards = new ArrayList<>();
                for (LegalCase c : mine) if (s.id().equals(c.getLitigationStage())) cards.add(card(c));
                columns.add(Map.of("stage", s.id(), "label", s.label(), "side", s.side(), "matters", cards));
            }
            tracksOut.add(Map.of("track", t.id(), "label", t.label(), "count", mine.size(), "columns", columns));
        }
        out.put("tracks", tracksOut);
        out.put("disclaimer", catalog.disclaimer());
        return out;
    }

    private Map<String, Object> card(LegalCase c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("caseNumber", c.getCaseNumber());
        m.put("title", c.getTitle());
        m.put("status", c.getStatus());
        m.put("courtLevel", c.getCourtLevel());
        if (c.getClientId() != null) clients.findById(c.getClientId()).ifPresent(cl -> m.put("clientName", cl.displayName()));
        LocalDate next = null;
        for (TaskItem t : tasks.findByTenantIdAndCaseId(c.getTenantId(), c.getId())) {
            if (t.getSourceKey() == null || !t.getSourceKey().startsWith("stage:") || "completed".equals(t.getStatus()) || t.getDueDate() == null) continue;
            LocalDate d = t.getDueDate().atZone(SAST).toLocalDate();
            if (next == null || d.isBefore(next)) next = d;
        }
        m.put("nextDeadline", next);
        return m;
    }

    private Map<String, Object> view(LegalCase c) {
        Map<String, Object> m = new LinkedHashMap<>();
        Track t = catalog.track(c.getLitigationTrack());
        m.put("caseId", c.getId());
        m.put("track", c.getLitigationTrack());
        m.put("trackLabel", t == null ? null : t.label());
        m.put("courtLevel", c.getCourtLevel());
        m.put("stage", c.getLitigationStage());
        m.put("stageLabel", t == null || c.getLitigationStage() == null || t.stage(c.getLitigationStage()) == null ? null : t.stage(c.getLitigationStage()).label());
        List<MatterStageEvent> hist = history(c);
        List<String> ids = hist.stream().map(MatterStageEvent::getStage).toList();
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (MatterStageEvent e : hist) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("stage", e.getStage());
            Stage s = t == null ? null : t.stage(e.getStage());
            row.put("label", s == null ? e.getStage() : s.label());
            row.put("date", e.getEnteredOn());
            row.put("note", e.getNote());
            timeline.add(row);
        }
        m.put("timeline", timeline);
        List<Map<String, Object>> next = new ArrayList<>();
        if (t != null) {
            for (Stage s : t.stages()) {
                if (LitigationCatalog.transitionProblem(t, ids, s.id()) == null) {
                    next.add(Map.of("id", s.id(), "label", s.label(), "side", s.side()));
                }
            }
        }
        m.put("nextStages", next);
        List<Map<String, Object>> deadlines = new ArrayList<>();
        for (TaskItem task : tasks.findByTenantIdAndCaseId(c.getTenantId(), c.getId())) {
            if (task.getSourceKey() != null && task.getSourceKey().startsWith("stage:")) deadlines.add(taskView(task));
        }
        deadlines.sort((a, b) -> String.valueOf(a.get("dueDate")).compareTo(String.valueOf(b.get("dueDate"))));
        m.put("deadlines", deadlines);
        m.put("trackNote", t == null ? null : t.note());
        m.put("disclaimer", catalog.disclaimer());
        return m;
    }

    private Map<String, Object> taskView(TaskItem t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("status", t.getStatus());
        m.put("priority", t.getPriority());
        m.put("dueDate", t.getDueDate() == null ? null : t.getDueDate().atZone(SAST).toLocalDate());
        return m;
    }

    /** Daily at 07:15 SAST: a bell notification 7, 3, 1 and 0 days before each open stage deadline. */
    @Scheduled(cron = "0 15 7 * * *", zone = "Africa/Johannesburg")
    public void reminderJob() {
        for (Tenant t : tenants.findAll()) remind(t.getId(), LocalDate.now(SAST));
    }

    @Transactional
    public int remind(UUID tenantId, LocalDate today) {
        int sent = 0;
        for (TaskItem t : tasks.findByTenantIdOrderByDueDateAsc(tenantId)) {
            if (t.getSourceKey() == null || !t.getSourceKey().startsWith("stage:") || t.getDueDate() == null) continue;
            if ("completed".equals(t.getStatus()) || "done".equals(t.getStatus()) || t.getAssignedTo() == null) continue;
            long days = ChronoUnit.DAYS.between(today, t.getDueDate().atZone(SAST).toLocalDate());
            if (!REMIND_AT.contains(days)) continue;
            AppNotification n = new AppNotification();
            n.setTenantId(tenantId);
            n.setUserId(t.getAssignedTo());
            n.setTitle(days == 0 ? "Deadline today" : "Deadline in " + days + (days == 1 ? " day" : " days"));
            n.setBody(t.getTitle());
            n.setType("deadline");
            n.setLink(t.getCaseId() == null ? "/tasks" : "/cases/" + t.getCaseId());
            notifications.save(n);
            push.ifAvailable(p -> p.toUser(tenantId, n.getUserId(), n.getTitle(), n.getBody(), n.getLink()));
            sent++;
        }
        return sent;
    }

    private List<MatterStageEvent> history(LegalCase c) {
        return events.findByTenantIdAndCaseIdOrderByEnteredOnAscCreatedAtAsc(c.getTenantId(), c.getId());
    }

    private LegalCase requireCase(UUID id) {
        return cases.findByIdAndTenantId(id, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Matter not found"));
    }

    private static LocalDate parse(String s, String field) {
        try {
            return LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())));
        } catch (Exception e) {
            throw ApiException.badRequest("The " + field + " must be a date like 2026-11-02.");
        }
    }
}
