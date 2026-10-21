package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;

/** Platform screens are for the Owlaw operator only. */
public final class PlatformGuard {
    private PlatformGuard() {}

    public static final String PLATFORM_SLUG = "legalsuite";

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
