package com.legalsuite.payfast;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import java.util.Locale;
import java.util.Set;

public final class ProductBillingAccess {
    private static final Set<String> MANAGERS = Set.of("owner", "attorney", "director", "admin", "partner");

    private ProductBillingAccess() {}

    public static void requireStaff() {
        String role = TenantContext.getRole();
        if (role == null || role.isBlank() || "client".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("Product billing is for the firm, not the client portal");
        }
    }

    public static void requireManager() {
        requireStaff();
        String role = TenantContext.getRole() == null ? "" : TenantContext.getRole().toLowerCase(Locale.ROOT);
        if (!MANAGERS.contains(role)) {
            throw ApiException.forbidden("Only a firm owner or director can change product billing");
        }
    }

    public static boolean canManage() {
        String role = TenantContext.getRole();
        if (role == null || role.isBlank() || "client".equalsIgnoreCase(role)) return false;
        return MANAGERS.contains(role.toLowerCase(Locale.ROOT));
    }
}
