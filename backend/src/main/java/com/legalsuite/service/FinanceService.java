package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.domain.TrustReconciliation;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.repo.TrustReconciliationRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final TrustReconciliationRepository recons;
    private final AppUserRepository users;
    private final LegalCaseRepository cases;
    private final TenantRepository tenants;
    private final Map<UUID, Instant> runningTimers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> timerCases = new ConcurrentHashMap<>();

    public FinanceService(
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            ExpenseRepository expenses,
            TrustAccountRepository trusts,
            TrustTransactionRepository trustTx,
            TrustReconciliationRepository recons,
            AppUserRepository users,
            LegalCaseRepository cases,
            TenantRepository tenants) {
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.expenses = expenses;
        this.trusts = trusts;
        this.trustTx = trustTx;
        this.recons = recons;
        this.users = users;
        this.cases = cases;
        this.tenants = tenants;
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
        Tenant tenant = tenants.findById(tid()).orElse(null);
        if ("ZA".equals(DocketEngine.of(tenant))) {
            BigDecimal vat = sub.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            inv.setTaxAmount(vat);
            inv.setTotal(sub.add(vat));
            inv.setNotes("VAT 15% (Value-Added Tax Act 89 of 1991 s 7).");
        } else {
            inv.setTotal(sub);
        }
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
                throw ApiException.badRequest("Limited file: trust movements wait until the mandate is signed. The retainer is pledged, not posted.");
            }
        }
        UUID clientId = body.get("clientId") == null ? null : UUID.fromString(String.valueOf(body.get("clientId")));
        if ("withdrawal".equals(type) && clientId != null) {
            BigDecimal ledger = clientLedger(acct.getId(), clientId);
            if (ledger.compareTo(amount) < 0) {
                throw ApiException.badRequest("Cannot use another client's trust money. This client's section 86 / IOLTA ledger is "
                        + ledger.toPlainString() + ".");
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
        if (clientId != null) tx.setClientId(clientId);
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
                .map(tx -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", tx.getId());
                    m.put("type", tx.getType());
                    m.put("amount", tx.getAmount());
                    m.put("balanceAfter", tx.getBalanceAfter());
                    m.put("description", tx.getDescription());
                    m.put("clientId", tx.getClientId());
                    m.put("createdAt", tx.getCreatedAt());
                    return m;
                })
                .toList();
    }

    public Map<String, Object> liveRecon(UUID accountId) {
        TrustAccount acct = trusts.findByIdAndTenantId(accountId, tid())
                .orElseThrow(() -> ApiException.notFound("Trust account not found"));
        return liveRecon(acct, acct.getBankBalance());
    }

    public Map<String, Object> firmRecon() {
        List<TrustAccount> accounts = trusts.findByTenantId(tid());
        if (accounts.isEmpty()) {
            return Map.of("status", "empty", "accounts", List.of(), "worstStatus", "empty");
        }
        List<Map<String, Object>> rows = accounts.stream().map(a -> liveRecon(a, a.getBankBalance())).toList();
        String worst = rows.stream().anyMatch(r -> "unbalanced".equals(r.get("status"))) ? "unbalanced" : "balanced";
        Map<String, Object> m = new HashMap<>();
        m.put("accounts", rows);
        m.put("worstStatus", worst);
        m.put("history", recons.findByTenantIdOrderByCreatedAtDesc(tid()).stream().limit(8).map(this::reconView).toList());
        return m;
    }

    @Transactional
    public Map<String, Object> reconcile(Map<String, Object> body) {
        UUID accountId = UUID.fromString(String.valueOf(body.get("accountId")));
        TrustAccount acct = trusts.findByIdAndTenantId(accountId, tid())
                .orElseThrow(() -> ApiException.notFound("Trust account not found"));
        BigDecimal bank = new BigDecimal(String.valueOf(body.getOrDefault("bankBalance",
                acct.getBankBalance() == null ? acct.getBalance() : acct.getBankBalance())));
        boolean certify = Boolean.parseBoolean(String.valueOf(body.getOrDefault("certify", "false")));
        String notes = String.valueOf(body.getOrDefault("notes", ""));
        Map<String, Object> live = liveRecon(acct, bank);
        BigDecimal difference = new BigDecimal(String.valueOf(live.get("difference")));
        if (certify && difference.abs().compareTo(new BigDecimal("0.009")) > 0 && (notes == null || notes.trim().length() < 40)) {
            throw ApiException.badRequest("Cannot certify an unbalanced three-way recon without a written explanation (40+ characters). LPC inspectors read the note.");
        }
        acct.setBankBalance(bank);
        acct.setLastReconciledAt(Instant.now());
        trusts.save(acct);
        TrustReconciliation row = new TrustReconciliation();
        row.setTenantId(tid());
        row.setTrustAccountId(acct.getId());
        row.setPeriodEnd(TexasDocketRules.parseDate(body.get("periodEnd")) == null
                ? LocalDate.now() : TexasDocketRules.parseDate(body.get("periodEnd")));
        row.setBankBalance(bank);
        row.setBookBalance(new BigDecimal(String.valueOf(live.get("bookBalance"))));
        row.setClientLedgerTotal(new BigDecimal(String.valueOf(live.get("clientLedgerTotal"))));
        row.setDifference(difference);
        row.setStatus(String.valueOf(live.get("status")));
        row.setNotes(notes);
        row.setLedgersJson(JsonLists.toJson(live.get("ledgers")));
        if (certify) {
            row.setCertified(true);
            row.setCertifiedBy(TenantContext.getUserId());
            row.setCertifiedAt(Instant.now());
        }
        recons.save(row);
        Map<String, Object> view = reconView(row);
        view.put("live", liveRecon(acct, bank));
        return view;
    }

    BigDecimal clientLedger(UUID accountId, UUID clientId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (TrustTransaction tx : trustTx.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(tid(), accountId)) {
            if (clientId == null) {
                if (tx.getClientId() != null) continue;
            } else if (!clientId.equals(tx.getClientId())) {
                continue;
            }
            if ("withdrawal".equals(tx.getType())) sum = sum.subtract(nz(tx.getAmount()));
            else sum = sum.add(nz(tx.getAmount()));
        }
        return sum;
    }

    private Map<String, Object> liveRecon(TrustAccount acct, BigDecimal bankBalance) {
        Map<UUID, BigDecimal> byClient = new LinkedHashMap<>();
        BigDecimal unallocated = BigDecimal.ZERO;
        for (TrustTransaction tx : trustTx.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(tid(), acct.getId())) {
            BigDecimal delta = "withdrawal".equals(tx.getType()) ? nz(tx.getAmount()).negate() : nz(tx.getAmount());
            if (tx.getClientId() == null) unallocated = unallocated.add(delta);
            else byClient.merge(tx.getClientId(), delta, BigDecimal::add);
        }
        BigDecimal clientTotal = byClient.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).add(unallocated);
        BigDecimal book = nz(acct.getBalance());
        BigDecimal bank = bankBalance == null ? book : bankBalance;
        BigDecimal bankVsBook = bank.subtract(book);
        BigDecimal bookVsClients = book.subtract(clientTotal);
        boolean balanced = bankVsBook.abs().compareTo(new BigDecimal("0.009")) <= 0
                && bookVsClients.abs().compareTo(new BigDecimal("0.009")) <= 0;
        List<Map<String, Object>> ledgers = new ArrayList<>();
        byClient.forEach((id, bal) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("clientId", id);
            row.put("balance", bal);
            ledgers.add(row);
        });
        if (unallocated.signum() != 0) {
            ledgers.add(Map.of("clientId", "", "balance", unallocated, "unallocated", true));
        }
        Map<String, Object> m = new HashMap<>();
        m.put("accountId", acct.getId());
        m.put("accountName", acct.getAccountName());
        m.put("bankName", acct.getBankName());
        m.put("bookBalance", book);
        m.put("bankBalance", bank);
        m.put("clientLedgerTotal", clientTotal);
        m.put("bankVsBook", bankVsBook);
        m.put("bookVsClients", bookVsClients);
        m.put("difference", bankVsBook.abs().max(bookVsClients.abs()));
        m.put("status", balanced ? "balanced" : "unbalanced");
        m.put("ledgers", ledgers);
        m.put("lastReconciledAt", acct.getLastReconciledAt());
        m.put("rule", "Legal Practice Act 28 of 2014 ss 86–87: trust bank = cashbook = sum of client ledgers. Never mix clients.");
        return m;
    }

    private Map<String, Object> reconView(TrustReconciliation r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("trustAccountId", r.getTrustAccountId());
        m.put("periodEnd", r.getPeriodEnd());
        m.put("bankBalance", r.getBankBalance());
        m.put("bookBalance", r.getBookBalance());
        m.put("clientLedgerTotal", r.getClientLedgerTotal());
        m.put("difference", r.getDifference());
        m.put("status", r.getStatus());
        m.put("certified", r.isCertified());
        m.put("notes", r.getNotes());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
        m.put("taxAmount", i.getTaxAmount());
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
        m.put("bankBalance", a.getBankBalance());
        m.put("lastReconciledAt", a.getLastReconciledAt());
        m.put("accountType", a.getAccountType());
        m.put("status", a.getStatus());
        m.put("recon", liveRecon(a, a.getBankBalance()));
        return m;
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }
}
