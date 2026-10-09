package com.legalsuite.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Tenant;
import com.legalsuite.security.JwtService;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class SignalingHandlerTest {
    private final JwtService jwt = new JwtService("test-secret-test-secret-test-secret-0123456789", 60_000, 600_000);
    private final SignalingHandler handler = new SignalingHandler(jwt);

    private static AppUser user(UUID tenant) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenant);
        u.setEmail(u.getId() + "@example.co.za");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole("associate");
        return u;
    }

    private static WebSocketSession socket(String query) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        when(s.getAttributes()).thenReturn(attrs);
        when(s.getUri()).thenReturn(URI.create("ws://localhost/ws/signal" + (query == null ? "" : "?" + query)));
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private static List<String> sent(WebSocketSession s) throws Exception {
        ArgumentCaptor<TextMessage> c = ArgumentCaptor.forClass(TextMessage.class);
        verify(s, org.mockito.Mockito.atLeast(0)).sendMessage(c.capture());
        List<String> out = new ArrayList<>();
        c.getAllValues().forEach(m -> out.add(m.getPayload()));
        return out;
    }

    @Test
    void connectionWithoutTokenIsClosed() throws Exception {
        WebSocketSession s = socket(null);
        handler.afterConnectionEstablished(s);
        verify(s).close(any(CloseStatus.class));
        assertThat(s.getAttributes()).doesNotContainKey(SignalingHandler.USER);
    }

    @Test
    void refreshAndPortalTokensAreRefused() throws Exception {
        UUID tenant = UUID.randomUUID();
        WebSocketSession refresh = socket("token=" + jwt.refreshToken(user(tenant)));
        handler.afterConnectionEstablished(refresh);
        verify(refresh).close(any(CloseStatus.class));

        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setTenantId(tenant);
        client.setFirstName("Nomsa");
        client.setLastName("Khumalo");
        client.setEmail("nomsa@example.com");
        Tenant t = new Tenant();
        t.setId(tenant);
        WebSocketSession portal = socket("token=" + jwt.portalToken(client, t));
        handler.afterConnectionEstablished(portal);
        verify(portal).close(any(CloseStatus.class));
    }

    @Test
    void registerCannotClaimSomeoneElse() throws Exception {
        UUID tenant = UUID.randomUUID();
        AppUser alice = user(tenant), bob = user(tenant);
        WebSocketSession bobSocket = socket("token=" + jwt.accessToken(bob));
        handler.afterConnectionEstablished(bobSocket);
        WebSocketSession mallory = socket("token=" + jwt.accessToken(alice));
        handler.afterConnectionEstablished(mallory);

        // Alice's socket tries to register as Bob.
        handler.handleTextMessage(mallory, new TextMessage("{\"type\":\"register\",\"userId\":\"" + bob.getId() + "\"}"));
        assertThat(sent(mallory)).anyMatch(m -> m.contains(alice.getId().toString())).noneMatch(m -> m.contains(bob.getId().toString()));
        assertThat(mallory.getAttributes().get(SignalingHandler.USER)).isEqualTo(alice.getId().toString());
        assertThat(handler.isOnline(bob.getId())).isTrue();

        // An offer sent to Bob still reaches Bob's own socket, with the true sender.
        handler.handleTextMessage(mallory, new TextMessage("{\"type\":\"offer\",\"to\":\"" + bob.getId() + "\",\"from\":\"forged\",\"sdp\":\"x\"}"));
        assertThat(sent(bobSocket)).singleElement().satisfies(m -> {
            assertThat(m).contains("\"from\":\"" + alice.getId() + "\"");
            assertThat(m).doesNotContain("forged");
        });
    }

    @Test
    void signalsAreNotRelayedAcrossFirms() throws Exception {
        AppUser here = user(UUID.randomUUID()), elsewhere = user(UUID.randomUUID());
        WebSocketSession a = socket("token=" + jwt.accessToken(here));
        WebSocketSession b = socket("token=" + jwt.accessToken(elsewhere));
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        handler.handleTextMessage(a, new TextMessage("{\"type\":\"offer\",\"to\":\"" + elsewhere.getId() + "\",\"sdp\":\"x\"}"));
        verify(b, never()).sendMessage(any());
    }

    @Test
    void closingAnOldTabDoesNotDropTheNewOne() throws Exception {
        AppUser u = user(UUID.randomUUID());
        WebSocketSession first = socket("token=" + jwt.accessToken(u));
        WebSocketSession second = socket("token=" + jwt.accessToken(u));
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertThat(handler.isOnline(u.getId())).isTrue();
    }
}
