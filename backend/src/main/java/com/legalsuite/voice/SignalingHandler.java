package com.legalsuite.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.security.JwtService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SignalingHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtService jwtService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public SignalingHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String query = session.getUri() == null ? "" : session.getUri().getQuery();
            String token = null;
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("token=")) token = part.substring(6);
                }
            }
            if (token != null) {
                Claims claims = jwtService.parse(token);
                String userId = claims.getSubject();
                session.getAttributes().put("userId", userId);
                sessions.put(userId, session);
            }
        } catch (Exception ignored) {
            // stay unauthenticated; client may send a register event
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText();
        if ("register".equals(type) && node.has("userId")) {
            String userId = node.get("userId").asText();
            session.getAttributes().put("userId", userId);
            sessions.put(userId, session);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "registered", "userId", userId))));
            return;
        }
        String to = node.path("to").asText(null);
        if (to == null || to.isBlank()) return;
        WebSocketSession target = sessions.get(to);
        if (target != null && target.isOpen()) {
            var payload = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> payload.set(e.getKey(), e.getValue()));
            Object from = session.getAttributes().get("userId");
            if (from != null) payload.put("from", from.toString());
            target.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) sessions.remove(userId.toString());
    }

    public boolean isOnline(UUID userId) {
        WebSocketSession s = sessions.get(userId.toString());
        return s != null && s.isOpen();
    }
}
