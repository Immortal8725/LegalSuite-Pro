package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {
    private final TimeEntryRepository timeEntries;
    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final TrustAccountRepository trusts;
    private final TrustTransactionRepository trustTx;
    private final AppUserRepository users;
    private final LegalCaseRepository cases;
    private final Map<UUID, Instant> runningTimers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> timerCases = new ConcurrentHashMap<>();

    public FinanceService(
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            ExpenseRepository expenses,
            TrustAccountRepository trusts,
            TrustTransactionRepository trustTx,
            AppUserRepository users,
            LegalCaseRepository cases) {
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.expenses = expenses;
        this.trusts = trusts;
        this.trustTx = trustTx;
        this.users = users;
        this.cases = cases;
    }

    public List<Map<String, Object>> timeEntries() {
        return timeEntries.findByTenantIdOrderByDateDesc(tid()).stream().map(this::timeView).toList();
    }

    @Transactional
    public Map<String, Object> logTime(Map<String, Object> body) {
        AppUser user = users.findById(TenantContext.requireUser()).orElseThrow();
        TimeEntry t = new TimeEntry();
        t.setTenantId(tid());
        t.setUserId(user.getId());
        if (body.get("caseId") != null) t.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        t.setDescription(String.valueOf(body.getOrDefault("description", "Legal services")));
        t.setActivityType(String.valueOf(body.getOrDefault("activityType", "legal")));
        t.setDurationMinutes(Integer.parseInt(String.valueOf(body.getOrDefault("durationMinutes", "15"))));
        t.setHourlyRate(user.getHourlyRate() == null ? new BigDecimal("350") : user.getHourlyRate());
        t.setTotalAmount(t.getHourlyRate().multiply(BigDecimal.valueOf(t.getDurationMinutes()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        t.setBillable(body.get("billable") == null || Boolean.parseBoolean(String.valueOf(body.get("billable"))));
        t.setSource(String.valueOf(body.getOrDefault("source", "manual")));
        if (t.getCaseId() != null) {
            cases.findByIdAndTenantId(t.getCaseId(), tid()).ifPresent(c -> {
                if (t.isBillable() && !DocumentHash.appearanceAuthorized(c.getEngagementStatus(), c.isAppearanceAuthorized())) {
                    throw ApiException.badRequest("Limited file: billable time is blocked until the engagement is signed.");
                }
            });
        }
        timeEntries.save(t);
        return timeView(t);
    }

    public Map<String, Object> startTimer(UUID caseId) {
        UUID userId = TenantContext.requireUser();
        runningTimers.put(userId, Instant.now());
        if (caseId != null) timerCases.put(userId, caseId);
        else timerCases.remove(userId);
        Map<String, Object> m = new HashMap<>();
        m.put("running", true);
        m.put("startedAt", runningTimers.get(userId));
        m.put("caseId", caseId);
        return m;
    }

    public Map<String, Object> activeTimer() {
        UUID userId = TenantContext.requireUser();
        Instant started = runningTimers.get(userId);
        Map<String, Object> m = new HashMap<>();
        if (started == null) {
            m.put("running", false);
            return m;
        }
        m.put("running", true);
        m.put("startedAt", started);
        m.put("elapsedSeconds", Instant.now().getEpochSecond() - started.getEpochSecond());
        m.put("caseId", timerCases.get(userId));
        return m;
    }

    public List<Map<String, Object>> invoicesForClient(UUID clientId) {
        return invoices.findByTenantIdOrderByDateIssuedDesc(tid()).stream()
                .filter(i -> clientId.equals(i.getClientId()))
                .map(this::invoiceView)
                .toList();
    }

    @Transactional
    public Map<String, Object> stopTimer(String description) {
        UUID userId = TenantContext.requireUser();
        Instant started = runningTimers.remove(userId);
        UUID caseId = timerCases.remove(userId);
        if (started == null) throw ApiException.badRequest("No timer running");
        long seconds = Math.max(60, Instant.now().getEpochSecond() - started.getEpochSecond());
        int minutes = (int) Math.ceil(seconds / 60.0);
        minutes = ((minutes + 5) / 6) * 6; // 6-minute increment
        Map<String, Object> body = new HashMap<>();
        body.put("durationMinutes", minutes);
        body.put("description", description == null ? "Timed work" : description);
        body.put("source", "timer");
        if (caseId != null) body.put("caseId", caseId.toString());
        return logTime(body);
    }

    public List<Map<String, Object>> invoices() {
        return invoices.findByTenantIdOrderByDateIssuedDesc(tid()).stream().map(this::invoiceView).toList();
    }

    public Map<String, Object> getInvoice(UUID id) {
        return invoiceView(invoices.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Invoice not found")));
    }

    @Transactional
    public Map<String, Object> generateInvoice(UUID clientId, UUID caseId) {
        List<TimeEntry> unbilled = timeEntries.findByTenantIdAndBilledFalseAndBillableTrue(tid()).stream()
                .filter(t -> caseId == null || caseId.equals(t.getCaseId()))
                .toList();
        Invoice inv = new Invoice();
        inv.setTenantId(tid());
        inv.setClientId(clientId);
        inv.setCaseId(caseId);
        inv.setInvoiceNumber("INV-" + (1000 + invoices.countByTenantId(tid()) + 1));
        inv.setStatus("draft");
        inv.setDateDue(LocalDate.now().plusDays(30));
        BigDecimal sub = unbilled.stream().map(TimeEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> lines = unbilled.stream().map(t -> Map.<String, Object>of(
                "description", t.getDescription(),
                "minutes", t.getDurationMinutes(),
                "amount", t.getTotalAmount()
        )).toList();
        inv.setLineItemsJson(JsonLists.toJson(lines));
        inv.setSubtotal(sub);
        inv.setTotal(sub);
        invoices.save(inv);
        unbilled.forEach(t -> {
            t.setBilled(true);
            t.setInvoiceId(inv.getId());
            timeEntries.save(t);
        });
        return invoiceView(inv);
    }

    @Transactional
    public Map<String, Object> updateInvoice(UUID id, Map<String, Object> body) {
        Invoice inv = invoices.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (body.get("status") != null) inv.setStatus(String.valueOf(body.get("status")));
        if ("paid".equals(inv.getStatus())) {
            inv.setAmountPaid(inv.getTotal());
        }
        invoices.save(inv);
        return invoiceView(inv);
    }

    public List<Map<String, Object>> expenses() {
        return expenses.findByTenantIdOrderByDateDesc(tid()).stream().map(this::expenseView).toList();
    }

    @Transactional
    public Map<String, Object> addExpense(Map<String, Object> body) {
        Expense e = new Expense();
        e.setTenantId(tid());
        e.setUserId(TenantContext.requireUser());
        if (body.get("caseId") != null) e.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        e.setCategory(String.valueOf(body.getOrDefault("category", "other")));
        e.setDescription(String.valueOf(body.getOrDefault("description", "Expense")));
        e.setAmount(new BigDecimal(String.valueOf(body.getOrDefault("amount", "0"))));
        e.setVendor(body.get("vendor") == null ? null : String.valueOf(body.get("vendor")));
        expenses.save(e);
        return expenseView(e);
    }

    public List<Map<String, Object>> trustAccounts() {
        return trusts.findByTenantId(tid()).stream().map(this::trustView).toList();
    }

    @Transactional
    public Map<String, Object> trustMove(Map<String, Object> body) {
        return trustMove(body, false);
    }

    @Transactional
    public Map<String, Object> trustMove(Map<String, Object> body, boolean internalUnlock) {
        UUID accountId = UUID.fromString(String.valueOf(body.get("accountId")));
        TrustAccount acct = trusts.findByIdAndTenantId(accountId, tid())
                .orElseThrow(() -> ApiException.notFound("Trust account not found"));
        String type = String.valueOf(body.getOrDefault("type", "deposit"));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        if (body.get("caseId") != null && !internalUnlock) {
            UUID caseId = UUID.fromString(String.valueOf(body.get("caseId")));
            LegalCase matter = cases.findByIdAndTenantId(caseId, tid()).orElse(null);
            if (matter != null && !DocumentHash.appearanceAuthorized(matter.getEngagementStatus(), matter.isAppearanceAuthorized())) {
                throw ApiException.badRequest("Limited file: IOLTA movements wait until the engagement is signed. The retainer is pledged, not posted.");
            }
        }
        if ("withdrawal".equals(type) && acct.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("Insufficient trust funds for this client ledger");
        }
        BigDecimal next = "withdrawal".equals(type) ? acct.getBalance().subtract(amount) : acct.getBalance().add(amount);
        acct.setBalance(next);
        trusts.save(acct);
        TrustTransaction tx = new TrustTransaction();
        tx.setTenantId(tid());
        tx.setTrustAccountId(acct.getId());
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(next);
        tx.setDescription(String.valueOf(body.getOrDefault("description", type)));
        tx.setCreatedBy(TenantContext.getUserId());
        if (body.get("clientId") != null) tx.setClientId(UUID.fromString(String.valueOf(body.get("clientId"))));
        if (body.get("caseId") != null) tx.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        trustTx.save(tx);
        Map<String, Object> view = trustView(acct);
        view.put("transaction", Map.of(
                "id", tx.getId(),
                "type", tx.getType(),
                "amount", tx.getAmount(),
                "balanceAfter", tx.getBalanceAfter(),
                "description", tx.getDescription(),
                "createdAt", tx.getCreatedAt()));
        return view;
    }

    public List<Map<String, Object>> trustLedger(UUID accountId) {
        return trustTx.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(tid(), accountId).stream()
                .map(tx -> Map.<String, Object>of(
                        "id", tx.getId(),
                        "type", tx.getType(),
                        "amount", tx.getAmount(),
                        "balanceAfter", tx.getBalanceAfter(),
                        "description", tx.getDescription(),
                        "createdAt", tx.getCreatedAt()))
                .toList();
    }

    private Map<String, Object> timeView(TimeEntry t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("caseId", t.getCaseId());
        m.put("userId", t.getUserId());
        m.put("date", t.getDate());
        m.put("durationMinutes", t.getDurationMinutes());
        m.put("hourlyRate", t.getHourlyRate());
        m.put("totalAmount", t.getTotalAmount());
        m.put("description", t.getDescription());
        m.put("billable", t.isBillable());
        m.put("billed", t.isBilled());
        m.put("source", t.getSource());
        return m;
    }

    private Map<String, Object> invoiceView(Invoice i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("invoiceNumber", i.getInvoiceNumber());
        m.put("clientId", i.getClientId());
        m.put("caseId", i.getCaseId());
        m.put("status", i.getStatus());
        m.put("dateIssued", i.getDateIssued());
        m.put("dateDue", i.getDateDue());
        m.put("subtotal", i.getSubtotal());
        m.put("total", i.getTotal());
        m.put("amountPaid", i.getAmountPaid());
        m.put("balanceDue", i.getTotal().subtract(i.getAmountPaid() == null ? BigDecimal.ZERO : i.getAmountPaid()));
        m.put("lineItems", JsonLists.strings(i.getLineItemsJson()).isEmpty()
                ? JsonLists.map("{\"items\":" + (i.getLineItemsJson() == null ? "[]" : i.getLineItemsJson()) + "}")
                : List.of());
        m.put("rawLineItems", i.getLineItemsJson());
        m.put("notes", i.getNotes());
        return m;
    }

    private Map<String, Object> expenseView(Expense e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("caseId", e.getCaseId());
        m.put("category", e.getCategory());
        m.put("description", e.getDescription());
        m.put("amount", e.getAmount());
        m.put("date", e.getDate());
        m.put("vendor", e.getVendor());
        m.put("billable", e.isBillable());
        m.put("status", e.getStatus());
        return m;
    }

    private Map<String, Object> trustView(TrustAccount a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("accountName", a.getAccountName());
        m.put("bankName", a.getBankName());
        m.put("balance", a.getBalance());
        m.put("status", a.getStatus());
        return m;
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }
}
