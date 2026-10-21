package com.legalsuite.directory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Shared helpers: sign in to demo firms and list Ndlovu & Partners in the directory. */
abstract class DirectoryTestSupport {
    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper json = new ObjectMapper();

    protected String token(String slug, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    protected String thabo() throws Exception {
        return token("ndlovu-partners", "thabo@ndlovulaw.co.za");
    }

    protected String ops() throws Exception {
        return com.legalsuite.OperatorTokens.ops(mvc);
    }

    protected JsonNode putProfile(String token, String body) throws Exception {
        return data(mvc.perform(put("/api/v1/directory/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected JsonNode profile(String token) throws Exception {
        return data(mvc.perform(get("/api/v1/directory/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Makes Ndlovu & Partners listed: profile, switch on, one verified practitioner. Idempotent. */
    protected void listNdlovu() throws Exception {
        String t = thabo();
        putProfile(t, "{\"practiceAreas\":[\"raf-claims\",\"divorce-and-family\"],\"cities\":[\"sandton\",\"johannesburg\"],"
                + "\"languages\":[\"english\",\"isizulu\"],\"consultOptions\":[\"free-first\",\"online\",\"contingency\"],"
                + "\"responseTime\":\"one-day\",\"bio\":\"Road accident and family matters in Gauteng.\",\"listed\":true}");
        JsonNode p = profile(t);
        boolean hasVerified = false;
        for (JsonNode pr : p.get("practitioners")) {
            if ("verified".equals(pr.get("status").asText()) && pr.get("fidelityCurrent").asBoolean()) hasVerified = true;
        }
        if (hasVerified) return;
        JsonNode added = data(mvc.perform(post("/api/v1/directory/practitioners")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Thabo Ndlovu\",\"lpcNumber\":\"LPC GP 44821\",\"fidelityExpiresOn\":\""
                                + LocalDate.now().plusYears(1) + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        String id = null;
        for (JsonNode pr : added.get("practitioners")) {
            if ("pending".equals(pr.get("status").asText())) id = pr.get("id").asText();
        }
        mvc.perform(post("/api/v1/platform/directory/practitioners/" + id + "/verify")
                        .header("Authorization", "Bearer " + ops())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"verified\",\"note\":\"Checked on the LPC register\"}"))
                .andExpect(status().isOk());
    }
}
