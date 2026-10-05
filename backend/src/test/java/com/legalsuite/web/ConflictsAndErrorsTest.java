package com.legalsuite.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** B4, B5 and B10 from the A to Z report. */
@SpringBootTest
@AutoConfigureMockMvc
class ConflictsAndErrorsTest {
    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void conflictCheckWithSeveralMatchesIsSaved() throws Exception {
        String thabo = token();
        // The seed gives two hits; a third party on file pushes the stored JSON well past 255 characters.
        expect(send(post("/api/v1/contacts"), thabo,
                "{\"firstName\":\"Lindiwe\",\"lastName\":\"Khumalo\",\"company\":\"Road Accident Fund\",\"type\":\"opposing_party\",\"email\":\"l.khumalo@example.com\"}"), 200);
        MvcResult result = send(post("/api/v1/conflicts/check"), thabo,
                "{\"name\":\"Khumalo\",\"opposingParty\":\"Road Accident Fund\"}");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode check = data(result);
        assertTrue(check.get("matchCount").asInt() >= 3, "expected three or more hits, got " + check.get("matchCount"));
        assertTrue(check.get("matches").toString().length() > 255);
        assertEquals("potential_conflict", check.get("status").asText());

        JsonNode history = data(mvc.perform(get("/api/v1/conflicts").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn());
        assertTrue(history.toString().contains(check.get("id").asText()));
    }

    @Test
    void retainOnLeadsWithConflictHitsAsksForAWaiverInsteadOfCrashing() throws Exception {
        String thabo = token();
        JsonNode leads = data(mvc.perform(get("/api/v1/intake/leads").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn());
        int most = 0;
        for (JsonNode lead : leads) {
            if (!lead.toString().contains("opposingParty") || lead.get("opposingParty") == null || lead.get("opposingParty").isNull()) continue;
            MvcResult result = send(post("/api/v1/retain/" + lead.get("id").asText()), thabo, "{}");
            assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            JsonNode out = data(result);
            if (out.has("blocked") && out.get("blocked").asBoolean()) {
                assertTrue(out.get("waiverRequired").asBoolean());
                most = Math.max(most, out.get("conflict").get("matchCount").asInt());
            }
        }
        assertTrue(most >= 2, "expected a seeded lead with two or more hits, best was " + most);
    }

    @Test
    void blankOptionalIdsMeanNotChosen() throws Exception {
        String thabo = token();
        JsonNode time = data(expect(send(post("/api/v1/time-entries"), thabo,
                "{\"caseId\":\"\",\"description\":\"Unassigned research\",\"durationMinutes\":30}"), 200));
        assertTrue(time.get("caseId") == null || time.get("caseId").isNull());

        String caseId = data(mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        JsonNode signature = data(expect(send(post("/api/v1/signatures"), thabo,
                "{\"caseId\":\"" + caseId + "\",\"clientId\":\"\",\"title\":\"Mandate\",\"signerName\":\"A\",\"signerEmail\":\"a@example.com\",\"documentBody\":\"x\"}"), 200));
        assertTrue(signature.get("signUrl").asText().startsWith("/sign/"));

        expect(send(post("/api/v1/time-entries"), thabo, "{\"caseId\":\"not-an-id\",\"durationMinutes\":30}"), 400);
        expect(send(post("/api/v1/signatures"), thabo, "{\"clientId\":\"12345\",\"title\":\"x\"}"), 400);
        expect(send(post("/api/v1/time-entries"), thabo, "{\"durationMinutes\":\"half an hour\"}"), 400);
        expect(send(post("/api/v1/expenses"), thabo, "{\"amount\":\"lots\"}"), 400);
    }

    @Test
    void errorsAreShortAndNeverShowSql() throws Exception {
        String thabo = token();
        String invoiceId = data(mvc.perform(get("/api/v1/invoices").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        MvcResult missing = send(post("/api/v1/invoices/" + invoiceId + "/refund-everything"), thabo, "{}");
        assertEquals(404, missing.getResponse().getStatus());
        assertFalse(missing.getResponse().getContentAsString().contains("Unexpected error"));

        MvcResult wrongMethod = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/conflicts/check").header("Authorization", "Bearer " + thabo))
                .andReturn();
        assertEquals(405, wrongMethod.getResponse().getStatus());

        MvcResult badJson = send(post("/api/v1/conflicts/check"), thabo, "{not json");
        assertEquals(400, badJson.getResponse().getStatus());

        // A duplicate email was a unique-index 500 with the SQL in the message. Now it is a clean 4xx.
        String body = "{\"firstName\":\"Dup\",\"lastName\":\"Check\",\"email\":\"thabo@ndlovulaw.co.za\",\"role\":\"staff\"}";
        MvcResult dup = send(post("/api/v1/users/invite"), thabo, body);
        assertTrue(dup.getResponse().getStatus() >= 400 && dup.getResponse().getStatus() < 500);
        String text = dup.getResponse().getContentAsString().toLowerCase();
        assertFalse(text.contains("sql"));
        assertFalse(text.contains("insert into"));
    }

    private MvcResult send(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private MvcResult expect(MvcResult result, int code) throws Exception {
        assertEquals(code, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return result;
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
