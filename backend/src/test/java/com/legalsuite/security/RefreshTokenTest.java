package com.legalsuite.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.repo.AppUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenTest {
    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode login(String email) throws Exception {
        return login("ndlovu-partners", email);
    }

    private JsonNode login(String firm, String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + firm + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private int refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void concurrentRefreshesWithOneTokenGiveOneSessionAndNoServerError() throws Exception {
        String token = login("thabo@ndlovulaw.co.za").get("refreshToken").asText();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) calls.add(() -> refresh(token));
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) codes.add(f.get());
            assertEquals(1, codes.stream().filter(c -> c == 200).count(), "exactly one refresh wins: " + codes);
            assertEquals(3, codes.stream().filter(c -> c == 401).count(), "the rest are 401, not 500: " + codes);
        } finally {
            pool.shutdownNow();
        }
        // The used token cannot be replayed.
        assertEquals(401, refresh(token));
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        JsonNode session = login("smith-associates", "john@smithlaw.com");
        UUID id = UUID.fromString(session.get("user").get("id").asText());
        var user = users.findById(id).orElseThrow();
        user.setStatus("inactive");
        users.save(user);
        try {
            assertEquals(401, refresh(session.get("refreshToken").asText()));
        } finally {
            user = users.findById(id).orElseThrow();
            user.setStatus("active");
            users.save(user);
        }
    }
}
