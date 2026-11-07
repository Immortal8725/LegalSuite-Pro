package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReferralTest {
    @Autowired
    MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aCodeListsTheFirmThatUsedItAndDoesNotPromiseAPayout() throws Exception {
        String slugA = "refa-" + UUID.randomUUID().toString().substring(0, 6);
        String tokenA = register(slugA, null);
        JsonNode mine = data(mvc.perform(get("/api/v1/referrals").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk()));
        String code = mine.get("code").asText();
        assertTrue(mine.get("reward").asText().contains("Reward to be confirmed"));
        assertEquals(0, mine.get("referredFirms").size());

        String slugB = "refb-" + UUID.randomUUID().toString().substring(0, 6);
        register(slugB, code);
        JsonNode after = data(mvc.perform(get("/api/v1/referrals").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk()));
        assertEquals(1, after.get("referredFirms").size());
        assertTrue(after.get("referredFirms").get(0).get("firmName").asText().contains(slugB));
    }

    private String register(String slug, String code) throws Exception {
        String body = "{\"firmName\":\"Refer " + slug + "\",\"email\":\"o@" + slug + ".co.za\",\"password\":\"Sup3r-Secret-99\",\"firstName\":\"A\",\"lastName\":\"B\",\"firmSlug\":\"" + slug + "\",\"planSlug\":\"firm\""
                + (code == null ? "" : ",\"referralCode\":\"" + code + "\"") + "}";
        String raw = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(raw).get("data").get("accessToken").asText();
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString()).get("data");
    }
}
