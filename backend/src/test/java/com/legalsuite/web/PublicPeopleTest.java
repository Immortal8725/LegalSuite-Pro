package com.legalsuite.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PublicPeopleTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void invitedLawyerStaysOffThePublicPeoplePageUntilPublished() throws Exception {
        String thabo = token("thabo@ndlovulaw.co.za");
        JsonNode invited = data(mvc.perform(post("/api/v1/users/invite").header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Kea\",\"lastName\":\"Mothibi\",\"email\":\"kea.public@example.com\",\"role\":\"attorney\"}"))
                .andExpect(status().isOk()).andReturn());
        assertFalse(invited.get("publicProfile").asBoolean());
        assertFalse(peopleJson().contains("kea.public@example.com"));

        String id = invited.get("id").asText();
        String sipho = token("sipho@ndlovulaw.co.za");
        mvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicProfile\":true}"))
                .andExpect(status().isForbidden());
        try {
            mvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + thabo)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"publicProfile\":true}"))
                    .andExpect(status().isOk());
            assertTrue(peopleJson().contains("kea.public@example.com"));
        } finally {
            // Leave the shared demo firm's public page as other tests expect it.
            mvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + thabo)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"publicProfile\":false,\"status\":\"inactive\"}"));
        }
        assertFalse(peopleJson().contains("kea.public@example.com"));
    }

    private String peopleJson() throws Exception {
        JsonNode site = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        return site.path("people").toString();
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
