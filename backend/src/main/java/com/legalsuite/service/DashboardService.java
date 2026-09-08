package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.CalendarEvent;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.CalendarEventRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final TaskItemRepository tasks;
    private final TimeEntryRepository timeEntries;
    private final InvoiceRepository invoices;
    private final CalendarEventRepository events;
    private final AppNotificationRepository notifications;
    private final CallRecordRepository calls;
    private final PracticeService practice;
    private final LeadRepository leads;
    private final FinanceService finance;
    private final TenantRepository tenants;

    public DashboardService(
            LegalCaseRepository cases,
            ClientRepository clients,
            TaskItemRepository tasks,
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            CalendarEventRepository events,
            AppNotificationRepository notifications,
            CallRecordRepository calls,
            PracticeService practice,
            LeadRepository leads,
            FinanceService finance,
            TenantRepository tenants) {
        this.cases = cases;
        this.clients = clients;
        this.tasks = tasks;
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.events = events;
        this.notifications = notifications;
        this.calls = calls;
        this.practice = practice;
        this.leads = leads;
        this.finance = finance;
        this.tenants = tenants;
    }

    public Map<String, Object> overview() {
        UUID tid = TenantContext.requireTenant();
        List<TimeEntry> times = timeEntries.findByTenantIdOrderByDateDesc(tid);
        BigDecimal billedHours = times.stream()
                .filter(TimeEntry::isBillable)
                .map(t -> BigDecimal.valueOf(t.getDurationMinutes()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(60), 1, java.math.RoundingMode.HALF_UP);
        BigDecimal revenue = invoices.findByTenantIdOrderByDateIssuedDesc(tid).stream()
                .map(i -> i.getAmountPaid() == null ? BigDecimal.ZERO : i.getAmountPaid())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.findByTenantIdOrderByDateIssuedDesc(tid).stream()
                .filter(i -> !List.of("paid", "void", "write_off").contains(i.getStatus()))
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("activeCases", cases.countByTenantIdAndStatusNotIn(tid, Set.of("closed", "archived", "settled")));
        m.put("totalClients", clients.countByTenantId(tid));
        m.put("pendingTasks", tasks.countByTenantIdAndStatusNot(tid, "completed"));
        m.put("hoursBilled", billedHours);
        m.put("revenueMtd", revenue);
        m.put("outstanding", outstanding);
        m.put("unreadNotifications", notifications.countByTenantIdAndUserIdAndReadFalse(tid, TenantContext.requireUser()));
        m.put("recentCases", cases.findByTenantIdOrderByUpdatedAtDesc(tid).stream().limit(6).map(practice::caseView).toList());
        m.put("upcomingEvents", events.findByTenantIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(tid, Instant.now().minusSeconds(3600))
                .stream().limit(6).map(e -> Map.of(
                        "id", e.getId(),
                        "title", e.getTitle(),
                        "startTime", e.getStartTime(),
                        "location", e.getLocation() == null ? "" : e.getLocation(),
                        "type", e.getType()
                )).toList());
        m.put("callCount", calls.findByTenantIdOrderByStartedAtDesc(tid).size());
        m.put("docket", docket(tid));
        m.put("newLeads", leads.findByTenantIdOrderByCreatedAtDesc(tid).stream()
                .filter(l -> "new".equals(l.getStatus()) || "consultation".equals(l.getStatus()))
                .count());
        var tenant = tenants.findById(tid).orElse(null);
        m.put("jurisdiction", DocketEngine.of(tenant));
        m.put("trustLabel", DocketEngine.trustLabel(tenant));
        m.put("currency", DocketEngine.currency(tenant));
        m.put("trustRecon", finance.firmRecon());
        return m;
    }

    public List<Map<String, Object>> docket(UUID tid) {
        List<Map<String, Object>> items = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (LegalCase c : cases.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            if (List.of("closed", "settled", "archived").contains(c.getStatus())) continue;
            List<Map<String, Object>> clocks = com.legalsuite.common.JsonLists.objects(c.getDocketClocksJson());
            if (clocks.isEmpty() && c.getStatuteOfLimitations() != null) {
                long days = ChronoUnit.DAYS.between(today, c.getStatuteOfLimitations());
                if (days <= 45) {
                    items.add(docketItem("sol", c, c.getStatuteOfLimitations().toString(), days,
                            "Statute of limitations", c.getSolCitation(), c.getSolReason()));
                }
            } else {
                for (Map<String, Object> clock : clocks) {
                    LocalDate date = TexasDocketRules.parseDate(clock.get("date"));
                    if (date == null) continue;
                    long days = ChronoUnit.DAYS.between(today, date);
                    if (days > 45 && days >= 0) continue;
                    String kind = String.valueOf(clock.getOrDefault("kind", "sol"));
                    items.add(docketItem(kind, c, date.toString(), days,
                            String.valueOf(clock.getOrDefault("title", kind)),
                            clock.get("citation") == null ? null : String.valueOf(clock.get("citation")),
                            clock.get("reason") == null ? null : String.valueOf(clock.get("reason"))));
                }
            }
        }
        Instant horizon = Instant.now().plus(45, ChronoUnit.DAYS);
        for (CalendarEvent e : events.findByTenantIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(tid, Instant.now().minus(2, ChronoUnit.DAYS))) {
            if (e.getStartTime() == null || e.getStartTime().isAfter(horizon)) continue;
            String type = e.getType() == null ? "meeting" : e.getType();
            if (!List.of("court_date", "deadline", "filing", "deposition", "mediation").contains(type)) continue;
            LocalDate day = LocalDate.ofInstant(e.getStartTime(), java.time.ZoneOffset.UTC);
            long days = ChronoUnit.DAYS.between(today, day);
            LegalCase matter = e.getCaseId() == null ? null : cases.findByIdAndTenantId(e.getCaseId(), tid).orElse(null);
            Map<String, Object> item = new HashMap<>();
            item.put("kind", type.equals("deadline") || type.equals("filing") ? "filing" : "hearing");
            item.put("label", e.getTitle());
            item.put("date", e.getStartTime().toString());
            item.put("daysLeft", days);
            item.put("urgency", urgency(days));
            item.put("caseId", e.getCaseId());
            item.put("caseNumber", matter == null ? null : matter.getCaseNumber());
            item.put("title", matter == null ? e.getTitle() : matter.getTitle());
            items.add(item);
        }
        items.sort(Comparator.comparingLong(m -> ((Number) m.get("daysLeft")).longValue()));
        return items.stream().limit(16).toList();
    }

    public Map<String, Object> previewDocket(Map<String, Object> body) {
        var tenant = tenants.findById(TenantContext.requireTenant()).orElseThrow();
        return DocketEngine.preview(tenant, body).asMap();
    }

    private Map<String, Object> docketItem(
            String kind, LegalCase c, String date, long days, String label, String citation, String reason) {
        Map<String, Object> item = new HashMap<>();
        item.put("kind", kind);
        item.put("label", label);
        item.put("date", date);
        item.put("daysLeft", days);
        item.put("urgency", urgency(days));
        item.put("caseId", c.getId());
        item.put("caseNumber", c.getCaseNumber());
        item.put("title", c.getTitle());
        item.put("citation", citation);
        item.put("reason", reason);
        return item;
    }

    private static String urgency(long days) {
        if (days < 0) return "overdue";
        if (days <= 7) return "soon";
        if (days <= 21) return "watch";
        return "ok";
    }

    public Map<String, Object> reports() {
        UUID tid = TenantContext.requireTenant();
        Map<String, BigDecimal> byArea = new HashMap<>();
        cases.findByTenantIdOrderByUpdatedAtDesc(tid).forEach(c -> {
            String area = c.getPracticeArea() == null ? "General" : c.getPracticeArea();
            byArea.merge(area, c.getBillingRate() == null ? BigDecimal.ZERO : c.getBillingRate(), BigDecimal::add);
        });
        return Map.of(
                "overview", overview(),
                "revenueByPracticeArea", byArea,
                "aging", Map.of("current", 0, "days30", 0, "days60", 0, "days90", 0)
        );
    }
}
