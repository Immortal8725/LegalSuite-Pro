package com.legalsuite.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** An authenticator code works once. A second sign-in with the same code is refused. */
@SpringBootTest
@AutoConfigureMockMvc
class TotpReplayTest {
    @Autowired
    MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();

    @Test
    void sameCodeCannotBeUsedTwice() throws Exception {
        String slug = "totp-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "o@" + slug + ".co.za";
        String reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"Totp " + slug + "\",\"email\":\"" + email + "\",\"password\":\"Sup3r-Secret-99\",\"firstName\":\"Sipho\",\"lastName\":\"Zulu\",\"firmSlug\":\"" + slug + "\",\"planSlug\":\"firm\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(reg).get("data").get("accessToken").asText();
        String start = mvc.perform(post("/api/v1/auth/totp/start").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = json.readTree(start).get("data").get("secret").asText();
        long step = Instant.now().getEpochSecond() / 30;

        String enrolCode = Totp.codeAt(secret, step - 1);
        mvc.perform(post("/api/v1/auth/totp/confirm").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + enrolCode + "\"}")).andExpect(status().isOk());

        String login = "{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"Sup3r-Secret-99\",\"totpCode\":\"%s\"}";
        // The enrolment code was used already.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login.formatted(enrolCode)))
                .andExpect(status().isUnauthorized());
        String fresh = Totp.codeAt(secret, step);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login.formatted(fresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").exists());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login.formatted(fresh)))
                .andExpect(status().isUnauthorized());
    }
}
