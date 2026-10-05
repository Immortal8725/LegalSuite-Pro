package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InvoiceExpenseLinesTest {
    @Autowired
    private FinanceService finance;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private LegalCaseRepository cases;
    @Autowired
    private TimeEntryRepository timeEntries;
    @Autowired
    private ExpenseRepository expenses;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void generateInvoiceBillsUnbilledBillableExpensesForTheSameCaseFilterAsTime() {
        Tenant smith = tenants.findBySlug("smith-associates").orElseThrow();
        Tenant ndlovu = tenants.findBySlug("ndlovu-partners").orElseThrow();
        TenantContext.setTenantId(smith.getId());
        UUID clientId = UUID.randomUUID();
        LegalCase matter = matter(smith.getId(), clientId, "C-EXP-1");
        LegalCase otherMatter = matter(smith.getId(), UUID.randomUUID(), "C-EXP-2");

        TimeEntry time = time(smith.getId(), matter.getId(), "Research on the motion", new BigDecimal("200.00"));
        Expense filing = expense(smith.getId(), matter.getId(), "County filing fee", "filing fees", new BigDecimal("40.00"), true, false);
        filing.setDate(LocalDate.parse("2026-03-02"));
        filing.setVendor("Travis County Clerk");
        expenses.save(filing);
        Expense courier = expense(smith.getId(), matter.getId(), "Courier to the court", "postage", new BigDecimal("15.00"), true, false);
        Expense internal = expense(smith.getId(), matter.getId(), "Internal printout", "other", new BigDecimal("12.00"), false, false);
        Expense already = expense(smith.getId(), matter.getId(), "Already on a prior bill", "postage", new BigDecimal("8.00"), true, true);
        Expense otherCase = expense(smith.getId(), otherMatter.getId(), "Other matter sheriff", "travel", new BigDecimal("55.00"), true, false);
        Expense otherTenant = expense(ndlovu.getId(), matter.getId(), "Foreign firm disbursement", "expert fees", new BigDecimal("999.00"), true, false);

        Map<String, Object> invoice = finance.generateInvoice(clientId, matter.getId());
        UUID invoiceId = UUID.fromString(String.valueOf(invoice.get("id")));

        assertEquals(0, new BigDecimal("255.00").compareTo(money(invoice.get("subtotal"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(invoice.get("taxAmount"))));
        assertEquals(0, new BigDecimal("255.00").compareTo(money(invoice.get("total"))));

        List<Map<String, Object>> lines = JsonLists.objects(String.valueOf(invoice.get("rawLineItems")));
        assertEquals(3, lines.size());
        assertEquals("time", lines.get(0).get("kind"));
        assertTrue(lines.subList(1, lines.size()).stream().allMatch(line -> "expense".equals(line.get("kind"))));
        assertTrue(lines.stream().anyMatch(line ->
                "time".equals(line.get("kind"))
                        && "Research on the motion".equals(line.get("description"))
                        && Integer.valueOf(60).equals(number(line.get("minutes")))
                        && new BigDecimal("200.00").compareTo(money(line.get("amount"))) == 0));
        assertTrue(lines.stream().anyMatch(line ->
                "expense".equals(line.get("kind"))
                        && "County filing fee".equals(line.get("description"))
                        && "filing fees".equals(line.get("category"))
                        && "2026-03-02".equals(line.get("date"))
                        && "Travis County Clerk".equals(line.get("vendor"))
                        && new BigDecimal("40.00").compareTo(money(line.get("amount"))) == 0));
        assertTrue(lines.stream().anyMatch(line ->
                "expense".equals(line.get("kind"))
                        && "Courier to the court".equals(line.get("description"))
                        && new BigDecimal("15.00").compareTo(money(line.get("amount"))) == 0));
        assertTrue(lines.stream().noneMatch(line -> String.valueOf(line.get("description")).contains("Foreign")));
        assertTrue(lines.stream().noneMatch(line -> String.valueOf(line.get("description")).contains("Internal")));
        assertTrue(lines.stream().noneMatch(line -> String.valueOf(line.get("description")).contains("Already")));
        assertTrue(lines.stream().noneMatch(line -> String.valueOf(line.get("description")).contains("Other matter")));

        Expense billed = expenses.findById(filing.getId()).orElseThrow();
        assertTrue(billed.isBilled());
        assertEquals(invoiceId, billed.getInvoiceId());
        TimeEntry billedTime = timeEntries.findById(time.getId()).orElseThrow();
        assertTrue(billedTime.isBilled());
        assertEquals(invoiceId, billedTime.getInvoiceId());

        assertFalse(expenses.findById(internal.getId()).orElseThrow().isBilled());
        assertNull(expenses.findById(internal.getId()).orElseThrow().getInvoiceId());
        assertTrue(expenses.findById(already.getId()).orElseThrow().isBilled());
        assertNull(expenses.findById(already.getId()).orElseThrow().getInvoiceId());
        assertTrue(expenses.findById(courier.getId()).orElseThrow().isBilled());
        assertEquals(invoiceId, expenses.findById(courier.getId()).orElseThrow().getInvoiceId());
        assertFalse(expenses.findById(otherCase.getId()).orElseThrow().isBilled());
        assertFalse(expenses.findById(otherTenant.getId()).orElseThrow().isBilled());

        Map<String, Object> listed = finance.expenses().stream()
                .filter(row -> filing.getId().equals(row.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, listed.get("billed"));
        assertEquals(invoiceId, listed.get("invoiceId"));

        Map<String, Object> second = finance.generateInvoice(clientId, matter.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(money(second.get("subtotal"))));
        assertTrue(JsonLists.objects(String.valueOf(second.get("rawLineItems"))).isEmpty());
    }

    @Test
    void zaVatAppliesToTheCombinedTimeAndExpenseSubtotal() {
        Tenant ndlovu = tenants.findBySlug("ndlovu-partners").orElseThrow();
        TenantContext.setTenantId(ndlovu.getId());
        UUID clientId = UUID.randomUUID();
        LegalCase matter = matter(ndlovu.getId(), clientId, "C-ZA-EXP");
        time(ndlovu.getId(), matter.getId(), "RAF follow-up", new BigDecimal("100.00"));
        expense(ndlovu.getId(), matter.getId(), "Sheriff service", "travel", new BigDecimal("20.00"), true, false);
        expense(ndlovu.getId(), matter.getId(), "Counsel copies", "postage", new BigDecimal("30.00"), true, false);
        expense(ndlovu.getId(), matter.getId(), "Counsel lunch", "other", new BigDecimal("80.00"), false, false);

        Map<String, Object> invoice = finance.generateInvoice(clientId, matter.getId());

        assertEquals(0, new BigDecimal("150.00").compareTo(money(invoice.get("subtotal"))));
        assertEquals(0, new BigDecimal("22.50").compareTo(money(invoice.get("taxAmount"))));
        assertEquals(0, new BigDecimal("172.50").compareTo(money(invoice.get("total"))));
        assertTrue(String.valueOf(invoice.get("notes")).contains("VAT 15%"));
        List<Map<String, Object>> lines = JsonLists.objects(String.valueOf(invoice.get("rawLineItems")));
        assertEquals(3, lines.size());
        assertEquals("time", lines.get(0).get("kind"));
        assertEquals("expense", lines.get(1).get("kind"));
        assertEquals("expense", lines.get(2).get("kind"));
        assertEquals(2, lines.stream().filter(line -> "expense".equals(line.get("kind"))).count());
    }

    @Test
    void openCaseFilterIncludesOnlyThisTenantsUnbilledExpenses() {
        Tenant smith = tenants.findBySlug("smith-associates").orElseThrow();
        Tenant ndlovu = tenants.findBySlug("ndlovu-partners").orElseThrow();
        LegalCase smithMatter = matter(smith.getId(), UUID.randomUUID(), "C-EXP-OPEN");
        Expense smithExpense = expense(smith.getId(), smithMatter.getId(), "Smith courier", "postage", new BigDecimal("15.00"), true, false);
        Expense ndlovuExpense = expense(ndlovu.getId(), null, "Ndlovu advocate fee", "expert fees", new BigDecimal("500.00"), true, false);

        TenantContext.setTenantId(smith.getId());
        Map<String, Object> invoice = finance.generateInvoice(smithMatter.getClientId(), null);
        String raw = String.valueOf(invoice.get("rawLineItems"));
        assertTrue(raw.contains("Smith courier"));
        assertTrue(raw.contains("\"kind\":\"expense\""));
        assertFalse(raw.contains("Ndlovu advocate fee"));
        assertTrue(expenses.findById(smithExpense.getId()).orElseThrow().isBilled());
        assertFalse(expenses.findById(ndlovuExpense.getId()).orElseThrow().isBilled());
        assertNull(expenses.findById(ndlovuExpense.getId()).orElseThrow().getInvoiceId());
    }

    private LegalCase matter(UUID tenantId, UUID clientId, String number) {
        LegalCase matter = new LegalCase();
        matter.setTenantId(tenantId);
        matter.setClientId(clientId);
        matter.setCaseNumber(number);
        matter.setTitle(number);
        return cases.save(matter);
    }

    private TimeEntry time(UUID tenantId, UUID caseId, String description, BigDecimal amount) {
        TimeEntry entry = new TimeEntry();
        entry.setTenantId(tenantId);
        entry.setCaseId(caseId);
        entry.setDescription(description);
        entry.setDurationMinutes(60);
        entry.setHourlyRate(amount);
        entry.setTotalAmount(amount);
        entry.setBillable(true);
        entry.setBilled(false);
        return timeEntries.save(entry);
    }

    private Expense expense(UUID tenantId, UUID caseId, String description, String category, BigDecimal amount, boolean billable, boolean billed) {
        Expense expense = new Expense();
        expense.setTenantId(tenantId);
        expense.setCaseId(caseId);
        expense.setDescription(description);
        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setBillable(billable);
        expense.setBilled(billed);
        return expenses.save(expense);
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer number(Object value) {
        return Integer.valueOf(String.valueOf(value));
    }
}
