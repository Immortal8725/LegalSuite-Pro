package com.legalsuite.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TwilioRestGatewayTest {
    private HttpServer server;
    private TwilioProperties props;
    private TwilioRestGateway gateway;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> form = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/2010-04-01/Accounts/AC1234567890/Calls.json", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            form.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"sid\":\"CA999\",\"status\":\"queued\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/2010-04-01/Accounts/AC1234567890/IncomingPhoneNumbers.json", exchange -> {
            byte[] resp = "{\"message\":\"The phone number is unavailable\",\"code\":21422}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        props = new TwilioProperties();
        props.setAccountSid("AC1234567890");
        props.setAuthToken("super-secret-token");
        props.setApiRoot("http://127.0.0.1:" + server.getAddress().getPort());
        gateway = new TwilioRestGateway(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createCallPostsFromAndToWithoutPuttingTheTokenInTheUrl() {
        String sid = gateway.createCall("+27830000000", "+27110000000", "https://example.com/bridge/abc", "https://example.com/status/abc");
        assertEquals("CA999", sid);
        assertTrue(auth.get().startsWith("Basic "));
        assertFalse(path.get().contains("super-secret-token"));
        assertFalse(form.get().contains("super-secret-token"));
        assertTrue(form.get().contains("To=%2B27830000000"));
        assertTrue(form.get().contains("From=%2B27110000000"));
        assertTrue(form.get().contains("Url=https%3A%2F%2Fexample.com%2Fbridge%2Fabc"));
        assertTrue(form.get().contains("StatusCallbackEvent=answered"));
        assertTrue(form.get().contains("StatusCallbackEvent=completed"));
    }

    @Test
    void carrierErrorsDoNotEchoTheAuthToken() {
        ApiException ex = assertThrows(ApiException.class, () -> gateway.buyLocal("+27110000000", "Firm", "https://example.com/in", "https://example.com/st"));
        assertEquals("The phone number is unavailable", ex.getMessage());
        assertFalse(ex.getMessage().contains("super-secret-token"));
    }
}
