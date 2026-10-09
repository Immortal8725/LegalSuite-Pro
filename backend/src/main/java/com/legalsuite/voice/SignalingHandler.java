package com.legalsuite.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.security.JwtService;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebRTC signalling between staff in the same firm. The identity of a socket comes only from the
 * access token in the connect URL; a client cannot claim another user's id, and messages are only
 * relayed to colleagues in the sender's own tenant.
 */
@Component
public class SignalingHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(SignalingHandler.class);
    static final String USER = "userId";
    static final String TENANT = "tenantId";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtService jwtService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public SignalingHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Claims claims = authenticate(session);
        if (claims == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Sign in required"));
            return;
        }
        session.getAttributes().put(USER, claims.getSubject());
        session.getAttributes().put(TENANT, claims.get("tenantId", String.class));
        WebSocketSession previous = sessions.put(claims.getSubject(), session);
        if (previous != null && previous != session && previous.isOpen()) {
            // A newer tab took over; the old one stops receiving calls.
            previous.close(CloseStatus.NORMAL.withReason("Signed in elsewhere"));
        }
    }

    private Claims authenticate(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getRawQuery();
        if (query == null) return null;
        String token = null;
        for (String part : query.split("&")) {
            if (part.startsWith("token=")) token = java.net.URLDecoder.decode(part.substring(6), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = jwtService.parse(token);
            String type = claims.get("type", String.class);
            // Staff access tokens only: no refresh tokens, no client portal tokens, and not while a
            // password change is still pending.
            if (type != null) return null;
            if (Boolean.TRUE.equals(claims.get("pwc", Boolean.class))) return null;
            if ("client".equals(claims.get("role", String.class))) return null;
            if (claims.getSubject() == null || claims.get("tenantId", String.class) == null) return null;
            UUID.fromString(claims.getSubject());
            return claims;
        } catch (Exception e) {
            log.debug("Signalling connect refused: {}", e.getMessage());
            return null;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String me = (String) session.getAttributes().get(USER);
        String tenant = (String) session.getAttributes().get(TENANT);
        if (me == null || tenant == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Sign in required"));
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            return; // not JSON; ignore
        }
        String type = node.path("type").asText();
        if ("register".equals(type)) {
            // Older clients send their own id here. The token already decided who this is.
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "registered", "userId", me))));
            return;
        }
        String to = node.path("to").asText(null);
        if (to == null || to.isBlank() || to.equals(me)) return;
        WebSocketSession target = sessions.get(to);
        if (target == null || !target.isOpen()) return;
        if (!tenant.equals(target.getAttributes().get(TENANT))) return; // never across firms
        var payload = mapper.createObjectNode();
        node.properties().forEach(e -> payload.set(e.getKey(), e.getValue()));
        payload.put("from", me);
        synchronized (target) {
            target.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get(USER);
        if (userId != null) sessions.remove(userId.toString(), session);
    }

    public boolean isOnline(UUID userId) {
        WebSocketSession s = sessions.get(userId.toString());
        return s != null && s.isOpen();
    }
}
