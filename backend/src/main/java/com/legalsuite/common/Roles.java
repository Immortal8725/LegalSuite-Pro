package com.legalsuite.common;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Role rules shared by services. Firms are the tenant boundary; these checks sit inside it. */
public final class Roles {
    /** Roles that may run the firm: team, modules, firm settings, integrations and the firm's own bill. */
    public static final Set<String> TENANT_ADMINS = Set.of("owner", "admin", "partner", "director");

    /** Seniority used to stop a user granting a role above their own. Unknown roles rank as staff. */
    private static final List<String> RANK = List.of(
            "staff", "receptionist", "paralegal", "associate", "attorney", "partner", "director", "admin", "owner");

    private Roles() {}

    public static String current() {
        String role = TenantContext.getRole();
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isTenantAdmin(String role) {
        return role != null && TENANT_ADMINS.contains(role.trim().toLowerCase(Locale.ROOT));
    }

    public static void requireTenantAdmin(String message) {
        if (!isTenantAdmin(current())) {
            throw ApiException.forbidden(message);
        }
        TenantContext.requireTenant();
    }

    public static int rank(String role) {
        if (role == null) return 0;
        int i = RANK.indexOf(role.trim().toLowerCase(Locale.ROOT));
        return Math.max(i, 0);
    }

    /** Owners may grant any firm role; other admins may grant up to their own rank, never owner. */
    public static void requireCanGrant(String target) {
        if (target == null) return;
        String me = current();
        if ("owner".equals(me)) return;
        String t = target.trim().toLowerCase(Locale.ROOT);
        if ("owner".equals(t) || rank(t) > rank(me)) {
            throw ApiException.forbidden("Only an owner can give someone a role above your own.");
        }
    }
}
