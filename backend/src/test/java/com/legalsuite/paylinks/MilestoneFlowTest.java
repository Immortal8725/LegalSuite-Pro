package com.legalsuite.paylinks;

import com.legalsuite.milestones.MatterMilestone;
import com.legalsuite.milestones.MatterMilestoneRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.paylinks.PayLinkService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MilestoneFlowTest.AlwaysValid.class)
class MilestoneFlowTest {
    @TestConfiguration
    static class AlwaysValid {
        @Bean PayLinkService.ItnValidator validator() { return (mode, fields) -> true; }
    }

    @Autowired private MockMvc mvc;
    @Autowired private MatterMilestoneRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void depositGoesToTrustAndStageFeeIsInvoicedWhenStageIsReached() throws Exception {
        String t = token();
        String clientId = data(call(get("/api/v1/clients"), t, null)).get(0).get("id").asText();
        String caseId = data(call(post("/api/v1/cases"), t, "{\"clientId\":\"" + clientId + "\",\"title\":\"Milestone flow\"}")).get("id").asText();
        String path = "/api/v1/cases/" + caseId;
        // Stage milestones need a track.
        assertEquals(400, raw(put(path + "/milestones"), t, "{\"milestones\":[{\"kind\":\"stage\",\"stage\":\"plea\",\"amount\":100}]}"));
        call(put(path + "/litigation"), t, "{\"track\":\"high_court_action\",\"courtLevel\":\"high\"}");
        assertEquals(400, raw(put(path + "/milestones"), t, "{\"milestones\":[{\"kind\":\"stage\",\"stage\":\"banana\",\"amount\":100}]}"));
        assertEquals(400, raw(put(path + "/milestones"), t, "{\"milestones\":[{\"kind\":\"deposit\",\"amount\":-5}]}"));
        JsonNode plan = data(call(put(path + "/milestones"), t, "{\"milestones\":["
                + "{\"kind\":\"deposit\",\"label\":\"Deposit on fees\",\"amount\":5000},"
                + "{\"kind\":\"stage\",\"label\":\"Summons served\",\"stage\":\"summons_served\",\"amount\":2000},"
                + "{\"kind\":\"stage\",\"label\":\"Plea\",\"stage\":\"plea\",\"amount\":3000}]}"));
        assertEquals(3, plan.get("milestones").size());
        assertTrue(plan.get("stages").size() > 3);

        // Deposit: billed straight away, with a PayFast sandbox link.
        String depId = plan.get("milestones").get(0).get("id").asText();
        JsonNode afterBill = data(call(post("/api/v1/milestones/" + depId + "/bill"), t, null));
        JsonNode dep = afterBill.get("milestones").get(0);
        assertEquals("invoiced", dep.get("status").asText());
        assertTrue(dep.get("payUrl").asText().contains("/pay/"), "pay link");
        assertEquals(400, raw(post("/api/v1/milestones/" + depId + "/bill"), t, null), "no double billing");
        String invId = dep.get("invoiceId").asText();
        JsonNode inv = data(call(get("/api/v1/invoices/" + invId), t, null));
        assertEquals(0, inv.get("total").decimalValue().compareTo(new BigDecimal("5000.00")), "no VAT on a trust deposit");
        assertTrue(inv.get("invoiceNumber").asText().startsWith("DEP-"));

        // Pay it online (sandbox ITN). It must land in trust or be held, never booked as fees.
        String tokn = dep.get("payUrl").asText().replaceAll(".*/pay/", "");
        JsonNode view = data(mvc.perform(get("/api/v1/public/pay/" + tokn)).andExpect(status().isOk()));
        assertEquals("https://sandbox.payfast.co.za/eng/process", view.get("action").asText());
        List<Field> f = new ArrayList<>();
        f.add(new Field("m_payment_id", view.get("fields").get("m_payment_id").asText()));
        f.add(new Field("pf_payment_id", "777"));
        f.add(new Field("payment_status", "COMPLETE"));
        f.add(new Field("amount_gross", "5000.00"));
        f.add(new Field("merchant_id", PayLinkService.SANDBOX_ID));
        mvc.perform(post("/api/v1/public/pay/" + tokn + "/itn").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(PayFastSignature.formBody(f) + "&signature=" + PayFastSignature.sign(f, PayLinkService.SANDBOX_PASS))).andExpect(status().isOk());
        MatterMilestone paid = repo.findById(java.util.UUID.fromString(depId)).orElseThrow();
        assertTrue(List.of("paid", "held").contains(paid.getStatus()), paid.getStatus());
        if ("paid".equals(paid.getStatus())) assertNotNull(paid.getTrustTransactionId());
        JsonNode invAfter = data(call(get("/api/v1/invoices/" + invId), t, null));
        assertEquals("paid", invAfter.get("status").asText());
        JsonNode pays = invAfter.get("payments");
        assertTrue(pays == null || pays.isNull() || pays.size() == 0, "no business-account payment row for a trust deposit");

        // Reaching summons_served bills only that milestone, with VAT.
        JsonNode st = data(call(post(path + "/stages"), t, "{\"stage\":\"summons_served\"}"));
        assertEquals(1, st.get("billedMilestones").size());
        JsonNode p2 = data(call(get(path + "/milestones"), t, null)).get("milestones");
        assertEquals("invoiced", p2.get(1).get("status").asText());
        assertEquals("pending", p2.get(2).get("status").asText());
        JsonNode feeInv = data(call(get("/api/v1/invoices/" + p2.get(1).get("invoiceId").asText()), t, null));
        assertEquals(0, feeInv.get("total").decimalValue().compareTo(new BigDecimal("2300.00")));

        // Saving the plan again keeps billed rows and replaces pending ones.
        JsonNode p3 = data(call(put(path + "/milestones"), t, "{\"milestones\":[{\"kind\":\"stage\",\"label\":\"Trial\",\"stage\":\"plea\",\"amount\":9000}]}"));
        assertEquals(3, p3.get("milestones").size());

        // Staff routes are closed to portal clients; the portal route works.
        String portal = portalToken();
        assertTrue(raw(get(path + "/milestones"), portal, null) >= 400);
        assertEquals(200, raw(get("/api/v1/portal/milestones"), portal, null));
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        return data(mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder b, String t, String body) throws Exception {
        b.header("Authorization", "Bearer " + t);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b).andExpect(status().isOk());
    }

    private int raw(MockHttpServletRequestBuilder b, String t, String body) throws Exception {
        b.header("Authorization", "Bearer " + t);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
