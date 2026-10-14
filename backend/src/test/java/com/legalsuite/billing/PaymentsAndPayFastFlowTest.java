package com.legalsuite.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Invoice;
import com.legalsuite.payfast.PayFastProperties;
import com.legalsuite.payfast.PayFastSignature;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end over HTTP: part payment, write-off, client proof of payment with staff review,
 * and PayFast sandbox seat checkout, signed ITN and minutes charge (ITN mocked, no PayFast calls).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentsAndPayFastFlowTest {
    private static final String MERCHANT_ID = "10000100";
    private static final String MERCHANT_KEY = "46f0cd694581a";
    private static final String PASSPHRASE = "local-test-passphrase";

    @Autowired private MockMvc mvc;
    @Autowired private PayFastProperties payfast;
    @Autowired private TenantRepository tenants;
    @Autowired private ClientRepository clients;
    @Autowired private InvoiceRepository invoices;
    @Autowired private CallRecordRepository calls;
    private final ObjectMapper json = new ObjectMapper();
    private PayFastProperties saved;

    @BeforeEach
    void sandboxMerchant() {
        saved = new PayFastProperties();
        saved.setMerchantId(payfast.getMerchantId());
        saved.setMerchantKey(payfast.getMerchantKey());
        saved.setPassphrase(payfast.getPassphrase());
        saved.setEnv(payfast.getEnv());
        saved.setReturnUrl(payfast.getReturnUrl());
        saved.setCancelUrl(payfast.getCancelUrl());
        saved.setNotifyUrl(payfast.getNotifyUrl());
        saved.setSandboxLocal(payfast.isSandboxLocal());
        payfast.setMerchantId(MERCHANT_ID);
        payfast.setMerchantKey(MERCHANT_KEY);
        payfast.setPassphrase(PASSPHRASE);
        payfast.setEnv("sandbox");
        payfast.setReturnUrl("http://localhost:3000/product-billing/return");
        payfast.setCancelUrl("http://localhost:3000/product-billing/cancel");
        payfast.setNotifyUrl("http://localhost:8080/api/v1/product-billing/payfast/itn");
        payfast.setSandboxLocal(true);
    }

    @AfterEach
    void restore() {
        payfast.setMerchantId(saved.getMerchantId());
        payfast.setMerchantKey(saved.getMerchantKey());
        payfast.setPassphrase(saved.getPassphrase());
        payfast.setEnv(saved.getEnv());
        payfast.setReturnUrl(saved.getReturnUrl());
        payfast.setCancelUrl(saved.getCancelUrl());
        payfast.setNotifyUrl(saved.getNotifyUrl());
        payfast.setSandboxLocal(saved.isSandboxLocal());
    }

    @Test
    void partPaymentProofReviewAndWriteOffSettleAnInvoice() throws Exception {
        String thabo = token("thabo@ndlovulaw.co.za", "password");
        String nomsa = portalToken();
        Invoice inv = invoiceForNomsa("INV-FLOW-1", "1000.00");

        // Staff record a part payment by EFT.
        JsonNode part = data(call(post("/api/v1/invoices/" + inv.getId() + "/payments"), thabo,
                "{\"amount\":\"300\",\"method\":\"eft\",\"reference\":\"EFT-1\"}").andExpect(status().isOk()).andReturn());
        assertEquals("partial", part.get("status").asText());
        assertEquals(0, new BigDecimal("700.00").compareTo(part.get("balanceDue").decimalValue()));
        // Overpaying is refused.
        call(post("/api/v1/invoices/" + inv.getId() + "/payments"), thabo, "{\"amount\":\"900\",\"method\":\"eft\"}")
                .andExpect(status().isBadRequest());

        // The client uploads proof from the portal. The staff route is closed to a portal token.
        MockMultipartFile pdf = new MockMultipartFile("file", "pop.pdf", "application/pdf", "%PDF-1.4 proof".getBytes());
        mvc.perform(multipart("/api/v1/invoices/" + inv.getId() + "/proofs").file(pdf).param("amount", "500")
                        .header("Authorization", "Bearer " + nomsa))
                .andExpect(status().isForbidden());
        JsonNode proof = data(mvc.perform(multipart("/api/v1/portal/invoices/" + inv.getId() + "/proofs").file(pdf)
                        .param("amount", "500").param("reference", "POP-77")
                        .header("Authorization", "Bearer " + nomsa))
                .andExpect(status().isOk()).andReturn());
        assertEquals("pending_review", proof.get("status").asText());
        // A non-PDF/image file is refused.
        MockMultipartFile exe = new MockMultipartFile("file", "pop.exe", "application/octet-stream", new byte[] {1, 2});
        mvc.perform(multipart("/api/v1/portal/invoices/" + inv.getId() + "/proofs").file(exe)
                        .header("Authorization", "Bearer " + nomsa))
                .andExpect(status().isBadRequest());

        // Staff see it in the review queue and accept it.
        JsonNode queue = data(mvc.perform(get("/api/v1/payment-proofs?status=pending_review").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn());
        String proofId = proof.get("id").asText();
        boolean listed = false;
        for (JsonNode row : queue) listed |= proofId.equals(row.get("id").asText());
        assertTrue(listed, "proof should be in the staff review queue");
        JsonNode accepted = data(call(post("/api/v1/payment-proofs/" + proofId + "/accept"), thabo, "{\"method\":\"eft\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("partial", accepted.get("status").asText());
        assertEquals(0, new BigDecimal("200.00").compareTo(accepted.get("balanceDue").decimalValue()));

        // The rest is written off with a reason, which closes the invoice.
        call(post("/api/v1/invoices/" + inv.getId() + "/write-off"), thabo, "{\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        JsonNode closed = data(call(post("/api/v1/invoices/" + inv.getId() + "/write-off"), thabo,
                "{\"reason\":\"Goodwill on settlement\"}").andExpect(status().isOk()).andReturn());
        assertEquals("write_off", closed.get("status").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(closed.get("balanceDue").decimalValue()));
        assertEquals(2, closed.get("payments").size());

        // The client sees the settled invoice on the portal.
        JsonNode portal = data(mvc.perform(get("/api/v1/portal/invoices").header("Authorization", "Bearer " + nomsa))
                .andExpect(status().isOk()).andReturn());
        boolean seen = false;
        for (JsonNode row : portal) seen |= "INV-FLOW-1".equals(row.path("invoiceNumber").asText());
        assertTrue(seen);

        // Each money step is in the audit log.
        String audit = mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String action : List.of("invoice.payment", "invoice.proof_submitted", "invoice.proof_accepted", "invoice.write_off")) {
            assertTrue(audit.contains(action), "audit should contain " + action);
        }
    }

    @Test
    void payFastSeatCheckoutItnAndMinutesChargeInSandbox() throws Exception {
        String thabo = token("thabo@ndlovulaw.co.za", "password");
        String sipho = token("sipho@ndlovulaw.co.za", "password");

        // Only a firm admin can start checkout.
        call(post("/api/v1/product-billing/seats/checkout"), sipho, "{\"seats\":1}").andExpect(status().isForbidden());
        JsonNode form = data(call(post("/api/v1/product-billing/seats/checkout"), thabo, "{\"seats\":2}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("https://sandbox.payfast.co.za/eng/process", form.get("action").asText());
        String mPaymentId = form.get("mPaymentId").asText();
        String amount = form.get("amount").asText();
        List<PayFastSignature.Field> fields = new ArrayList<>();
        form.get("fields").forEach(f -> fields.add(new PayFastSignature.Field(f.get("name").asText(), f.get("value").asText())));
        assertEquals(MERCHANT_ID, PayFastSignature.value(fields, "merchant_id"));
        assertTrue(PayFastSignature.matches(fields, PASSPHRASE, PayFastSignature.value(fields, "signature")),
                "checkout form must carry a valid signature");

        // A forged ITN (wrong merchant) and a tampered amount do not activate the seat.
        itn(List.of(f("m_payment_id", mPaymentId), f("pf_payment_id", "900001"), f("payment_status", "COMPLETE"),
                f("amount_gross", amount), f("merchant_id", "999999"), f("token", "tok-aaaaaaaa-1111")))
                .andExpect(status().isBadRequest()).andExpect(content().string("merchant"));
        itn(List.of(f("m_payment_id", mPaymentId), f("pf_payment_id", "900002"), f("payment_status", "COMPLETE"),
                f("amount_gross", "1.00"), f("merchant_id", MERCHANT_ID)))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
        assertEquals("pending", billingStatus(thabo).get("seatStatus").asText());

        // The real ITN activates two seats and stores the card token.
        itn(List.of(f("m_payment_id", mPaymentId), f("pf_payment_id", "900003"), f("payment_status", "COMPLETE"),
                f("amount_gross", amount), f("merchant_id", MERCHANT_ID), f("token", "tok-aaaaaaaa-2222")))
                .andExpect(status().isOk());
        JsonNode active = billingStatus(thabo);
        assertEquals("active", active.get("seatStatus").asText());
        assertEquals(2, active.get("seatCount").asInt());
        // Replaying the same ITN is harmless.
        itn(List.of(f("m_payment_id", mPaymentId), f("pf_payment_id", "900003"), f("payment_status", "COMPLETE"),
                f("amount_gross", amount), f("merchant_id", MERCHANT_ID), f("token", "tok-aaaaaaaa-2222")))
                .andExpect(status().isOk());

        // Public-network minutes for last month are charged against the stored token.
        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        YearMonth period = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        CallRecord call = new CallRecord();
        call.setTenantId(firm);
        call.setCallType("pstn_outbound");
        call.setStatus("completed");
        call.setStartedAt(period.atDay(10).atStartOfDay().toInstant(ZoneOffset.UTC));
        call.setDurationSeconds(600);
        call.setTotalCost(new BigDecimal("12.50"));
        calls.save(call);
        call(post("/api/v1/product-billing/minutes/charge"), sipho, "{\"period\":\"" + period + "\"}")
                .andExpect(status().isForbidden());
        JsonNode charged = data(call(post("/api/v1/product-billing/minutes/charge"), thabo, "{\"period\":\"" + period + "\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("charged", charged.get("status").asText());
        assertEquals(1250, charged.get("amountCents").asLong());
    }

    private PayFastSignature.Field f(String name, String value) {
        return new PayFastSignature.Field(name, value);
    }

    private org.springframework.test.web.servlet.ResultActions itn(List<PayFastSignature.Field> posted) throws Exception {
        List<PayFastSignature.Field> signed = new ArrayList<>(posted);
        signed.add(f("signature", PayFastSignature.sign(posted, PASSPHRASE)));
        return mvc.perform(post("/api/v1/product-billing/payfast/itn")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(PayFastSignature.formBody(signed)));
    }

    private JsonNode billingStatus(String token) throws Exception {
        return data(mvc.perform(get("/api/v1/product-billing").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private Invoice invoiceForNomsa(String number, String total) {
        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        Client nomsa = clients.findByTenantIdAndEmailIgnoreCase(firm, "nomsa@example.com").orElseThrow();
        Invoice inv = new Invoice();
        inv.setTenantId(firm);
        inv.setClientId(nomsa.getId());
        inv.setInvoiceNumber(number);
        inv.setStatus("sent");
        inv.setSubtotal(new BigDecimal(total));
        inv.setTotal(new BigDecimal(total));
        inv.setDateDue(LocalDate.now().plusDays(30));
        return invoices.save(inv);
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
