package com.legalsuite.security;

import com.legalsuite.common.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final com.legalsuite.repo.TenantRepository tenants;

    public JwtAuthFilter(JwtService jwtService, com.legalsuite.repo.TenantRepository tenants) {
        this.jwtService = jwtService;
        this.tenants = tenants;
    }

    private static void deny(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            String token = null;
            if (header != null && header.startsWith("Bearer ")) {
                token = header.substring(7);
            } else if (request.getParameter("token") != null) {
                token = request.getParameter("token");
            }
            if (token != null && !token.isBlank()) {
                try {
                    Claims claims = jwtService.parse(token);
                    if ("refresh".equals(claims.get("type", String.class))) {
                        throw new IllegalArgumentException("refresh token used as access token");
                    }
                    UUID userId = UUID.fromString(claims.getSubject());
                    UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
                    String role = claims.get("role", String.class);
                    String email = claims.get("email", String.class);
                    TenantContext.setUserId(userId);
                    TenantContext.setTenantId(tenantId);
                    TenantContext.setRole(role);
                    TenantContext.setEmail(email);
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "STAFF" : role.toUpperCase()))));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    String path = request.getRequestURI();
                    boolean superadmin = "superadmin".equalsIgnoreCase(role);
                    if (!superadmin && !path.equals("/api/v1/auth/logout")) {
                        String status = tenants.findById(tenantId).map(com.legalsuite.domain.Tenant::getStatus).orElse("missing");
                        if ("deleted".equals(status) || "purged".equals(status)) {
                            deny(response, "firm_deleted", DELETED_MESSAGE);
                            return;
                        }
                        if ("suspended".equals(status) || "cancelled".equals(status) || "missing".equals(status)) {
                            deny(response, "firm_suspended", SUSPENDED_MESSAGE);
                            return;
                        }
                    }
                    if (superadmin && path.startsWith("/api/v1/platform/") && !Boolean.TRUE.equals(claims.get("mfa", Boolean.class))) {
                        deny(response, "mfa_required", "Set up your authenticator app before you use the platform screens.");
                        return;
                    }
                    if (Boolean.TRUE.equals(claims.get("pwc", Boolean.class)) && !passwordChangeAllowed(request)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"success\":false,\"code\":\"password_change_required\",\"message\":\"Change your temporary password before you continue.\"}");
                        return;
                    }
                } catch (Exception ignored) {
                    // invalid token; continue unauthenticated
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    public static final String DELETED_MESSAGE =
            "This firm's Owlaw account has been deleted. Contact Owlaw at support@owlaw.co.za if you think this is a mistake.";

    public static final String SUSPENDED_MESSAGE =
            "This firm's Owlaw account is suspended. The firm's owner can contact Owlaw at support@owlaw.co.za to restore it.";

    private static boolean passwordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/password")
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/auth/logout");
    }
}
