package com.legalsuite.fica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class FicaTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rulesScoreAndSaIdCheck() {
        assertEquals("high", FicaRules.rating(FicaRules.score(List.of("pep_or_ppo", "cash_heavy")), "no"));
        assertEquals("medium", FicaRules.rating(FicaRules.score(List.of("cash_heavy")), "no"));
        assertEquals("high", FicaRules.rating(0, "yes"));
        assertTrue(FicaService.validSaId("8001015009087"));
        assertFalse(FicaService.validSaId("8001015009088"));
        assertEquals("*********9087", FicaService.mask("8001015009087"));
    }

    @Test
    void captureVerifyReportAndRemind() throws Exception {
        String tok = token();
        String client = data(call(post("/api/v1/clients"), tok, Map.of("firstName", "Fica", "lastName", "Client" + System.nanoTime()))).get("id").asText();
        call(put("/api/v1/fica/clients/" + client), tok, Map.of("idType", "sa_id", "idNumber", "8001015009088")).andExpect(status().isBadRequest());
        JsonNode p = data(call(put("/api/v1/fica/clients/" + client), tok, Map.of(
                "idType", "sa_id", "idNumber", "8001015009087", "idExpiry", LocalDate.now().plusDays(10).toString(),
                "pepStatus", "no", "sanctionsResult", "clear", "sanctionsSources", "UN list",
                "riskFactors", List.of("cash_heavy"), "poaDate", LocalDate.now().toString())));
        assertEquals("medium", p.get("riskRating").asText());
        assertEquals("*********9087", p.get("idNumberMasked").asText());
        call(post("/api/v1/fica/clients/" + client + "/verify"), tok, Map.of()).andExpect(status().isBadRequest());
        for (String kind : List.of("id", "poa")) {
            mvc.perform(multipart("/api/v1/fica/clients/" + client + "/documents").file(new MockMultipartFile("file", kind + ".pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes()))
                    .param("kind", kind).header("Authorization", "Bearer " + tok)).andExpect(status().isOk());
        }
        JsonNode v = data(call(post("/api/v1/fica/clients/" + client + "/verify"), tok, Map.of()).andExpect(status().isOk()));
        assertEquals("verified", v.get("status").asText());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")).plusYears(2).toString(), v.get("nextReviewDate").asText());
        JsonNode r = data(call(post("/api/v1/fica/reminders/run"), tok, Map.of()));
        assertTrue(r.get("created").asInt() >= 1, "ID expiring in 10 days gets a task");
        JsonNode again = data(call(post("/api/v1/fica/reminders/run"), tok, Map.of()));
        assertEquals(0, again.get("created").asInt());
        String csv = mvc.perform(get("/api/v1/fica/report.csv").header("Authorization", "Bearer " + tok)).andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("client,status,risk"));
        byte[] docx = mvc.perform(get("/api/v1/fica/rmcp.docx").header("Authorization", "Bearer " + tok)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', docx[0]);
    }

    @Test
    void sanctionsMatchBlocks() throws Exception {
        String tok = token();
        String client = data(call(post("/api/v1/clients"), tok, Map.of("firstName", "Block", "lastName", "Me" + System.nanoTime()))).get("id").asText();
        JsonNode p = data(call(put("/api/v1/fica/clients/" + client), tok, Map.of("sanctionsResult", "match")));
        assertEquals("blocked", p.get("status").asText());
        call(post("/api/v1/fica/clients/" + client + "/verify"), tok, Map.of()).andExpect(status().isBadRequest());
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String tok, Object body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + tok).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
