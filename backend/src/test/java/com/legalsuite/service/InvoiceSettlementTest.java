package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TrustAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@Transactional
class InvoiceSettlementTest {
    @Autowired
    private FinanceService finance;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private LegalCaseRepository cases;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private TrustAccountRepository trusts;

    private Tenant smith;
    private UUID userId;

    @BeforeEach
    void context() {
        smith = tenants.findBySlug("smith-associates").orElseThrow();
        userId = UUID.randomUUID();
        TenantContext.setTenantId(smith.getId());
        TenantContext.setUserId(userId);
        TenantContext.setRole("attorney");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void partialPaymentThenFullPaymentUpdatesBalanceAndStatus() {
        UUID clientId = UUID.randomUUID();
        Invoice inv = invoice(clientId, null, "INV-PAY-1", "100.00");

        Map<String, Object> partial = finance.recordPayment(inv.getId(), Map.of(
                "amount", "40.00",
                "method", "eft",
                "reference", "EFT-100",
                "note", "First transfer"));

        assertEquals("partial", partial.get("status"));
        assertEquals(0, new BigDecimal("40.00").compareTo(money(partial.get("amountPaid"))));
        assertEquals(0, new BigDecimal("60.00").compareTo(money(partial.get("balanceDue"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(partial.get("total"))));
        List<Map<String, Object>> payments = paymentsOf(partial);
        assertEquals(1, payments.size());
        assertEquals("eft", payments.get(0).get("method"));
        assertEquals("EFT-100", payments.get(0).get("reference"));

        Map<String, Object> paid = finance.recordPayment(inv.getId(), Map.of(
                "amount", "60.00",
                "method", "cash"));
        assertEquals("paid", paid.get("status"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(paid.get("amountPaid"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(paid.get("balanceDue"))));
        assertEquals(2, paymentsOf(paid).size());
    }

    @Test
    void overpaymentIsRefused() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-PAY-2", "100.00");
        finance.recordPayment(inv.getId(), Map.of("amount", "80.00", "method", "card"));

        ApiException ex = assertThrows(ApiException.class, () -> finance.recordPayment(inv.getId(), Map.of(
                "amount", "21.00",
                "method", "card")));
        assertTrue(ex.getMessage().contains("exceeds the balance due"));
    }

    @Test
    void writeOffKeepsTheOriginalTotalAndClosesTheRemainder() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-WO-1", "100.00");
        finance.recordPayment(inv.getId(), Map.of("amount", "30.00", "method", "cash"));

        Map<String, Object> partial = finance.writeOff(inv.getId(), Map.of(
                "amount", "20.00",
                "reason", "Courtesy reduction agreed with the client"));
        assertEquals("partial", partial.get("status"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(partial.get("total"))));
        assertEquals(0, new BigDecimal("30.00").compareTo(money(partial.get("amountPaid"))));
        assertEquals(0, new BigDecimal("20.00").compareTo(money(partial.get("writeOffAmount"))));
        assertEquals(0, new BigDecimal("50.00").compareTo(money(partial.get("balanceDue"))));
        assertEquals(userId, writeOffsOf(partial).get(0).get("createdBy"));

        Map<String, Object> closed = finance.writeOff(inv.getId(), Map.of(
                "reason", "Remainder waived by the director"));
        assertEquals("write_off", closed.get("status"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(closed.get("total"))));
        assertEquals(0, new BigDecimal("30.00").compareTo(money(closed.get("amountPaid"))));
        assertEquals(0, new BigDecimal("70.00").compareTo(money(closed.get("writeOffAmount"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(closed.get("balanceDue"))));
        assertEquals(2, writeOffsOf(closed).size());
    }

    @Test
    void writeOffAboveTheBalanceIsRefused() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-WO-2", "100.00");
        ApiException ex = assertThrows(ApiException.class, () -> finance.writeOff(inv.getId(), Map.of(
                "amount", "150.00",
                "reason", "Too much")));
        assertTrue(ex.getMessage().contains("exceeds the balance due"));
    }

    @Test
    void trustToFeeWithdrawsTheClientLedgerAndRecordsThePayment() {
        UUID clientId = UUID.randomUUID();
        LegalCase matter = matter(clientId, "C-TRUST-1", true);
        Invoice inv = invoice(clientId, matter.getId(), "INV-TR-1", "100.00");
        TrustAccount acct = trustAccount();
        finance.trustMove(Map.of(
                "accountId", acct.getId().toString(),
                "type", "deposit",
                "amount", "200.00",
                "clientId", clientId.toString(),
                "description", "Retainer"));

        Map<String, Object> partial = finance.applyTrustToInvoice(inv.getId(), Map.of(
                "accountId", acct.getId().toString(),
                "amount", "40.00"));
        assertEquals("partial", partial.get("status"));
        assertEquals(0, new BigDecimal("40.00").compareTo(money(partial.get("amountPaid"))));
        assertEquals(0, new BigDecimal("60.00").compareTo(money(partial.get("balanceDue"))));
        assertEquals("trust", paymentsOf(partial).get(0).get("method"));
        assertEquals(0, new BigDecimal("160.00").compareTo(trusts.findById(acct.getId()).orElseThrow().getBalance()));

        Map<String, Object> paid = finance.applyTrustToInvoice(inv.getId(), Map.of(
                "accountId", acct.getId().toString(),
                "amount", "60.00"));
        assertEquals("paid", paid.get("status"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(paid.get("amountPaid"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(trusts.findById(acct.getId()).orElseThrow().getBalance()));
    }

    @Test
    void trustToFeeRefusesWhenTheClientLedgerIsShort() {
        UUID clientId = UUID.randomUUID();
        UUID otherClient = UUID.randomUUID();
        LegalCase matter = matter(clientId, "C-TRUST-2", true);
        Invoice inv = invoice(clientId, matter.getId(), "INV-TR-2", "100.00");
        TrustAccount acct = trustAccount();
        finance.trustMove(Map.of(
                "accountId", acct.getId().toString(),
                "type", "deposit",
                "amount", "25.00",
                "clientId", otherClient.toString(),
                "description", "Other client's retainer"));

        ApiException ex = assertThrows(ApiException.class, () -> finance.applyTrustToInvoice(inv.getId(), Map.of(
                "accountId", acct.getId().toString(),
                "amount", "25.00")));
        assertTrue(ex.getMessage().toLowerCase().contains("trust") || ex.getMessage().contains("ledger"));
    }

    @Test
    void trustToFeeStaysBehindTheLimitedFileGate() {
        UUID clientId = UUID.randomUUID();
        LegalCase matter = matter(clientId, "C-TRUST-3", false);
        Invoice inv = invoice(clientId, matter.getId(), "INV-TR-3", "50.00");
        TrustAccount acct = trustAccount();
        finance.trustMove(Map.of(
                "accountId", acct.getId().toString(),
                "type", "deposit",
                "amount", "50.00",
                "clientId", clientId.toString(),
                "description", "Pledged retainer"));

        ApiException ex = assertThrows(ApiException.class, () -> finance.applyTrustToInvoice(inv.getId(), Map.of(
                "accountId", acct.getId().toString(),
                "amount", "50.00")));
        assertTrue(ex.getMessage().contains("Limited file"));
    }

    @Test
    void proofSubmitAcceptRecordsOnePaymentAndASecondAcceptDoesNotDoubleCount() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-POP-1", "100.00");
        Map<String, Object> proof = finance.submitProof(inv.getId(), pdf("slip.pdf"), "40.00", "BANK-9", "EFT slip");
        assertEquals("pending_review", proof.get("status"));
        assertEquals(0, new BigDecimal("40.00").compareTo(money(proof.get("amountClaimed"))));

        UUID proofId = UUID.fromString(String.valueOf(proof.get("id")));
        Map<String, Object> accepted = finance.acceptProof(proofId, Map.of("method", "eft"));
        assertEquals("partial", accepted.get("status"));
        assertEquals(0, new BigDecimal("40.00").compareTo(money(accepted.get("amountPaid"))));
        assertEquals(1, paymentsOf(accepted).size());
        assertEquals("eft", paymentsOf(accepted).get(0).get("method"));
        assertEquals(proofId, paymentsOf(accepted).get(0).get("proofId"));
        Map<String, Object> stored = proofsOf(accepted).get(0);
        assertEquals("accepted", stored.get("status"));
        assertEquals(paymentsOf(accepted).get(0).get("id"), stored.get("paymentId"));

        Map<String, Object> again = finance.acceptProof(proofId, Map.of("method", "cash", "amount", "40.00"));
        assertEquals(1, paymentsOf(again).size());
        assertEquals(0, new BigDecimal("40.00").compareTo(money(again.get("amountPaid"))));
        assertEquals("eft", paymentsOf(again).get(0).get("method"));
    }

    @Test
    void rejectedProofLeavesTheInvoiceUnchanged() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-POP-2", "80.00");
        Map<String, Object> proof = finance.submitProof(inv.getId(), pdf("slip.png"), null, "REF-2", null);
        assertEquals("pending_review", proof.get("status"));
        assertEquals(0, new BigDecimal("80.00").compareTo(money(proof.get("amountClaimed"))));

        Map<String, Object> rejected = finance.rejectProof(
                UUID.fromString(String.valueOf(proof.get("id"))),
                Map.of("note", "The slip is for a different account."));
        assertEquals("rejected", rejected.get("status"));
        assertEquals("The slip is for a different account.", rejected.get("reviewNote"));

        Map<String, Object> invoice = finance.getInvoice(inv.getId());
        assertEquals("sent", invoice.get("status"));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(invoice.get("amountPaid"))));
        assertEquals(0, new BigDecimal("80.00").compareTo(money(invoice.get("balanceDue"))));
        assertTrue(proofsOf(invoice).stream().anyMatch(p -> "rejected".equals(p.get("status"))));
    }

    @Test
    void rejectRequiresANote() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-POP-5", "15.00");
        Map<String, Object> proof = finance.submitProof(inv.getId(), pdf("slip.pdf"), null, null, null);
        ApiException missing = assertThrows(ApiException.class, () -> finance.rejectProof(
                UUID.fromString(String.valueOf(proof.get("id"))), Map.of("note", "no")));
        assertTrue(missing.getMessage().contains("rejection note"));
    }

    @Test
    void acceptRefusesWhenTheClaimedAmountWouldOverpay() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-POP-3", "100.00");
        Map<String, Object> proof = finance.submitProof(inv.getId(), pdf("big.pdf"), "80.00", null, null);
        finance.recordPayment(inv.getId(), Map.of("amount", "50.00", "method", "eft"));

        ApiException ex = assertThrows(ApiException.class, () -> finance.acceptProof(
                UUID.fromString(String.valueOf(proof.get("id"))), Map.of()));
        assertTrue(ex.getMessage().contains("exceeds the balance due"));
    }

    @Test
    void proofFileMustBePdfOrImage() {
        Invoice inv = invoice(UUID.randomUUID(), null, "INV-POP-4", "10.00");
        MultipartFile text = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "not a slip".getBytes(StandardCharsets.UTF_8));
        ApiException ex = assertThrows(ApiException.class, () -> finance.submitProof(inv.getId(), text, null, null, null));
        assertTrue(ex.getMessage().contains("PDF"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> paymentsOf(Map<String, Object> invoice) {
        return (List<Map<String, Object>>) invoice.get("payments");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> writeOffsOf(Map<String, Object> invoice) {
        return (List<Map<String, Object>>) invoice.get("writeOffs");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> proofsOf(Map<String, Object> invoice) {
        return (List<Map<String, Object>>) invoice.get("proofs");
    }

    private Invoice invoice(UUID clientId, UUID caseId, String number, String total) {
        Invoice inv = new Invoice();
        inv.setTenantId(smith.getId());
        inv.setClientId(clientId);
        inv.setCaseId(caseId);
        inv.setInvoiceNumber(number);
        inv.setStatus("sent");
        inv.setSubtotal(new BigDecimal(total));
        inv.setTotal(new BigDecimal(total));
        inv.setAmountPaid(BigDecimal.ZERO);
        inv.setDateDue(LocalDate.now().plusDays(14));
        return invoices.save(inv);
    }

    private LegalCase matter(UUID clientId, String number, boolean authorized) {
        LegalCase matter = new LegalCase();
        matter.setTenantId(smith.getId());
        matter.setClientId(clientId);
        matter.setCaseNumber(number);
        matter.setTitle(number);
        matter.setAppearanceAuthorized(authorized);
        matter.setEngagementStatus(authorized ? "signed" : "limited");
        return cases.save(matter);
    }

    private TrustAccount trustAccount() {
        TrustAccount acct = new TrustAccount();
        acct.setTenantId(smith.getId());
        acct.setAccountName("Test IOLTA");
        acct.setBalance(BigDecimal.ZERO);
        return trusts.save(acct);
    }

    private static MultipartFile pdf(String name) {
        String type = name.endsWith(".png") ? "image/png" : "application/pdf";
        return new MockMultipartFile("file", name, type, "%PDF-1.4 proof".getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
