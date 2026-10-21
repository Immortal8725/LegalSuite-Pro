package com.legalsuite.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OperatorFirmIdAliasTest {
    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions login(String slug, String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"));
    }

    @Test
    void operatorCanUseOwlawAsFirmId() throws Exception {
        for (String s : new String[] {"Owlaw", " owlaw ", "legalsuite"}) {
            login(s, "ops@legalsuite.pro").andExpect(status().isOk());
        }
        // The alias only reaches the platform account: firm users are not found there.
        login("owlaw", "thabo@ndlovulaw.co.za").andExpect(status().isUnauthorized());
        login("nope-firm", "ops@legalsuite.pro").andExpect(status().isNotFound());
    }
}
