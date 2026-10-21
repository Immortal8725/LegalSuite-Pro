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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    static final int PURGE_AFTER_DAYS = 30;

    public PlatformFirmService(TenantRepository tenants, AppUserRepository users, PlanRepository plans,
            RefreshTokenRepository refreshTokens, PlatformActionRepository actions, AuditService audit, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        m.put("deletedAt", t.getDeletedAt());
        m.put("deletedReason", t.getDeletedReason());
        m.put("purgeAvailableAt", t.getDeletedAt() == null ? null : t.getDeletedAt().plus(PURGE_AFTER_DAYS, ChronoUnit.DAYS));
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

    private static void requireTypedName(Tenant t, Map<String, Object> body) {
        String typed = body == null || body.get("confirmName") == null ? "" : String.valueOf(body.get("confirmName")).trim();
        if (!typed.equals(t.getFirmName() == null ? "" : t.getFirmName().trim())) {
            throw ApiException.badRequest("Type the firm's name exactly to confirm.");
        }
    }

    /** Soft delete: the firm and every user are blocked, and it drops off the directory and public site at once. */
    @Transactional
    public Map<String, Object> softDelete(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        Tenant t = firm(id);
        requireTypedName(t, body);
        if ("deleted".equals(t.getStatus()) || "purged".equals(t.getStatus())) throw ApiException.badRequest("This firm is already deleted.");
        if (!"suspended".equals(t.getStatus())) t.setStatusBeforeSuspend(t.getStatus());
        t.setStatus("deleted");
        t.setDeletedAt(Instant.now());
        t.setDeletedReason(reason);
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(t.getId())) refreshTokens.deleteByUserId(u.getId());
        audit.platform(t.getId(), "platform.firm.deleted", "tenant", t.getId().toString(), reason);
        return view(t);
    }

    @Transactional
    public Map<String, Object> restore(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        Tenant t = firm(id);
        if (!"deleted".equals(t.getStatus())) throw ApiException.badRequest("Only a deleted firm can be restored.");
        String back = t.getStatusBeforeSuspend();
        t.setStatus(back == null || back.isBlank() || "deleted".equals(back) ? "active" : back);
        t.setStatusBeforeSuspend(null);
        t.setDeletedAt(null);
        t.setDeletedReason(null);
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.platform(t.getId(), "platform.firm.restored", "tenant", t.getId().toString(), reason + " (status " + t.getStatus() + ")");
        return view(t);
    }

    /**
     * Permanent purge, 30 days after a soft delete. Removes every row that carries the firm's tenant_id and the users'
     * own rows, then anonymises the firm record. The platform action log keeps only that the purge happened.
     */
    @Transactional
    public Map<String, Object> purge(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        Tenant t = firm(id);
        requireTypedName(t, body);
        if (!"deleted".equals(t.getStatus()) || t.getDeletedAt() == null) throw ApiException.badRequest("Delete the firm first. Purge is only for deleted firms.");
        Instant allowed = t.getDeletedAt().plus(PURGE_AFTER_DAYS, ChronoUnit.DAYS);
        if (Instant.now().isBefore(allowed)) throw ApiException.badRequest("Purge opens on " + allowed + ", 30 days after the firm was deleted.");
        int rows = purgeRows(t.getId());
        String name = t.getFirmName();
        String tail = t.getId().toString().substring(0, 8);
        t.setFirmName("Purged firm " + tail);
        t.setSlug("purged-" + t.getId());
        t.setEmail(null);
        t.setPhone(null);
        t.setAddressLine1(null);
        t.setCity(null);
        t.setStatus("purged");
        t.setDeletedReason(null);
        t.setSuspendedReason(null);
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.platform(t.getId(), "platform.firm.purged", "tenant", t.getId().toString(), rows + " rows removed: " + reason);
        return Map.of("status", "purged", "rowsRemoved", rows, "formerName", name);
    }

    private static final Set<String> KEEP = Set.of("TENANTS", "PLATFORM_ACTIONS", "FLYWAY_SCHEMA_HISTORY", "PLANS");

    private int purgeRows(UUID tenantId) {
        List<String> tenantTables = new ArrayList<>();
        List<String> userTables = new ArrayList<>();
        jdbc.execute((java.sql.Connection c) -> {
            var md = c.getMetaData();
            try (var rs = md.getColumns(c.getCatalog(), c.getSchema(), "%", "%")) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    String col = rs.getString("COLUMN_NAME");
                    if (table == null || col == null || KEEP.contains(table.toUpperCase(Locale.ROOT))) continue;
                    if ("TENANT_ID".equalsIgnoreCase(col) && !tenantTables.contains(table)) tenantTables.add(table);
                    if ("USER_ID".equalsIgnoreCase(col) && !userTables.contains(table)) userTables.add(table);
                }
            }
            return null;
        });
        int rows = 0;
        List<UUID> userIds = users.findByTenantIdOrderByLastNameAsc(tenantId).stream().map(AppUser::getId).toList();
        for (String table : userTables) {
            if (tenantTables.contains(table)) continue;
            for (UUID u : userIds) rows += jdbc.update("DELETE FROM " + quote(table) + " WHERE user_id = ?", u);
        }
        for (String table : tenantTables) rows += jdbc.update("DELETE FROM " + quote(table) + " WHERE tenant_id = ?", tenantId);
        return rows;
    }

    private static String quote(String table) {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("Unexpected table name");
        return table;
    }

    static String canonical(String slug) {
        return Pricing.canonicalSlug(slug);
    }
}
