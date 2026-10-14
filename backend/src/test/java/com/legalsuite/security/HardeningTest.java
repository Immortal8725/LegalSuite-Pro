package com.legalsuite.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class HardeningTest {
    @Autowired private MockMvc mvc;

    @Test
    void repeatedFailedSignInsAreThrottledButTheRightPasswordStillWorksForOthers() throws Exception {
        String wrong = "{\"firmSlug\":\"ndlovu-partners\",\"email\":\"lindiwe@ndlovulaw.co.za\",\"password\":\"guess\"}";
        for (int i = 0; i < 8; i++) {
            mvc.perform(from("10.20.30.40", post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong)))
                    .andExpect(status().isUnauthorized());
        }
        // The account is now locked for the window, even with the right password.
        mvc.perform(from("10.20.30.40", post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"lindiwe@ndlovulaw.co.za\",\"password\":\"password\"}")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Another account from the same address is not affected.
        mvc.perform(from("10.20.30.40", post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"sipho@ndlovulaw.co.za\",\"password\":\"password\"}")))
                .andExpect(status().isOk());
    }

    @Test
    void apiSendsSecurityHeaders() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'self'; base-uri 'none'; form-action 'none'"));
    }

    @Test
    void corsOnlyAnswersConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/clients").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/v1/clients").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder req) {
        return req.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }
}
