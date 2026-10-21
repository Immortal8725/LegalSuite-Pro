package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;

/** Platform screens are for the Owlaw operator only. */
public final class PlatformGuard {
    private PlatformGuard() {}

    public static final String PLATFORM_SLUG = "legalsuite";

    public static final String SUPPORT = "support";

    /** Owlaw staff accounts: the operator (superadmin) and support staff. Both need two-factor sign-in. */
    public static boolean isPlatformRole(String role) {
        return role != null && ("superadmin".equalsIgnoreCase(role) || SUPPORT.equalsIgnoreCase(role));
    }

    /**
     * Approval queues (public sites, LPC verification, directory visibility). Support staff may work these;
     * they cannot manage firms, plans or billing and have no standing access to client data.
     */
    public static void requireApprover() {
        if (!isPlatformRole(TenantContext.getRole())) throw ApiException.forbidden("This is limited to Owlaw staff.");
    }

    public static void requireSuperadmin() {
        String role = TenantContext.getRole();
        if (role == null || !"superadmin".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("This is limited to the Owlaw operator.");
        }
    }

    public static String reason(Object raw) {
        String s = raw == null ? "" : String.valueOf(raw).trim();
        if ("null".equals(s)) s = "";
        if (s.length() < 5) throw ApiException.badRequest("Give a reason of at least 5 characters. It is kept in the audit log.");
        if (s.length() > 500) throw ApiException.badRequest("Keep the reason under 500 characters.");
        return s;
    }
}
