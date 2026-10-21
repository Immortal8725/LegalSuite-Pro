package com.legalsuite.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class TrustAuditPackTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void packHasLedgersThreeWayInvestmentsAndExports() throws Exception {
        String tok = token();
        String client = data(call(post("/api/v1/clients"), tok, Map.of("firstName", "Audit", "lastName", "Pack" + System.nanoTime()))).get("id").asText();
        String acct = data(mvc.perform(get("/api/v1/trust/accounts").header("Authorization", "Bearer " + tok))).get(0).get("id").asText();
        call(post("/api/v1/trust/move"), tok, Map.of("accountId", acct, "type", "deposit", "amount", "1000.00", "clientId", client, "description", "Deposit")).andExpect(status().isOk());
        call(post("/api/v1/trust/investments"), tok, Map.of("section", "86(4)")).andExpect(status().isBadRequest());
        call(post("/api/v1/trust/investments"), tok, Map.of("section", "86(4)", "clientId", client, "institution", "Big Bank", "principal", "500", "interestEarned", "12.34")).andExpect(status().isOk());
        String from = LocalDate.now().withDayOfMonth(1).toString();
        String to = LocalDate.now().plusDays(1).toString();
        JsonNode p = data(mvc.perform(get("/api/v1/trust/audit-pack").param("from", from).param("to", to).header("Authorization", "Bearer " + tok)).andExpect(status().isOk()));
        assertTrue(p.get("accounts").size() >= 1);
        JsonNode a = p.get("accounts").get(0);
        assertTrue(a.get("threeWay").has("balanced"));
        assertTrue(a.get("clientLedgers").toString().contains("Audit Pack"));
        assertTrue(p.get("interest86_4").decimalValue().compareTo(new java.math.BigDecimal("12.34")) >= 0);
        byte[] pdf = mvc.perform(get("/api/v1/trust/audit-pack.pdf").param("from", from).param("to", to).header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4));
        String csv = mvc.perform(get("/api/v1/trust/audit-pack.csv").param("from", from).param("to", to).header("Authorization", "Bearer " + tok))
                .andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("THREE-WAY AT") && csv.contains("EXCEPTIONS"));
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
