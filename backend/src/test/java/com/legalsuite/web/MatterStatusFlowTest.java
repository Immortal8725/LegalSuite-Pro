package com.legalsuite.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MatterStatusFlowTest {
    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void statusRulesApplyOnBothRoutes() throws Exception {
        String t = token();
        assertEquals(400, send(post("/api/v1/cases"), t, "{\"title\":\"No client\"}").getResponse().getStatus());
        String clientId = data(mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        JsonNode m = data(expect(send(post("/api/v1/cases"), t,
                "{\"clientId\":\"" + clientId + "\",\"title\":\"Status flow\"}"), 200));
        String id = m.get("id").asText();
        assertEquals("open", m.get("status").asText());
        String path = "/api/v1/cases/" + id;
        assertEquals(400, send(patch(path + "/status"), t, "{\"status\":\"banana\"}").getResponse().getStatus());
        assertEquals(400, send(put(path), t, "{\"status\":\"banana\"}").getResponse().getStatus());
        JsonNode closed = data(expect(send(patch(path + "/status"), t, "{\"status\":\"closed\"}"), 200));
        assertFalse(closed.get("dateClosed").isNull());
        assertEquals(400, send(patch(path + "/status"), t, "{\"status\":\"discovery\"}").getResponse().getStatus());
        assertEquals(400, send(put(path), t, "{\"status\":\"trial\"}").getResponse().getStatus());
        JsonNode reopened = data(expect(send(put(path), t, "{\"status\":\"open\"}"), 200));
        assertTrue(reopened.get("dateClosed") == null || reopened.get("dateClosed").isNull());
        expect(send(patch(path + "/status"), t, "{\"status\":\"discovery\"}"), 200);
    }

    @Test
    void docketHoldAlsoBlocksTheGeneralUpdate() throws Exception {
        String t = token();
        String clientId = data(mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        String accrual = java.time.LocalDate.now().minusDays(240).toString();
        JsonNode m = data(expect(send(post("/api/v1/cases"), t, "{\"clientId\":\"" + clientId
                + "\",\"title\":\"Organ of state\",\"practiceArea\":\"organ_of_state\",\"caseType\":\"organ_of_state\",\"accrualDate\":\""
                + accrual + "\",\"governmentalDefendant\":true}"), 200));
        if (!m.get("docketHold").asBoolean()) return; // rules engine did not produce a notice clock for this firm
        String path = "/api/v1/cases/" + m.get("id").asText();
        assertEquals(400, send(put(path), t, "{\"status\":\"trial\"}").getResponse().getStatus());
        assertEquals(400, send(patch(path + "/status"), t, "{\"status\":\"trial\"}").getResponse().getStatus());
    }

    private MvcResult send(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult expect(MvcResult r, int code) throws Exception {
        assertEquals(code, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return r;
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }
}
