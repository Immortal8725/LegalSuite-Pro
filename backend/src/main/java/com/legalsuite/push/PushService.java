package com.legalsuite.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Web Push for staff (deadline reminders, new client messages) and portal clients (new messages, stage updates).
 * Keys come from VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY. Without them push is simply off.
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository subs;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    /** Tests switch real delivery off and read {@link #lastPayload}. */
    volatile boolean deliver = true;
    volatile String lastPayload;
    volatile int queued;

    public PushService(PushSubscriptionRepository subs,
                       @Value("${legalsuite.push.vapid-public-key:${VAPID_PUBLIC_KEY:}}") String publicKey,
                       @Value("${legalsuite.push.vapid-private-key:${VAPID_PRIVATE_KEY:}}") String privateKey,
                       @Value("${legalsuite.push.subject:${VAPID_SUBJECT:mailto:support@owlaw.co.za}}") String subject) {
        this.subs = subs;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
    }

    /** Test hooks (the bean is a proxy, so tests go through methods, not fields). */
    public void setDeliverForTests(boolean on) { this.deliver = on; }
    public int queuedForTests() { return queued; }
    public String lastPayloadForTests() { return lastPayload; }

    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    public Map<String, Object> keyView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", configured());
        m.put("publicKey", configured() ? publicKey : null);
        return m;
    }

    // ---------- subscribe ----------

    @Transactional
    public Map<String, Object> subscribeStaff(Map<String, Object> body, String userAgent) {
        return save(body, userAgent, TenantContext.requireTenant(), TenantContext.requireUser(), null);
    }

    @Transactional
    public Map<String, Object> subscribeClient(Map<String, Object> body, String userAgent) {
        return save(body, userAgent, TenantContext.requireTenant(), null, TenantContext.requireUser());
    }

    @Transactional
    public Map<String, Object> unsubscribe(Map<String, Object> body) {
        String endpoint = endpoint(body);
        UUID tid = TenantContext.requireTenant();
        UUID who = TenantContext.requireUser();
        subs.findByEndpoint(endpoint).ifPresent(s -> {
            if (tid.equals(s.getTenantId()) && (who.equals(s.getUserId()) || who.equals(s.getClientId()))) subs.delete(s);
        });
        return Map.of("subscribed", false);
    }

    public Map<String, Object> statusStaff() {
        return Map.of("enabled", configured(), "devices", subs.findByTenantIdAndUserId(TenantContext.requireTenant(), TenantContext.requireUser()).size());
    }

    public Map<String, Object> statusClient() {
        return Map.of("enabled", configured(), "devices", subs.findByTenantIdAndClientId(TenantContext.requireTenant(), TenantContext.requireUser()).size());
    }

    private Map<String, Object> save(Map<String, Object> body, String userAgent, UUID tenantId, UUID userId, UUID clientId) {
        if (!configured()) throw ApiException.badRequest("Push notifications are not switched on for Owlaw yet.");
        String endpoint = endpoint(body);
        Object keysRaw = body.get("keys");
        if (!(keysRaw instanceof Map<?, ?> keys)) throw ApiException.badRequest("The browser did not send push keys.");
        String p256dh = String.valueOf(keys.get("p256dh"));
        String auth = String.valueOf(keys.get("auth"));
        try {
            if (WebPushCrypto.b64(p256dh).length != 65 || WebPushCrypto.b64(auth).length != 16) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("The browser sent push keys Owlaw cannot use.");
        }
        PushSubscription s = subs.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setTenantId(tenantId);
        s.setUserId(userId);
        s.setClientId(clientId);
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setFailures(0);
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        subs.save(s);
        return Map.of("subscribed", true);
    }

    private static String endpoint(Map<String, Object> body) {
        Object e = body.get("endpoint");
        String s = e == null ? "" : String.valueOf(e).trim();
        if (!s.startsWith("https://") || s.length() > 1000) throw ApiException.badRequest("The browser sent a push address Owlaw cannot use.");
        return s;
    }

    // ---------- send ----------

    public void toUser(UUID tenantId, UUID userId, String title, String body, String link) {
        if (tenantId == null || userId == null) return;
        send(() -> subs.findByTenantIdAndUserId(tenantId, userId), title, body, link);
    }

    public void toClient(UUID tenantId, UUID clientId, String title, String body, String link) {
        if (tenantId == null || clientId == null) return;
        send(() -> subs.findByTenantIdAndClientId(tenantId, clientId), title, body, link);
    }

    private void send(java.util.function.Supplier<List<PushSubscription>> who, String title, String body, String link) {
        if (!configured()) return;
        String payload;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("title", title);
            p.put("body", body == null ? "" : body.length() > 300 ? body.substring(0, 300) : body);
            p.put("url", link == null ? "/" : link);
            payload = json.writeValueAsString(p);
        } catch (Exception e) {
            return;
        }
        lastPayload = payload;
        Runnable job = () -> {
            List<PushSubscription> list;
            try {
                list = who.get();
            } catch (RuntimeException e) {
                log.warn("Push lookup failed: {}", e.getClass().getSimpleName());
                return;
            }
            for (PushSubscription s : list) {
                queued++;
                if (deliver) deliver(s, payload);
            }
        };
        // Send after the surrounding transaction commits, so a rolled-back change never pings a phone.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Thread.ofVirtual().start(job);
                }
            });
        } else {
            Thread.ofVirtual().start(job);
        }
    }

    void deliver(PushSubscription s, String payload) {
        try {
            byte[] bodyBytes = WebPushCrypto.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                    WebPushCrypto.b64(s.getP256dh()), WebPushCrypto.b64(s.getAuth()));
            ECPrivateKey key = WebPushCrypto.privateKey(WebPushCrypto.b64(privateKey));
            String vapid = WebPushCrypto.vapidHeader(s.getEndpoint(), subject, WebPushCrypto.b64(publicKey), key, Instant.now().getEpochSecond());
            HttpRequest req = HttpRequest.newBuilder(URI.create(s.getEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Authorization", vapid)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            record(s.getId(), code);
        } catch (Exception e) {
            log.warn("Push not sent: {}", e.getClass().getSimpleName());
            record(s.getId(), -1);
        }
    }

    /** 404 and 410 mean the browser dropped the subscription: remove it. Repeated other failures also prune it. */
    void record(UUID id, int code) {
        subs.findById(id).ifPresent(s -> {
            if (code == 404 || code == 410) {
                subs.delete(s);
            } else if (code >= 200 && code < 300) {
                s.setFailures(0);
                s.setLastSuccessAt(Instant.now());
                subs.save(s);
            } else {
                s.setFailures(s.getFailures() + 1);
                if (s.getFailures() >= MAX_FAILURES) subs.delete(s);
                else subs.save(s);
            }
        });
    }
}
