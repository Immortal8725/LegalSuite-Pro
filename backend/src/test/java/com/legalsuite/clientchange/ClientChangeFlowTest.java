package com.legalsuite.clientchange;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.*;
import com.legalsuite.milestones.MatterMilestone;
import com.legalsuite.milestones.MatterMilestoneRepository;
import com.legalsuite.repo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Change client and merge: who may do it, what moves, what never gets edited, and who can see what afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
class ClientChangeFlowTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired ClientRepository clients;
    @Autowired LegalCaseRepository cases;
    @Autowired AppUserRepository users;
    @Autowired MatterMessageRepository messages;
    @Autowired DocumentFileRepository documents;
    @Autowired SignatureRequestRepository signatures;
    @Autowired MatterMilestoneRepository milestones;
    @Autowired InvoiceRepository invoices;
    @Autowired TrustAccountRepository trustAccounts;
    @Autowired TrustTransactionRepository trustTx;
    @Autowired TrustClientTransferRepository transfers;
    @Autowired PasswordEncoder encoder;
    final ObjectMapper json = new ObjectMapper();

    UUID firm;

    @Test
    void changeClientFullFlow() throws Exception {
        firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        Client wrong = clients.findAll().stream().filter(c -> firm.equals(c.getTenantId()) && "nomsa@example.com".equals(c.getEmail())).findFirst().orElseThrow();
        Client right = newClient("Right", "Person", "right.person@example.test", "portal456");
        AppUser lindiwe = users.findAll().stream().filter(u -> "lindiwe@ndlovulaw.co.za".equals(u.getEmail())).findFirst().orElseThrow();

        LegalCase m = new LegalCase();
        m.setTenantId(firm);
        m.setClientId(wrong.getId());
        m.setCaseNumber("CC-" + UUID.randomUUID().toString().substring(0, 6));
        m.setTitle("Mixed up matter");
        m.setStatus("open");
        m.setEngagementStatus("signed");
        m.setAppearanceAuthorized(true);
        m.setLeadAttorneyId(lindiwe.getId());
        m = cases.save(m);

        MatterMessage seen = msg(m, wrong, "staff", "Your court date is set");
        seen.setReadByClientAt(Instant.now());
        messages.save(seen);
        DocumentFile d = new DocumentFile();
        d.setTenantId(firm); d.setCaseId(m.getId()); d.setClientId(wrong.getId()); d.setName("affidavit.pdf");
        d = documents.save(d);
        SignatureRequest sig = new SignatureRequest();
        sig.setTenantId(firm); sig.setCaseId(m.getId()); sig.setClientId(wrong.getId()); sig.setTitle("Mandate");
        sig.setSignerName(wrong.displayName()); sig.setSignerEmail("nomsa@example.com"); sig.setStatus("pending");
        sig = signatures.save(sig);
        MatterMilestone ms = new MatterMilestone();
        ms.setTenantId(firm); ms.setCaseId(m.getId()); ms.setClientId(wrong.getId()); ms.setLabel("Deposit"); ms.setKind("deposit");
        ms.setAmount(new BigDecimal("1000.00")); ms.setSortOrder(1);
        ms = milestones.save(ms);
        Invoice unpaid = invoice(m, wrong, "CC-INV-1", "1500.00", "0");
        Invoice paid = invoice(m, wrong, "CC-INV-2", "800.00", "800.00");
        paid.setStatus("paid"); invoices.save(paid);
        TrustAccount acct = new TrustAccount();
        acct.setTenantId(firm); acct.setAccountName("CC test trust"); acct.setBalance(new BigDecimal("1000.00"));
        acct = trustAccounts.save(acct);
        TrustTransaction dep = new TrustTransaction();
        dep.setTenantId(firm); dep.setTrustAccountId(acct.getId()); dep.setClientId(wrong.getId()); dep.setCaseId(m.getId());
        dep.setType("deposit"); dep.setAmount(new BigDecimal("1000.00")); dep.setBalanceAfter(new BigDecimal("1000.00")); dep.setDescription("deposit");
        trustTx.save(dep);

        String thabo = staff("thabo@ndlovulaw.co.za");
        String sipho = staff("sipho@ndlovulaw.co.za");
        String lindi = staff("lindiwe@ndlovulaw.co.za");
        String path = "/api/v1/cases/" + m.getId() + "/change-client";

        // Permissions: an associate who is not the lead attorney cannot preview or change.
        mvc.perform(get(path + "/preview").param("toClientId", right.getId().toString()).header("Authorization", "Bearer " + sipho))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + sipho).contentType(MediaType.APPLICATION_JSON)
                .content(body(right.getId(), "Wrong client picked at intake"))).andExpect(status().isForbidden());

        JsonNode pv = data(mvc.perform(get(path + "/preview").param("toClientId", right.getId().toString()).header("Authorization", "Bearer " + lindi))
                .andExpect(status().isOk()));
        assertTrue(pv.get("possiblePopiaIncident").asBoolean(), "read receipt must flag a possible POPIA incident");
        assertEquals(1, pv.get("counts").get("unpaidInvoicesToMove").asInt());
        assertEquals(1, pv.get("counts").get("paidInvoicesNeedingCreditNote").asInt());
        assertEquals(1, pv.get("trust").size());

        // Reason is required.
        mvc.perform(post(path).header("Authorization", "Bearer " + lindi).contentType(MediaType.APPLICATION_JSON)
                .content(body(right.getId(), "oops"))).andExpect(status().isBadRequest());

        JsonNode res = data(mvc.perform(post(path).header("Authorization", "Bearer " + lindi).contentType(MediaType.APPLICATION_JSON)
                .content(body(right.getId(), "Wrong client picked at intake"))).andExpect(status().isOk()));
        assertTrue(res.get("possiblePopiaIncident").asBoolean());

        LegalCase after = cases.findById(m.getId()).orElseThrow();
        assertEquals(right.getId(), after.getClientId());
        assertEquals("unsigned", after.getEngagementStatus());
        assertFalse(after.isAppearanceAuthorized());
        assertEquals("void", signatures.findById(sig.getId()).orElseThrow().getStatus());
        assertEquals(right.getId(), documents.findById(d.getId()).orElseThrow().getClientId());
        assertEquals(right.getId(), milestones.findById(ms.getId()).orElseThrow().getClientId());
        assertEquals(right.getId(), invoices.findById(unpaid.getId()).orElseThrow().getClientId());
        Invoice paidAfter = invoices.findById(paid.getId()).orElseThrow();
        assertEquals(wrong.getId(), paidAfter.getClientId(), "paid invoice is never edited");
        assertEquals(0, paidAfter.getAmountPaid().compareTo(new BigDecimal("800.00")));
        Invoice cn = invoices.findByTenantIdAndClientId(firm, wrong.getId()).stream().filter(i -> "CC-INV-2-CN".equals(i.getInvoiceNumber())).findFirst().orElseThrow();
        assertEquals("credit_note", cn.getStatus());
        assertEquals(0, cn.getTotal().compareTo(new BigDecimal("-800.00")));
        MatterMessage seenAfter = messages.findById(seen.getId()).orElseThrow();
        assertTrue(seenAfter.isHiddenFromClient(), "earlier messages hidden by default");
        assertEquals(right.getId(), seenAfter.getClientId());

        // Trust: nothing posted yet, the deposit row is untouched.
        assertEquals(wrong.getId(), trustTx.findById(dep.getId()).orElseThrow().getClientId());
        assertEquals(1, trustTx.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(firm, acct.getId()).size());
        TrustClientTransfer t = transfers.findByTenantIdAndCaseIdOrderByCreatedAtDesc(firm, m.getId()).get(0);
        assertEquals("pending", t.getStatus());
        assertEquals(0, t.getAmount().compareTo(new BigDecimal("1000.00")));

        // Isolation: the wrong client loses the matter; the right client sees it but not the earlier history.
        String wrongPortal = portal("nomsa@example.com", "portal123");
        int st = mvc.perform(get("/api/v1/portal/cases/" + m.getId()).header("Authorization", "Bearer " + wrongPortal)).andReturn().getResponse().getStatus();
        assertTrue(st == 403 || st == 404, "wrong client must not open the matter, got " + st);
        String rightPortal = portal("right.person@example.test", "portal456");
        JsonNode pm = data(mvc.perform(get("/api/v1/portal/cases/" + m.getId()).header("Authorization", "Bearer " + rightPortal)).andExpect(status().isOk()));
        assertEquals(0, pm.get("messages").size(), "hidden history not shown to the new client");

        // Second approver: lindiwe asked, so lindiwe (and non-admins) cannot approve; thabo can.
        String tp = "/api/v1/trust/client-transfers/" + t.getId();
        mvc.perform(post(tp + "/approve").header("Authorization", "Bearer " + lindi)).andExpect(status().isForbidden());
        mvc.perform(post(tp + "/approve").header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        TrustAccount acctAfter = trustAccounts.findById(acct.getId()).orElseThrow();
        assertEquals(0, acctAfter.getBalance().compareTo(new BigDecimal("1000.00")), "bank balance unchanged");
        BigDecimal wrongLedger = BigDecimal.ZERO, rightLedger = BigDecimal.ZERO;
        for (TrustTransaction x : trustTx.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(firm, acct.getId())) {
            BigDecimal s = x.getType().endsWith("_out") || "withdrawal".equals(x.getType()) ? x.getAmount().negate() : x.getAmount();
            if (wrong.getId().equals(x.getClientId())) wrongLedger = wrongLedger.add(s);
            if (right.getId().equals(x.getClientId())) rightLedger = rightLedger.add(s);
        }
        assertEquals(0, wrongLedger.signum());
        assertEquals(0, rightLedger.compareTo(new BigDecimal("1000.00")));
        mvc.perform(post(tp + "/approve").header("Authorization", "Bearer " + thabo)).andExpect(status().isBadRequest());
        documents.delete(documents.findById(d.getId()).orElseThrow()); // no file behind it; keep the shared demo data clean
    }

    @Test
    void mergeIsAdminOnlyAndSelfApprovalBlocked() throws Exception {
        firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        Client dup = newClient("Dup", "Person", "dup.a@example.test", null);
        Client keep = newClient("Dup", "Person", "dup.b@example.test", null);
        LegalCase m = new LegalCase();
        m.setTenantId(firm); m.setClientId(dup.getId()); m.setCaseNumber("MG-" + UUID.randomUUID().toString().substring(0, 6));
        m.setTitle("Dup matter"); m.setStatus("open");
        m = cases.save(m);
        Invoice paid = invoice(m, dup, "MG-INV-1", "500.00", "500.00");
        TrustAccount acct = new TrustAccount();
        acct.setTenantId(firm); acct.setAccountName("MG test trust"); acct.setBalance(new BigDecimal("300.00"));
        acct = trustAccounts.save(acct);
        TrustTransaction dep = new TrustTransaction();
        dep.setTenantId(firm); dep.setTrustAccountId(acct.getId()); dep.setClientId(dup.getId()); dep.setCaseId(m.getId());
        dep.setType("deposit"); dep.setAmount(new BigDecimal("300.00")); dep.setBalanceAfter(new BigDecimal("300.00")); dep.setDescription("deposit");
        trustTx.save(dep);

        String lindi = staff("lindiwe@ndlovulaw.co.za");
        String thabo = staff("thabo@ndlovulaw.co.za");
        String mp = "/api/v1/clients/" + dup.getId() + "/merge";
        String b = "{\"intoClientId\":\"" + keep.getId() + "\",\"reason\":\"Same person captured twice\"}";
        mvc.perform(post(mp).header("Authorization", "Bearer " + lindi).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isForbidden());
        mvc.perform(post(mp).header("Authorization", "Bearer " + thabo).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isOk());

        assertEquals(keep.getId(), cases.findById(m.getId()).orElseThrow().getClientId());
        assertEquals("merged", clients.findById(dup.getId()).orElseThrow().getStatus());
        assertEquals(dup.getId(), invoices.findById(paid.getId()).orElseThrow().getClientId(), "paid invoice stays on the old record");
        TrustClientTransfer t = transfers.findByTenantIdOrderByCreatedAtDesc(firm).stream()
                .filter(x -> x.getTrustAccountId().equals(trustAccounts.findAll().stream().filter(a -> "MG test trust".equals(a.getAccountName())).findFirst().orElseThrow().getId()))
                .findFirst().orElseThrow();
        assertEquals(m.getId(), t.getCaseId());
        String tp = "/api/v1/trust/client-transfers/" + t.getId();
        mvc.perform(post(tp + "/approve").header("Authorization", "Bearer " + thabo)).andExpect(status().isForbidden());

        AppUser second = new AppUser();
        second.setTenantId(firm); second.setEmail("second.partner@ndlovulaw.test"); second.setPasswordHash(encoder.encode("password"));
        second.setFirstName("Second"); second.setLastName("Partner"); second.setRole("partner");
        users.save(second);
        String sp = staff("second.partner@ndlovulaw.test");
        mvc.perform(post(tp + "/approve").header("Authorization", "Bearer " + sp)).andExpect(status().isOk());
        assertEquals("approved", transfers.findById(t.getId()).orElseThrow().getStatus());

        // Another firm cannot touch it.
        String john = data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"smith-associates\",\"email\":\"john@smithlaw.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
        int st = mvc.perform(post("/api/v1/cases/" + m.getId() + "/change-client").header("Authorization", "Bearer " + john)
                .contentType(MediaType.APPLICATION_JSON).content(body(dup.getId(), "Cross firm attempt here"))).andReturn().getResponse().getStatus();
        assertTrue(st == 403 || st == 404, "other firm blocked, got " + st);
    }

    Client newClient(String first, String last, String email, String portalPw) {
        Client c = new Client();
        c.setTenantId(firm); c.setType("individual"); c.setFirstName(first); c.setLastName(last); c.setEmail(email);
        c.setStatus("active");
        if (portalPw != null) { c.setPortalEnabled(true); c.setPortalPasswordHash(encoder.encode(portalPw)); }
        return clients.save(c);
    }

    MatterMessage msg(LegalCase m, Client c, String sender, String text) {
        MatterMessage x = new MatterMessage();
        x.setTenantId(firm); x.setCaseId(m.getId()); x.setClientId(c.getId()); x.setSenderType(sender); x.setBody(text);
        return messages.save(x);
    }

    Invoice invoice(LegalCase m, Client c, String no, String total, String paidAmt) {
        Invoice i = new Invoice();
        i.setTenantId(firm); i.setCaseId(m.getId()); i.setClientId(c.getId()); i.setInvoiceNumber(no); i.setStatus("sent");
        i.setSubtotal(new BigDecimal(total)); i.setTotal(new BigDecimal(total)); i.setAmountPaid(new BigDecimal(paidAmt));
        return invoices.save(i);
    }

    String body(UUID to, String reason) {
        return "{\"toClientId\":\"" + to + "\",\"reason\":\"" + reason + "\",\"conflictAcknowledged\":true}";
    }

    String staff(String email) throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    String portal(String email, String pw) throws Exception {
        return data(mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
