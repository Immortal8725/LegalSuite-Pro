package com.legalsuite.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signed-in and other private responses must not sit in a shared cache.
 * A CDN in front of the API would otherwise be free to store matter, client, trust and portal JSON.
 * Public reads (firm sites, directory, blog, plans, health) are left alone so they can be cached later.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateNoStoreFilter extends OncePerRequestFilter {
    static final String NO_STORE = "private, no-store";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublicRead(request)) {
            HttpServletResponse wrapped = new HttpServletResponseWrapper(response) {
                private boolean explicit;

                @Override
                public void setHeader(String name, String value) {
                    if (isCacheControl(name)) explicit = true;
                    super.setHeader(name, value);
                }

                @Override
                public void addHeader(String name, String value) {
                    if (isCacheControl(name)) explicit = true;
                    super.addHeader(name, value);
                }

                @Override
                public ServletOutputStream getOutputStream() throws IOException {
                    stampPublic();
                    return super.getOutputStream();
                }

                @Override
                public java.io.PrintWriter getWriter() throws IOException {
                    stampPublic();
                    return super.getWriter();
                }

                private void stampPublic() {
                    if (explicit) return;
                    explicit = true;
                    if (getStatus() >= 400) {
                        super.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
                        super.setHeader(HttpHeaders.PRAGMA, "no-cache");
                    } else {
                        super.setHeader(HttpHeaders.CACHE_CONTROL, com.legalsuite.service.PublicContentCache.PUBLIC_CACHE);
                    }
                }
            };
            chain.doFilter(request, wrapped);
            return;
        }
        HttpServletResponse wrapped = new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                if (isCacheControl(name)) {
                    super.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
                    return;
                }
                super.setHeader(name, value);
            }

            @Override
            public void addHeader(String name, String value) {
                if (isCacheControl(name)) {
                    super.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
                    return;
                }
                super.addHeader(name, value);
            }

            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                stamp();
                return super.getOutputStream();
            }

            @Override
            public java.io.PrintWriter getWriter() throws IOException {
                stamp();
                return super.getWriter();
            }

            private void stamp() {
                super.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
                super.setHeader(HttpHeaders.PRAGMA, "no-cache");
            }
        };
        wrapped.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        wrapped.setHeader(HttpHeaders.PRAGMA, "no-cache");
        chain.doFilter(request, wrapped);
        if (!response.isCommitted()) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        }
    }

    /**
     * Anonymous GET/HEAD of published or static content. A bearer token on the same URL is still private,
     * so a shared cache cannot store a response that was made with a staff or portal session.
     */
    static boolean isPublicRead(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) return false;
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isBlank()) return false;
        String path = path(request);
        return path.startsWith("/api/v1/public/sites/")
                || path.startsWith("/api/v1/public/directory/")
                || path.startsWith("/api/v1/public/blog/")
                || "/api/v1/public/push/key".equals(path)
                || path.startsWith("/api/v1/landing/")
                || "/api/v1/plans".equals(path)
                || "/api/v1/modules".equals(path)
                || "/api/v1/health".equals(path);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }

    private static boolean isCacheControl(String name) {
        return HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name);
    }
}
