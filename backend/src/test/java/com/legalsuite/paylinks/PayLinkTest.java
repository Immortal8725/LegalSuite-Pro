package com.legalsuite.paylinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.payfast.PayFastSignature;
import com.legalsuite.payfast.PayFastSignature.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PayLinkTest.AlwaysValid.class)
class PayLinkTest {
    @TestConfiguration
    static class AlwaysValid {
        @Bean PayLinkService.ItnValidator validator() { return (mode, fields) -> true; }
    }

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void linkSignsFormAndItnBooksPaymentOnce() throws Exception {
        String tok = token();
        JsonNode invoices = data(mvc.perform(get("/api/v1/invoices").header("Authorization", "Bearer " + tok)));
        JsonNode inv = null;
        for (JsonNode i : invoices) {
            if (!"draft".equals(i.get("status").asText()) && !"void".equals(i.get("status").asText()) && i.get("balanceDue").decimalValue().signum() > 0) { inv = i; break; }
        }
        assertNotNull(inv, "seed has an open invoice");
        String id = inv.get("id").asText();
        BigDecimal due = inv.get("balanceDue").decimalValue();
        JsonNode link = data(mvc.perform(post("/api/v1/invoices/" + id + "/payment-link").header("Authorization", "Bearer " + tok)).andExpect(status().isOk()));
        assertEquals("sandbox", link.get("mode").asText());
        String t = link.get("token").asText();
        JsonNode view = data(mvc.perform(get("/api/v1/public/pay/" + t)).andExpect(status().isOk()));
        assertEquals("https://sandbox.payfast.co.za/eng/process", view.get("action").asText());
        assertEquals(PayLinkService.SANDBOX_ID, view.get("fields").get("merchant_id").asText());
        String mpid = view.get("fields").get("m_payment_id").asText();

        List<Field> f = new ArrayList<>();
        f.add(new Field("m_payment_id", mpid));
        f.add(new Field("pf_payment_id", "123456"));
        f.add(new Field("payment_status", "COMPLETE"));
        f.add(new Field("amount_gross", due.toPlainString()));
        f.add(new Field("merchant_id", PayLinkService.SANDBOX_ID));
        // Tampered signature is ignored.
        mvc.perform(post("/api/v1/public/pay/" + t + "/itn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(PayFastSignature.formBody(f) + "&signature=bad")).andExpect(status().isOk());
        assertEquals("open", data(mvc.perform(get("/api/v1/public/pay/" + t))).get("status").asText());
        String sig = PayFastSignature.sign(f, PayLinkService.SANDBOX_PASS);
        mvc.perform(post("/api/v1/public/pay/" + t + "/itn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(PayFastSignature.formBody(f) + "&signature=" + sig)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/pay/" + t + "/itn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(PayFastSignature.formBody(f) + "&signature=" + sig)).andExpect(status().isOk());
        assertEquals("paid", data(mvc.perform(get("/api/v1/public/pay/" + t))).get("status").asText());
        JsonNode after = data(mvc.perform(get("/api/v1/invoices/" + id).header("Authorization", "Bearer " + tok)));
        assertEquals(0, after.get("balanceDue").decimalValue().signum());

        JsonNode st = data(mvc.perform(get("/api/v1/clients/" + inv.get("clientId").asText() + "/statement").header("Authorization", "Bearer " + tok)));
        assertTrue(st.has("totalDue"));
        byte[] pdf = mvc.perform(get("/api/v1/clients/" + inv.get("clientId").asText() + "/statement.pdf").header("Authorization", "Bearer " + tok))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void liveModeNeedsCredentials() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/payments/settings")
                .header("Authorization", "Bearer " + token()).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"live\"}"))
                .andExpect(status().isBadRequest());
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
