package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.TenantRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The platform log (every superadmin action) and each firm's view of what Owlaw staff did on its account. */
@Service
public class PlatformAuditService {
    static final Set<String> FIRM_ADMINS = Set.of("owner", "partner", "director");

    private final PlatformActionRepository actions;
    private final TenantRepository tenants;

    public PlatformAuditService(PlatformActionRepository actions, TenantRepository tenants) {
        this.actions = actions;
        this.tenants = tenants;
    }

    public List<Map<String, Object>> platformLog(UUID tenantId, String action) {
        PlatformGuard.requireSuperadmin();
        List<PlatformAction> rows = tenantId == null ? actions.findTop500ByOrderByCreatedAtDesc() : actions.findTop500ByTenantIdOrderByCreatedAtDesc(tenantId);
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        Map<UUID, String> names = new HashMap<>();
        return rows.stream()
                .filter(r -> a.isEmpty() || r.getAction().toLowerCase(Locale.ROOT).contains(a))
                .map(r -> {
                    Map<String, Object> m = view(r);
                    m.put("firmName", r.getTenantId() == null ? null
                            : names.computeIfAbsent(r.getTenantId(), id -> tenants.findById(id).map(Tenant::getFirmName).orElse("Deleted firm")));
                    return m;
                })
                .toList();
    }

    public List<Map<String, Object>> staffActivity() {
        String role = TenantContext.getRole() == null ? "" : TenantContext.getRole().toLowerCase(Locale.ROOT);
        if (!FIRM_ADMINS.contains(role)) throw ApiException.forbidden("Only a firm owner or partner can see Owlaw staff activity.");
        return actions.findTop500ByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenant()).stream().map(PlatformAuditService::view).toList();
    }

    static Map<String, Object> view(PlatformAction r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("createdAt", r.getCreatedAt());
        m.put("actorEmail", r.getActorEmail());
        m.put("action", r.getAction());
        m.put("label", label(r.getAction()));
        m.put("entityType", r.getEntityType());
        m.put("entityId", r.getEntityId());
        m.put("detail", r.getDetail());
        m.put("tenantId", r.getTenantId());
        return m;
    }

    static String label(String action) {
        return switch (action) {
            case "platform.firm.suspended" -> "Suspended the firm";
            case "platform.firm.reactivated" -> "Reactivated the firm";
            case "platform.firm.trial_extended" -> "Extended the trial";
            case "platform.firm.plan_changed" -> "Changed the plan";
            case "platform.mfa.enrolled" -> "Operator turned on two-factor sign-in";
            case "platform.mfa.recovery_code_used" -> "Operator signed in with a recovery code";
            case "platform.mfa.recovery_codes_replaced" -> "Operator replaced recovery codes";
            case "support.view" -> "Viewed firm data under support access";
            case "support.grant.used" -> "Opened support access";
            default -> action.startsWith("directory.") ? "Find a Lawyer: " + action.substring(10).replace('.', ' ').replace('_', ' ')
                    : action.startsWith("public_site.") || action.startsWith("publicsite.") ? "Public site review"
                    : action.replace('.', ' ').replace('_', ' ');
        };
    }
}
