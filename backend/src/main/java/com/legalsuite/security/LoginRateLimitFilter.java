package com.legalsuite.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Slows down password guessing on the sign-in endpoints. Only failed attempts count, so normal
 * use is never throttled. Limits apply per account (firm + email) and per client address.
 * In-memory: on more than one API instance, put the same limit on the load balancer as well.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    static final Set<String> PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/portal/login", "/api/v1/auth/invite/accept", "/api/v1/auth/totp/verify");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int maxPerAccount;
    private final int maxPerIp;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LoginRateLimitFilter(
            @Value("${legalsuite.login-limit.max-failures:8}") int maxPerAccount,
            @Value("${legalsuite.login-limit.max-failures-per-ip:40}") int maxPerIp,
            @Value("${legalsuite.login-limit.window-seconds:900}") long windowSeconds) {
        this(maxPerAccount, maxPerIp, windowSeconds, Clock.systemUTC());
    }

    LoginRateLimitFilter(int maxPerAccount, int maxPerIp, long windowSeconds, Clock clock) {
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.windowMs = windowSeconds * 1000L;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(16 * 1024);
        String ipKey = "ip:" + request.getRemoteAddr();
        String accountKey = accountKey(request.getRequestURI(), body);
        if (over(ipKey, maxPerIp) || (accountKey != null && over(accountKey, maxPerAccount))) {
            reject(response, accountKey != null && over(accountKey, maxPerAccount) ? accountKey : ipKey);
            return;
        }
        chain.doFilter(new ReplayRequest(request, body), response);
        int status = response.getStatus();
        if (status == 401 || status == 400 || status == 403) {
            record(ipKey);
            if (accountKey != null) record(accountKey);
        } else if (status < 300 && accountKey != null) {
            failures.remove(accountKey);
        }
    }

    /** Lets the controller read the body this filter already consumed. */
    private static final class ReplayRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        ReplayRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() { return body.length; }

        @Override
        public long getContentLengthLong() { return body.length; }
    }

    private boolean over(String key, int max) {
        Deque<Long> q = failures.get(key);
        if (q == null) return false;
        synchronized (q) {
            prune(q);
            return q.size() >= max;
        }
    }

    private void record(String key) {
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.millis());
        }
        if (failures.size() > 100_000) failures.clear(); // memory guard under a spray attack
    }

    private void prune(Deque<Long> q) {
        long cutoff = clock.millis() - windowMs;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }

    private void reject(HttpServletResponse response, String key) throws IOException {
        log.warn("Sign-in attempts throttled for {}", key.startsWith("ip:") ? key : "an account");
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(windowMs / 1000));
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Too many failed sign-in attempts. Wait a few minutes and try again.\"}");
    }

    private static String accountKey(String path, byte[] body) {
        try {
            JsonNode node = JSON.readTree(body);
            if (node == null) return null;
            String email = node.path("email").asText("");
            String slug = node.path("firmSlug").asText("");
            if (email.isBlank()) email = node.path("token").asText("");
            if (email.isBlank()) return null;
            return account(path, slug, email);
        } catch (Exception e) {
            return null;
        }
    }

    static String account(String path, String slug, String email) {
        return "acct:" + path + ":" + slug.trim().toLowerCase(Locale.ROOT) + ":" + email.trim().toLowerCase(Locale.ROOT);
    }
}
