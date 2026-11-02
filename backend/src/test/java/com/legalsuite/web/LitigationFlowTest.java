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
class LitigationFlowTest {
    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void walkHighCourtStagesAndCreateDeadlines() throws Exception {
        String t = token();
        String clientId = data(mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        String id = data(expect(send(post("/api/v1/cases"), t,
                "{\"clientId\":\"" + clientId + "\",\"title\":\"Stage flow\"}"), 200)).get("id").asText();
        String path = "/api/v1/cases/" + id;
        assertEquals(400, send(post(path + "/stages"), t, "{\"stage\":\"plea\"}").getResponse().getStatus(), "no track yet");
        assertEquals(400, send(put(path + "/litigation"), t, "{\"track\":\"nope\"}").getResponse().getStatus());
        assertEquals(400, send(put(path + "/litigation"), t, "{\"track\":\"high_court_action\",\"courtLevel\":\"ccma\"}").getResponse().getStatus());
        JsonNode v = data(expect(send(put(path + "/litigation"), t, "{\"track\":\"high_court_action\",\"courtLevel\":\"high\"}"), 200));
        assertEquals("high", v.get("courtLevel").asText());
        JsonNode s = data(expect(send(post(path + "/stages"), t, "{\"stage\":\"summons_served\",\"date\":\"2026-11-02\"}"), 200));
        assertEquals("2026-11-16", s.get("createdDeadlines").get(0).get("dueDate").asText());
        JsonNode p = data(expect(send(post(path + "/stages"), t, "{\"stage\":\"appearance_to_defend\",\"date\":\"2026-11-02\"}"), 200));
        assertEquals("2026-11-30", p.get("createdDeadlines").get(0).get("dueDate").asText());
        assertEquals(400, send(post(path + "/stages"), t, "{\"stage\":\"summons_issued\",\"date\":\"2026-11-02\"}").getResponse().getStatus());
        assertEquals(400, send(post(path + "/stages"), t, "{\"stage\":\"plea\",\"date\":\"2026-10-01\"}").getResponse().getStatus(), "date before previous stage");
        assertEquals(400, send(put(path + "/litigation"), t, "{\"track\":\"divorce\"}").getResponse().getStatus(), "track locked once stages exist");
        JsonNode m = data(expect(mvc.perform(get(path + "/litigation").header("Authorization", "Bearer " + t)).andReturn(), 200));
        assertEquals(2, m.get("timeline").size());
        assertEquals(2, m.get("deadlines").size());
        assertEquals("appearance_to_defend", m.get("stage").asText());
        JsonNode pipe = data(expect(mvc.perform(get("/api/v1/litigation/pipeline?track=high_court_action").header("Authorization", "Bearer " + t)).andReturn(), 200));
        assertTrue(pipe.toString().contains(id));
        JsonNode cd = data(expect(mvc.perform(get("/api/v1/litigation/court-days?from=2026-12-07&amount=10").header("Authorization", "Bearer " + t)).andReturn(), 200));
        assertEquals("2027-01-21", cd.get("due").asText());
        assertEquals(10, data(expect(mvc.perform(get("/api/v1/litigation/tracks").header("Authorization", "Bearer " + t)).andReturn(), 200)).get("tracks").size());
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
