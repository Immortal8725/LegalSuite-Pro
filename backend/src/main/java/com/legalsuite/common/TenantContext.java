package com.legalsuite.common;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID getTenantId() { return TENANT.get(); }
    public static void setTenantId(UUID id) { TENANT.set(id); }
    public static UUID getUserId() { return USER.get(); }
    public static void setUserId(UUID id) { USER.set(id); }
    public static String getRole() { return ROLE.get(); }
    public static void setRole(String role) { ROLE.set(role); }
    public static String getEmail() { return EMAIL.get(); }
    public static void setEmail(String email) { EMAIL.set(email); }

    public static UUID requireTenant() {
        UUID id = TENANT.get();
        if (id == null) {
            throw ApiException.unauthorized("Missing tenant context");
        }
        return id;
    }

    public static UUID requireUser() {
        UUID id = USER.get();
        if (id == null) {
            throw ApiException.unauthorized("Missing user context");
        }
        return id;
    }

    public static void clear() {
        TENANT.remove();
        USER.remove();
        ROLE.remove();
        EMAIL.remove();
    }
}
