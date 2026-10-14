package com.legalsuite.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** B9 from the A to Z report: day-to-day work, money and team changes reach the audit log. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailTest {
    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void clientsMattersTimeInvoicesAndTeamChangesAreAudited() throws Exception {
        String john = token();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        JsonNode client = data(send(john, "/api/v1/clients",
                "{\"type\":\"individual\",\"firstName\":\"Audit\",\"lastName\":\"Trail" + suffix + "\",\"email\":\"audit" + suffix + "@example.com\"}"));
        JsonNode matter = data(send(john, "/api/v1/cases",
                "{\"title\":\"Audit v Trail " + suffix + "\",\"clientId\":\"" + client.get("id").asText() + "\",\"caseType\":\"civil\"}"));
        send(john, "/api/v1/time-entries", "{\"description\":\"Audit research\",\"durationMinutes\":30}");
        send(john, "/api/v1/invoices/generate", "{\"clientId\":\"" + client.get("id").asText() + "\"}");
        send(john, "/api/v1/users/invite",
                "{\"firstName\":\"Audit\",\"lastName\":\"Hire\",\"email\":\"audit-hire-" + suffix + "@smithlaw.com\",\"role\":\"paralegal\"}");

        JsonNode log = data(mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + john))
                .andExpect(status().isOk()).andReturn());
        Set<String> actions = new HashSet<>();
        log.forEach(row -> actions.add(row.get("action").asText()));
        for (String expected : new String[] {"client.create", "case.create", "time.create", "invoice.generate", "user.invite"}) {
            assertTrue(actions.contains(expected), expected + " missing from " + actions);
        }
        assertTrue(log.toString().contains(matter.get("id").asText()));
    }

    private JsonNode send(String token, String path, String body) throws Exception {
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String token() throws Exception {
        return data(json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"smith-associates\",\"email\":\"john@smithlaw.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static JsonNode data(JsonNode envelope) {
        return envelope.get("data");
    }
}
