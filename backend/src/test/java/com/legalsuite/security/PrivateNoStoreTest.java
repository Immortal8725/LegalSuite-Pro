package com.legalsuite.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Signed-in and credential responses must not be stored by a shared cache. Public reads stay cacheable. */
@SpringBootTest
@AutoConfigureMockMvc
class PrivateNoStoreTest {
    @Autowired
    MockMvc mvc;

    @Test
    void staffAndPortalResponsesArePrivate() throws Exception {
        String staff = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"));

        String portal = portalToken();
        mvc.perform(get("/api/v1/portal/cases").header("Authorization", "Bearer " + portal))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void unauthenticatedPrivateRoutesAndSignInAreNotStored() throws Exception {
        mvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(get("/api/v1/status"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void anonymousPublicReadsCanBeCached() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300"));
        mvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300"));
        mvc.perform(get("/api/v1/public/blog/posts"))
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300"));
    }

    @Test
    void aBearerTokenOnAPublicUrlIsStillPrivate() throws Exception {
        String staff = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        mvc.perform(get("/api/v1/plans").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    private String token(String firm, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + firm + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        String body = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").get("accessToken").asText();
    }
}
