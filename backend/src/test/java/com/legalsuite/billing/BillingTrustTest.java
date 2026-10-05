package com.legalsuite.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** B3 and B7 from the A to Z report. Runs on the Smith tenant with its own client, matter and trust account. */
@SpringBootTest
@AutoConfigureMockMvc
class BillingTrustTest {
    @Autowired private MockMvc mvc;
    @Autowired private TenantRepository tenants;
    @Autowired private com.legalsuite.repo.InvoicePaymentRepository payments;
    @Autowired private ClientRepository clients;
    @Autowired private LegalCaseRepository cases;
    @Autowired private TimeEntryRepository timeEntries;
    @Autowired private ExpenseRepository expenses;
    @Autowired private InvoiceRepository invoices;
    @Autowired private TrustAccountRepository trusts;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void invoiceForOneClientBillsOnlyThatClientsMatters() throws Exception {
        UUID firm = smith().getId();
        Client alpha = client(firm, "Alpha");
        Client beta = client(firm, "Beta");
        LegalCase alphaMatter = matter(firm, alpha.getId(), "B3-A");
        LegalCase betaMatter = matter(firm, beta.getId(), "B3-B");
        TimeEntry alphaTime = time(firm, alphaMatter.getId(), "Alpha consultation", "700.00");
        TimeEntry betaTime = time(firm, betaMatter.getId(), "Beta consultation", "900.00");
        TimeEntry noMatterTime = time(firm, null, "Unassigned research", "300.00");
        Expense alphaExpense = expense(firm, alphaMatter.getId(), "Alpha sheriff", "120.00");
        Expense noMatterExpense = expense(firm, null, "Loose courier", "45.00");

        String john = token();
        JsonNode invoice = data(call(post("/api/v1/invoices/generate"), john, "{\"clientId\":\"" + alpha.getId() + "\"}")
                .andExpect(status().isOk()));
        assertEquals(0, new BigDecimal("820.00").compareTo(invoice.get("subtotal").decimalValue()));
        String raw = invoice.get("rawLineItems").asText();
        assertTrue(raw.contains("Alpha consultation"));
        assertTrue(raw.contains("Alpha sheriff"));
        assertFalse(raw.contains("Beta"));
        assertFalse(raw.contains("Unassigned research"));
        assertFalse(raw.contains("Loose courier"));

        assertTrue(timeEntries.findById(alphaTime.getId()).orElseThrow().isBilled());
        assertTrue(expenses.findById(alphaExpense.getId()).orElseThrow().isBilled());
        assertFalse(timeEntries.findById(betaTime.getId()).orElseThrow().isBilled());
        assertFalse(timeEntries.findById(noMatterTime.getId()).orElseThrow().isBilled());
        assertFalse(expenses.findById(noMatterExpense.getId()).orElseThrow().isBilled());

        // Beta's own invoice still gets Beta's work.
        JsonNode betaInvoice = data(call(post("/api/v1/invoices/generate"), john, "{\"clientId\":\"" + beta.getId() + "\"}")
                .andExpect(status().isOk()));
        assertEquals(0, new BigDecimal("900.00").compareTo(betaInvoice.get("subtotal").decimalValue()));

        // A matter that belongs to someone else, or no client at all, is refused.
        call(post("/api/v1/invoices/generate"), john,
                "{\"clientId\":\"" + alpha.getId() + "\",\"caseId\":\"" + betaMatter.getId() + "\"}")
                .andExpect(status().isBadRequest());
        call(post("/api/v1/invoices/generate"), john, "{\"clientId\":\"\"}").andExpect(status().isBadRequest());
        call(post("/api/v1/invoices/generate"), john, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void trustMovementTypesAreWhitelistedAndFeeTransfersPayTheInvoice() throws Exception {
        UUID firm = smith().getId();
        Client client = client(firm, "Trust");
        LegalCase matter = matter(firm, client.getId(), "B7-T");
        TrustAccount account = new TrustAccount();
        account.setTenantId(firm);
        account.setAccountName("B7 test trust");
        account.setBankName("Test bank");
        account = trusts.save(account);
        String acct = account.getId().toString();
        String john = token();

        call(post("/api/v1/trust/move"), john, move(acct, "deposit", "1000", "\"caseId\":\"" + matter.getId() + "\""))
                .andExpect(status().isOk());
        assertEquals(0, new BigDecimal("1000").compareTo(balance(account.getId())));

        // The old behaviour: unknown types were booked as deposits and the balance went up.
        call(post("/api/v1/trust/move"), john, move(acct, "transfer_to_fees", "500", "\"clientId\":\"" + client.getId() + "\""))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "bonus", "500", null)).andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "deposit", "-5", null)).andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "withdrawal", "100", null)).andExpect(status().isBadRequest());
        assertEquals(0, new BigDecimal("1000").compareTo(balance(account.getId())));

        Invoice draft = invoice(firm, client.getId(), "draft", "400.00");
        Invoice sent = invoice(firm, client.getId(), "sent", "400.00");
        Invoice otherClients = invoice(firm, UUID.randomUUID(), "sent", "400.00");
        String clientField = "\"clientId\":\"" + client.getId() + "\"";
        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "100", clientField))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "100", clientField + ",\"invoiceId\":\"" + draft.getId() + "\""))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "100", clientField + ",\"invoiceId\":\"" + otherClients.getId() + "\""))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "450", clientField + ",\"invoiceId\":\"" + sent.getId() + "\""))
                .andExpect(status().isBadRequest());

        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "250", clientField + ",\"invoiceId\":\"" + sent.getId() + "\""))
                .andExpect(status().isOk());
        assertEquals(0, new BigDecimal("750").compareTo(balance(account.getId())));
        Invoice part = invoices.findById(sent.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("250").compareTo(part.getAmountPaid()));
        assertEquals("partial", part.getStatus());
        // The transfer is recorded once, as a trust payment that points back at the trust ledger row.
        var trustPayments = payments.findByTenantIdAndInvoiceIdOrderByPaidAtAscCreatedAtAsc(firm, sent.getId());
        assertEquals(1, trustPayments.size());
        assertEquals("trust", trustPayments.get(0).getMethod());
        assertNotNull(trustPayments.get(0).getTrustTransactionId());

        call(post("/api/v1/trust/move"), john, move(acct, "fee_transfer", "150", clientField + ",\"invoiceId\":\"" + sent.getId() + "\""))
                .andExpect(status().isOk());
        assertEquals("paid", invoices.findById(sent.getId()).orElseThrow().getStatus());
        assertEquals(2, payments.findByTenantIdAndInvoiceIdOrderByPaidAtAscCreatedAtAsc(firm, sent.getId()).size());
        assertEquals(0, new BigDecimal("600").compareTo(balance(account.getId())));

        // Book, client ledger and bank still agree: the fee transfer came off the client's ledger.
        JsonNode recon = data(mvc.perform(get("/api/v1/trust/accounts/" + acct + "/recon").header("Authorization", "Bearer " + john))
                .andExpect(status().isOk()));
        assertEquals(0, new BigDecimal("600").compareTo(recon.get("clientLedgerTotal").decimalValue()));
        assertEquals(0, new BigDecimal("600").compareTo(recon.get("bookBalance").decimalValue()));

        // A withdrawal larger than this client's ledger is refused.
        call(post("/api/v1/trust/move"), john, move(acct, "withdrawal", "601", clientField)).andExpect(status().isBadRequest());
    }

    private Tenant smith() {
        return tenants.findBySlug("smith-associates").orElseThrow();
    }

    private Client client(UUID tenantId, String name) {
        Client c = new Client();
        c.setTenantId(tenantId);
        c.setFirstName(name);
        c.setLastName("Test " + UUID.randomUUID().toString().substring(0, 6));
        return clients.save(c);
    }

    private LegalCase matter(UUID tenantId, UUID clientId, String number) {
        LegalCase m = new LegalCase();
        m.setTenantId(tenantId);
        m.setClientId(clientId);
        m.setCaseNumber(number + "-" + UUID.randomUUID().toString().substring(0, 4));
        m.setTitle(number);
        return cases.save(m);
    }

    private TimeEntry time(UUID tenantId, UUID caseId, String description, String amount) {
        TimeEntry t = new TimeEntry();
        t.setTenantId(tenantId);
        t.setCaseId(caseId);
        t.setDescription(description);
        t.setDurationMinutes(60);
        t.setHourlyRate(new BigDecimal(amount));
        t.setTotalAmount(new BigDecimal(amount));
        t.setBillable(true);
        t.setBilled(false);
        return timeEntries.save(t);
    }

    private Expense expense(UUID tenantId, UUID caseId, String description, String amount) {
        Expense e = new Expense();
        e.setTenantId(tenantId);
        e.setCaseId(caseId);
        e.setDescription(description);
        e.setCategory("travel");
        e.setAmount(new BigDecimal(amount));
        e.setBillable(true);
        e.setBilled(false);
        return expenses.save(e);
    }

    private Invoice invoice(UUID tenantId, UUID clientId, String state, String total) {
        Invoice inv = new Invoice();
        inv.setTenantId(tenantId);
        inv.setClientId(clientId);
        inv.setInvoiceNumber("INV-T-" + UUID.randomUUID().toString().substring(0, 6));
        inv.setStatus(state);
        inv.setSubtotal(new BigDecimal(total));
        inv.setTotal(new BigDecimal(total));
        return invoices.save(inv);
    }

    private BigDecimal balance(UUID accountId) {
        return trusts.findById(accountId).orElseThrow().getBalance();
    }

    private static String move(String account, String type, String amount, String extra) {
        return "{\"accountId\":\"" + account + "\",\"type\":\"" + type + "\",\"amount\":\"" + amount + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body)
            throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"smith-associates\",\"email\":\"john@smithlaw.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
