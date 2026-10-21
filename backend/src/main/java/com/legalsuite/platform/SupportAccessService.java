package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consent-based support access. The operator has no standing access to a firm's clients or matters. A firm owner or
 * partner can grant read-only access for up to 14 days with a reason; the operator can then view only while the grant
 * is live, every view is logged to the firm, and the firm can revoke at any time.
 */
@Service
public class SupportAccessService {
    public static final int MAX_DAYS = 14;

    private final SupportGrantRepository grants;
    private final PlatformActionRepository actions;
    private final TenantRepository tenants;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final AppUserRepository users;
    private final AuditService audit;

    public SupportAccessService(SupportGrantRepository grants, PlatformActionRepository actions, TenantRepository tenants,
            LegalCaseRepository cases, ClientRepository clients, AppUserRepository users, AuditService audit) {
        this.grants = grants;
        this.actions = actions;
        this.tenants = tenants;
        this.cases = cases;
        this.clients = clients;
        this.users = users;
        this.audit = audit;
    }

    // ---- Firm side ------------------------------------------------------------------------------

    private static void requireFirmAdmin() {
        String role = TenantContext.getRole() == null ? "" : TenantContext.getRole().toLowerCase(Locale.ROOT);
        if (!PlatformAuditService.FIRM_ADMINS.contains(role)) {
            throw ApiException.forbidden("Only a firm owner or partner can manage Owlaw support access.");
        }
    }

    public Map<String, Object> firmView() {
        requireFirmAdmin();
        UUID tid = TenantContext.requireTenant();
        Instant now = Instant.now();
        List<PlatformAction> views = actions.findTop500ByTenantIdOrderByCreatedAtDesc(tid).stream()
                .filter(a -> "support.view".equals(a.getAction())).toList();
        List<Map<String, Object>> list = grants.findByTenantIdOrderByCreatedAtDesc(tid).stream().map(g -> {
            Map<String, Object> m = view(g, now);
            m.put("views", views.stream().filter(v -> g.getId().toString().equals(v.getEntityId())).map(PlatformAuditService::view).toList());
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grants", list);
        out.put("maxDays", MAX_DAYS);
        out.put("active", list.stream().anyMatch(m -> Boolean.TRUE.equals(m.get("active"))));
        return out;
    }

    @Transactional
    public Map<String, Object> grant(Map<String, Object> body) {
        requireFirmAdmin();
        UUID tid = TenantContext.requireTenant();
        String reason = PlatformGuard.reason(body == null ? null : body.get("reason"));
        int days;
        try {
            days = Integer.parseInt(String.valueOf(body.get("days")).trim());
        } catch (Exception e) {
            throw ApiException.badRequest("Choose how many days Owlaw support may look, from 1 to " + MAX_DAYS + ".");
        }
        if (days < 1 || days > MAX_DAYS) throw ApiException.badRequest("Support access lasts 1 to " + MAX_DAYS + " days.");
        Instant now = Instant.now();
        if (grants.findByTenantIdOrderByCreatedAtDesc(tid).stream().anyMatch(g -> g.activeAt(now))) {
            throw ApiException.badRequest("Support access is already on. Revoke it first to change it.");
        }
        SupportGrant g = new SupportGrant();
        g.setTenantId(tid);
        g.setGrantedBy(TenantContext.requireUser());
        g.setGrantedByEmail(TenantContext.getEmail());
        g.setReason(reason);
        g.setScope("read_only");
        g.setCreatedAt(now);
        g.setExpiresAt(now.plus(Duration.ofDays(days)));
        grants.save(g);
        audit.recordFor(tid, "support.grant.created", "support_grant", g.getId().toString(), days + " days, read-only: " + reason);
        return firmView();
    }

    @Transactional
    public Map<String, Object> revoke(UUID id) {
        requireFirmAdmin();
        UUID tid = TenantContext.requireTenant();
        SupportGrant g = grants.findById(id).filter(x -> tid.equals(x.getTenantId()))
                .orElseThrow(() -> ApiException.notFound("Support access not found"));
        if (g.getRevokedAt() == null) {
            g.setRevokedAt(Instant.now());
            g.setRevokedByEmail(TenantContext.getEmail());
            grants.save(g);
            audit.recordFor(tid, "support.grant.revoked", "support_grant", g.getId().toString(), "revoked by " + TenantContext.getEmail());
        }
        return firmView();
    }

    static Map<String, Object> view(SupportGrant g, Instant now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("tenantId", g.getTenantId());
        m.put("reason", g.getReason());
        m.put("scope", g.getScope());
        m.put("grantedByEmail", g.getGrantedByEmail());
        m.put("createdAt", g.getCreatedAt());
        m.put("expiresAt", g.getExpiresAt());
        m.put("revokedAt", g.getRevokedAt());
        m.put("revokedByEmail", g.getRevokedByEmail());
        m.put("active", g.activeAt(now));
        return m;
    }

    // ---- Operator side ---------------------------------------------------------------------------

    public List<Map<String, Object>> activeGrants() {
        PlatformGuard.requireSuperadmin();
        Instant now = Instant.now();
        return grants.findByRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(now).stream().map(g -> {
            Map<String, Object> m = view(g, now);
            m.put("firmName", tenants.findById(g.getTenantId()).map(Tenant::getFirmName).orElse("Deleted firm"));
            return m;
        }).toList();
    }

    private SupportGrant liveGrant(UUID tenantId) {
        PlatformGuard.requireSuperadmin();
        Instant now = Instant.now();
        return grants.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().filter(g -> g.activeAt(now)).findFirst()
                .orElseThrow(() -> ApiException.forbidden("This firm has not granted Owlaw support access, or the access has ended."));
    }

    @Transactional
    public Map<String, Object> look(UUID tenantId, String section) {
        SupportGrant g = liveGrant(tenantId);
        Tenant t = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Firm not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grant", view(g, Instant.now()));
        out.put("firmName", t.getFirmName());
        out.put("readOnly", true);
        String s = section == null ? "overview" : section.trim().toLowerCase(Locale.ROOT);
        int rows;
        switch (s) {
            case "overview" -> {
                List<LegalCase> cs = cases.findByTenantIdOrderByUpdatedAtDesc(tenantId);
                out.put("matters", cs.size());
                out.put("openMatters", cs.stream().filter(c -> "open".equals(c.getStatus())).count());
                out.put("clients", clients.findByTenantIdOrderByLastNameAsc(tenantId).size());
                out.put("users", users.findByTenantIdOrderByLastNameAsc(tenantId).stream()
                        .filter(u -> !PlatformFirmService.NOT_SEATS.contains(String.valueOf(u.getRole())))
                        .map(u -> Map.of("name", u.getFullName(), "role", String.valueOf(u.getRole()), "status", String.valueOf(u.getStatus())))
                        .toList());
                rows = 1;
            }
            case "matters" -> {
                List<Map<String, Object>> list = cases.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("caseNumber", c.getCaseNumber());
                    m.put("title", c.getTitle());
                    m.put("practiceArea", c.getPracticeArea());
                    m.put("status", c.getStatus());
                    return m;
                }).toList();
                out.put("rows", list);
                rows = list.size();
            }
            case "clients" -> {
                List<Map<String, Object>> list = clients.findByTenantIdOrderByLastNameAsc(tenantId).stream().map((Client c) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", c.displayName());
                    m.put("email", c.getEmail());
                    m.put("status", c.getStatus());
                    return m;
                }).toList();
                out.put("rows", list);
                rows = list.size();
            }
            default -> throw ApiException.badRequest("Choose overview, matters or clients.");
        }
        out.put("section", s);
        audit.platform(tenantId, "support.view", "support_grant", g.getId().toString(), "Viewed " + s + " (" + rows + (rows == 1 ? " item" : " items") + ")");
        return out;
    }
}
