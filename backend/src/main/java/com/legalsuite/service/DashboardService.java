package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.CalendarEventRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

    public DashboardService(
            LegalCaseRepository cases,
            ClientRepository clients,
            TaskItemRepository tasks,
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            CalendarEventRepository events,
            AppNotificationRepository notifications,
            CallRecordRepository calls,
            PracticeService practice) {
        this.cases = cases;
        this.clients = clients;
        this.tasks = tasks;
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.events = events;
        this.notifications = notifications;
        this.calls = calls;
        this.practice = practice;
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
        return m;
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
