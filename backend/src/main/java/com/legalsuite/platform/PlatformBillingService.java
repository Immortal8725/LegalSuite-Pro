package com.legalsuite.platform;

import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.Plan;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.UsageInvoiceRepository;
import com.legalsuite.service.Pricing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billing overview across firms for the operator: plan, MRR in rand (excl. VAT), unpaid invoices and usage. */
@Service
public class PlatformBillingService {
    static final Set<String> ATTORNEY_ROLES = Set.of("owner", "partner", "director", "attorney", "associate");
    static final Set<String> UNPAID = Set.of("issued", "pending", "unpaid", "overdue", "failed", "sent");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PlanRepository plans;
    private final UsageInvoiceRepository usageInvoices;
    private final MinutesInvoiceRepository minutesInvoices;
    private final CallRecordRepository calls;

    public PlatformBillingService(TenantRepository tenants, AppUserRepository users, PlanRepository plans,
            UsageInvoiceRepository usageInvoices, MinutesInvoiceRepository minutesInvoices, CallRecordRepository calls) {
        this.tenants = tenants;
        this.users = users;
        this.plans = plans;
        this.usageInvoices = usageInvoices;
        this.minutesInvoices = minutesInvoices;
        this.calls = calls;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Instant monthStart = YearMonth.now(SAST).atDay(1).atStartOfDay(SAST).toInstant();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalMrr = BigDecimal.ZERO, totalUnpaid = BigDecimal.ZERO;
        int paying = 0;
        for (Tenant t : tenants.findAll()) {
            if (PlatformGuard.PLATFORM_SLUG.equals(t.getSlug())) continue;
            Plan plan = t.getPlanId() == null ? null : plans.findById(t.getPlanId()).orElse(null);
            Pricing.PlanSpec spec = Pricing.plan(plan == null ? null : plan.getSlug());
            int attorneys = 0, staff = 0;
            for (AppUser u : users.findByTenantIdOrderByLastNameAsc(t.getId())) {
                String role = String.valueOf(u.getRole());
                if (!"active".equals(u.getStatus()) || PlatformFirmService.NOT_SEATS.contains(role)) continue;
                if (ATTORNEY_ROLES.contains(role)) attorneys++; else staff++;
            }
            BigDecimal seatValue = spec.monthlyZar().multiply(BigDecimal.valueOf(attorneys))
                    .add(spec.staffSeats() ? Pricing.STAFF_SEAT_MONTHLY_ZAR.multiply(BigDecimal.valueOf(staff)) : BigDecimal.ZERO);
            boolean counts = "active".equals(t.getStatus());
            BigDecimal mrr = counts ? seatValue : BigDecimal.ZERO;

            BigDecimal unpaid = BigDecimal.ZERO;
            int unpaidCount = 0;
            for (var inv : usageInvoices.findByTenantIdOrderByPeriodDesc(t.getId())) {
                if (UNPAID.contains(String.valueOf(inv.getStatus()))) {
                    unpaid = unpaid.add(inv.getTotal() == null ? BigDecimal.ZERO : inv.getTotal());
                    unpaidCount++;
                }
            }
            for (var inv : minutesInvoices.findByTenantIdOrderByPeriodDesc(t.getId())) {
                if (UNPAID.contains(String.valueOf(inv.getStatus()))) {
                    unpaid = unpaid.add(BigDecimal.valueOf(inv.getAmountCents(), 2));
                    unpaidCount++;
                }
            }
            int minutes = 0;
            BigDecimal callCost = BigDecimal.ZERO;
            for (CallRecord c : calls.findByTenantIdOrderByStartedAtDesc(t.getId())) {
                if (c.getStartedAt() == null || c.getStartedAt().isBefore(monthStart)) break;
                if (c.getCallType() == null || !c.getCallType().startsWith("pstn")) continue;
                minutes += Math.max(0, c.getDurationSeconds()) / 60;
                callCost = callCost.add(c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost());
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getFirmName());
            m.put("status", t.getStatus());
            m.put("plan", spec.name());
            m.put("attorneySeats", attorneys);
            m.put("staffSeats", staff);
            m.put("mrr", mrr);
            m.put("listValue", seatValue);
            m.put("unpaidCount", unpaidCount);
            m.put("unpaidTotal", unpaid);
            m.put("callMinutesThisMonth", minutes);
            m.put("callChargesThisMonth", callCost);
            rows.add(m);
            totalMrr = totalMrr.add(mrr);
            totalUnpaid = totalUnpaid.add(unpaid);
            if (counts && mrr.signum() > 0) paying++;
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("mrr")).reversed()
                .thenComparing(r -> String.valueOf(r.get("name"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currency", "ZAR");
        out.put("vat", "excluded");
        out.put("totalMrr", totalMrr);
        out.put("totalUnpaid", totalUnpaid);
        out.put("payingFirms", paying);
        out.put("firms", rows);
        return out;
    }
}
