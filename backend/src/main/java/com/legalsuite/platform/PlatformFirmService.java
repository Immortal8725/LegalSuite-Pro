package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Plan;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.RefreshTokenRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.Pricing;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The operator's view of every firm: status, plan, seats and activity. No client or matter data. */
@Service
public class PlatformFirmService {
    static final Set<String> NOT_SEATS = Set.of("superadmin", "client");

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PlanRepository plans;
    private final RefreshTokenRepository refreshTokens;
    private final PlatformActionRepository actions;
    private final AuditService audit;

    public PlatformFirmService(TenantRepository tenants, AppUserRepository users, PlanRepository plans,
            RefreshTokenRepository refreshTokens, PlatformActionRepository actions, AuditService audit) {
        this.tenants = tenants;
        this.users = users;
        this.plans = plans;
        this.refreshTokens = refreshTokens;
        this.actions = actions;
        this.audit = audit;
    }

    public static boolean isPlatform(Tenant t) {
        return t != null && PlatformGuard.PLATFORM_SLUG.equals(t.getSlug());
    }

    public List<Map<String, Object>> list() {
        PlatformGuard.requireSuperadmin();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tenant t : tenants.findAll()) {
            if (isPlatform(t)) continue;
            out.add(view(t));
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("firmName")).toLowerCase(Locale.ROOT)));
        return out;
    }

    public List<Map<String, Object>> planOptions() {
        PlatformGuard.requireSuperadmin();
        return plans.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(p -> Map.<String, Object>of("slug", p.getSlug(), "name", p.getName(),
                        "priceMonthly", p.getPriceMonthly() == null ? 0 : p.getPriceMonthly()))
                .toList();
    }

    Map<String, Object> view(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        Plan plan = t.getPlanId() == null ? null : plans.findById(t.getPlanId()).orElse(null);
        List<AppUser> staff = users.findByTenantIdOrderByLastNameAsc(t.getId()).stream()
                .filter(u -> !NOT_SEATS.contains(String.valueOf(u.getRole())))
                .toList();
        long seats = staff.stream().filter(u -> "active".equals(u.getStatus())).count();
        Instant lastLogin = staff.stream().map(AppUser::getLastLoginAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        m.put("id", t.getId());
        m.put("firmName", t.getFirmName());
        m.put("slug", t.getSlug());
        m.put("email", t.getEmail());
        m.put("city", t.getCity());
        m.put("planSlug", plan == null ? null : plan.getSlug());
        m.put("planName", plan == null ? "No plan" : plan.getName());
        m.put("status", t.getStatus());
        m.put("seats", seats);
        m.put("createdAt", t.getCreatedAt());
        m.put("trialEndsAt", t.getTrialEndsAt());
        m.put("trialExpired", "trial".equals(t.getStatus()) && t.getTrialEndsAt() != null && t.getTrialEndsAt().isBefore(Instant.now()));
        m.put("lastActivityAt", lastLogin);
        m.put("suspendedReason", t.getSuspendedReason());
        m.put("suspendedAt", t.getSuspendedAt());
        m.put("lastPlatformAction", actions.findTopByTenantIdOrderByCreatedAtDesc(t.getId())
                .map(a -> a.getAction() + " on " + a.getCreatedAt()).orElse(null));
        return m;
    }

    private Tenant firm(UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (isPlatform(t)) throw ApiException.badRequest("The Owlaw platform account cannot be changed here.");
        return t;
    }

    @Transactional
    public Map<String, Object> suspend(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        Tenant t = firm(id);
        if ("suspended".equals(t.getStatus())) throw ApiException.badRequest("This firm is already suspended.");
        t.setStatusBeforeSuspend(t.getStatus());
        t.setStatus("suspended");
        t.setSuspendedReason(reason);
        t.setSuspendedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        // End every session now: refresh tokens go, and the request filter blocks live access tokens.
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(t.getId())) refreshTokens.deleteByUserId(u.getId());
        audit.platform(t.getId(), "platform.firm.suspended", "tenant", t.getId().toString(), reason);
        return view(t);
    }

    @Transactional
    public Map<String, Object> reactivate(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        Tenant t = firm(id);
        if (!"suspended".equals(t.getStatus())) throw ApiException.badRequest("This firm is not suspended.");
        String back = t.getStatusBeforeSuspend();
        t.setStatus(back == null || back.isBlank() || "suspended".equals(back) ? "active" : back);
        t.setStatusBeforeSuspend(null);
        t.setSuspendedReason(null);
        t.setSuspendedAt(null);
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.platform(t.getId(), "platform.firm.reactivated", "tenant", t.getId().toString(), reason + " (status " + t.getStatus() + ")");
        return view(t);
    }

    @Transactional
    public Map<String, Object> extendTrial(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        int days;
        try {
            days = Integer.parseInt(String.valueOf(body.get("days")).trim());
        } catch (Exception e) {
            throw ApiException.badRequest("Enter the number of days.");
        }
        if (days < 1 || days > 90) throw ApiException.badRequest("Extend by 1 to 90 days.");
        Tenant t = firm(id);
        if (!"trial".equals(t.getStatus())) throw ApiException.badRequest("Only a firm on trial can have its trial extended.");
        Instant from = t.getTrialEndsAt() == null || t.getTrialEndsAt().isBefore(Instant.now()) ? Instant.now() : t.getTrialEndsAt();
        Instant before = t.getTrialEndsAt();
        t.setTrialEndsAt(from.plus(days, ChronoUnit.DAYS));
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.platform(t.getId(), "platform.firm.trial_extended", "tenant", t.getId().toString(),
                "+" + days + " days, " + before + " to " + t.getTrialEndsAt() + ": " + reason);
        return view(t);
    }

    @Transactional
    public Map<String, Object> changePlan(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        String slug = body.get("planSlug") == null ? "" : String.valueOf(body.get("planSlug")).trim().toLowerCase(Locale.ROOT);
        Plan plan = plans.findBySlug(slug).filter(Plan::isActive).orElseThrow(() -> ApiException.badRequest("Unknown plan: " + slug));
        Tenant t = firm(id);
        String before = t.getPlanId() == null ? "none" : plans.findById(t.getPlanId()).map(Plan::getSlug).orElse("none");
        if (plan.getId().equals(t.getPlanId())) throw ApiException.badRequest("The firm is already on that plan.");
        t.setPlanId(plan.getId());
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.platform(t.getId(), "platform.firm.plan_changed", "tenant", t.getId().toString(), before + " to " + plan.getSlug() + ": " + reason);
        return view(t);
    }

    static String canonical(String slug) {
        return Pricing.canonicalSlug(slug);
    }
}
