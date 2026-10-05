package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.Expense;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.InvoicePayment;
import com.legalsuite.domain.InvoiceWriteOff;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.PaymentProof;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.domain.TrustReconciliation;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.ExpenseRepository;
import com.legalsuite.repo.InvoicePaymentRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.InvoiceWriteOffRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.PaymentProofRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.repo.TrustReconciliationRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final InvoicePaymentRepository payments;
    private final InvoiceWriteOffRepository writeOffs;
    private final PaymentProofRepository proofs;
    private final DocumentFileRepository documents;
    private final Path uploadRoot;
    private final Map<UUID, Instant> runningTimers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> timerCases = new ConcurrentHashMap<>();
    private static final Set<String> CLIENT_METHODS = Set.of("cash", "eft", "card", "other");
    private static final Set<String> PROOF_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> PROOF_EXT = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long PROOF_MAX_BYTES = 10L * 1024 * 1024;

    public FinanceService(
            TimeEntryRepository timeEntries,
            InvoiceRepository invoices,
            ExpenseRepository expenses,
            TrustAccountRepository trusts,
            TrustTransactionRepository trustTx,
            TrustReconciliationRepository recons,
            AppUserRepository users,
            LegalCaseRepository cases,
            TenantRepository tenants,
            InvoicePaymentRepository payments,
            InvoiceWriteOffRepository writeOffs,
            PaymentProofRepository proofs,
            DocumentFileRepository documents,
            @Value("${legalsuite.upload-dir:./uploads}") String uploadDir) throws IOException {
        this.timeEntries = timeEntries;
        this.invoices = invoices;
        this.expenses = expenses;
        this.trusts = trusts;
        this.trustTx = trustTx;
        this.recons = recons;
        this.users = users;
        this.cases = cases;
        this.tenants = tenants;
        this.payments = payments;
        this.writeOffs = writeOffs;
        this.proofs = proofs;
        this.documents = documents;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
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
        List<Expense> unbilledExpenses = expenses.findByTenantIdAndBilledFalseAndBillableTrue(tid()).stream()
                .filter(e -> caseId == null || caseId.equals(e.getCaseId()))
                .toList();
        Invoice inv = new Invoice();
        inv.setTenantId(tid());
        inv.setClientId(clientId);
        inv.setCaseId(caseId);
        inv.setInvoiceNumber("INV-" + (1000 + invoices.countByTenantId(tid()) + 1));
        inv.setStatus("draft");
        inv.setDateDue(LocalDate.now().plusDays(30));
        BigDecimal sub = unbilled.stream().map(TimeEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(unbilledExpenses.stream().map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (TimeEntry t : unbilled) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("kind", "time");
            if (t.getDate() != null) line.put("date", t.getDate().toString());
            line.put("description", t.getDescription());
            line.put("minutes", t.getDurationMinutes());
            line.put("amount", t.getTotalAmount());
            lines.add(line);
        }
        for (Expense e : unbilledExpenses) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("kind", "expense");
            if (e.getDate() != null) line.put("date", e.getDate().toString());
            line.put("description", e.getDescription());
            if (e.getCategory() != null) line.put("category", e.getCategory());
            if (e.getVendor() != null && !e.getVendor().isBlank()) line.put("vendor", e.getVendor());
            line.put("amount", nz(e.getAmount()));
            lines.add(line);
        }
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
        unbilledExpenses.forEach(e -> {
            e.setBilled(true);
            e.setInvoiceId(inv.getId());
            expenses.save(e);
        });
        return invoiceView(inv);
    }

    @Transactional
    public Map<String, Object> updateInvoice(UUID id, Map<String, Object> body) {
        Invoice inv = loadInvoice(id);
        if (body.get("status") != null) {
            String status = String.valueOf(body.get("status"));
            if ("paid".equals(status)) {
                return payInFull(id);
            }
            boolean moneyApplied = nz(inv.getAmountPaid()).signum() > 0 || nz(inv.getWriteOffAmount()).signum() > 0;
            if ("sent".equals(status) && (moneyApplied || balanceDue(inv).signum() <= 0)) {
                refreshStatus(inv);
            } else {
                inv.setStatus(status);
            }
        }
        invoices.save(inv);
        return invoiceView(inv);
    }

    @Transactional
    public Map<String, Object> payInFull(UUID id) {
        requireStaff();
        Invoice inv = loadInvoice(id);
        BigDecimal due = balanceDue(inv);
        if (due.signum() <= 0) {
            throw ApiException.badRequest("Invoice has no balance due.");
        }
        return applyPayment(id, Map.of(
                "amount", due.toPlainString(),
                "method", "other",
                "note", "Balance recorded as paid"), false, null);
    }

    @Transactional
    public Map<String, Object> recordPayment(UUID id, Map<String, Object> body) {
        requireStaff();
        if (body == null || body.get("amount") == null || String.valueOf(body.get("amount")).isBlank()) {
            throw ApiException.badRequest("amount is required");
        }
        return applyPayment(id, body, false, null);
    }

    @Transactional
    public Map<String, Object> writeOff(UUID id, Map<String, Object> body) {
        requireStaff();
        if (body == null) body = Map.of();
        Invoice inv = loadInvoice(id);
        if ("void".equals(inv.getStatus())) {
            throw ApiException.badRequest("A void invoice cannot be written off.");
        }
        BigDecimal due = balanceDue(inv);
        if (due.signum() <= 0) {
            throw ApiException.badRequest("Invoice has no balance due.");
        }
        BigDecimal amount = body.get("amount") == null || String.valueOf(body.get("amount")).isBlank()
                ? due
                : moneyAmount(body.get("amount"), "amount");
        if (amount.compareTo(due) > 0) {
            throw ApiException.badRequest("Write-off of " + amount.toPlainString()
                    + " exceeds the balance due of " + due.toPlainString() + ".");
        }
        String reason = text(body.get("reason") != null ? body.get("reason") : body.get("note"));
        if (reason.length() < 3) {
            throw ApiException.badRequest("A write-off reason is required.");
        }
        InvoiceWriteOff row = new InvoiceWriteOff();
        row.setTenantId(tid());
        row.setInvoiceId(inv.getId());
        row.setAmount(amount);
        row.setReason(reason);
        row.setCreatedBy(TenantContext.getUserId());
        writeOffs.save(row);
        inv.setWriteOffAmount(nz(inv.getWriteOffAmount()).add(amount).setScale(2, RoundingMode.HALF_UP));
        inv.setWriteOffNote(reason);
        inv.setWriteOffAt(row.getCreatedAt() == null ? Instant.now() : row.getCreatedAt());
        inv.setWriteOffBy(row.getCreatedBy());
        refreshStatus(inv);
        invoices.save(inv);
        return invoiceView(inv);
    }

    @Transactional
    public Map<String, Object> applyTrustToInvoice(UUID id, Map<String, Object> body) {
        requireStaff();
        if (body == null || body.get("accountId") == null || String.valueOf(body.get("accountId")).isBlank()) {
            throw ApiException.badRequest("Trust account is required.");
        }
        try {
            UUID.fromString(String.valueOf(body.get("accountId")));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Trust account is required.");
        }
        Invoice inv = loadInvoice(id);
        if (inv.getClientId() == null) {
            throw ApiException.badRequest("Invoice has no client, so trust cannot be applied.");
        }
        if ("void".equals(inv.getStatus())) {
            throw ApiException.badRequest("A void invoice cannot take a trust transfer.");
        }
        BigDecimal due = balanceDue(inv);
        if (due.signum() <= 0) {
            throw ApiException.badRequest("Invoice has no balance due.");
        }
        BigDecimal amount = moneyAmount(body.get("amount"), "amount");
        if (amount.compareTo(due) > 0) {
            throw ApiException.badRequest("Payment of " + amount.toPlainString()
                    + " exceeds the balance due of " + due.toPlainString() + ".");
        }
        Map<String, Object> move = new HashMap<>();
        move.put("accountId", String.valueOf(body.get("accountId")));
        move.put("type", "withdrawal");
        move.put("amount", amount.toPlainString());
        move.put("clientId", inv.getClientId().toString());
        if (inv.getCaseId() != null) move.put("caseId", inv.getCaseId().toString());
        String number = inv.getInvoiceNumber() == null ? inv.getId().toString() : inv.getInvoiceNumber();
        move.put("description", "Trust applied to fee invoice " + number);
        Map<String, Object> trust = trustMove(move, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> tx = (Map<String, Object>) trust.get("transaction");
        Map<String, Object> pay = new HashMap<>();
        pay.put("amount", amount.toPlainString());
        pay.put("method", "trust");
        pay.put("note", body.get("note") == null ? "Trust applied to fees" : body.get("note"));
        pay.put("reference", tx.get("id"));
        pay.put("trustTransactionId", tx.get("id"));
        Map<String, Object> view = applyPayment(id, pay, true, null);
        view.put("trustTransactionId", tx.get("id"));
        return view;
    }

    @Transactional
    public Map<String, Object> submitProof(UUID invoiceId, MultipartFile file, String amountRaw, String reference, String note) {
        Invoice inv = loadInvoice(invoiceId);
        assertCanSubmitProof(inv);
        if ("void".equals(inv.getStatus())) {
            throw ApiException.badRequest("A void invoice cannot take proof of payment.");
        }
        checkProofFile(file);
        BigDecimal due = balanceDue(inv);
        if (due.signum() <= 0) {
            throw ApiException.badRequest("Invoice has no balance due.");
        }
        BigDecimal amount = amountRaw == null || amountRaw.isBlank() ? due : moneyAmount(amountRaw, "amount");
        if (amount.compareTo(due) > 0) {
            throw ApiException.badRequest("Amount claimed exceeds the balance due of " + due.toPlainString() + ".");
        }
        DocumentFile doc;
        try {
            doc = storeProof(inv, file);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not store the proof of payment.");
        }
        PaymentProof proof = new PaymentProof();
        proof.setTenantId(tid());
        proof.setInvoiceId(inv.getId());
        proof.setDocumentId(doc.getId());
        proof.setAmountClaimed(amount);
        proof.setReference(blankToNull(reference));
        proof.setNote(blankToNull(note));
        proof.setStatus("pending_review");
        proof.setSubmittedBy(TenantContext.getUserId());
        proofs.save(proof);
        return proofView(proof, inv);
    }

    public List<Map<String, Object>> listProofs(String status) {
        requireStaff();
        List<PaymentProof> rows = status == null || status.isBlank()
                ? proofs.findByTenantIdOrderBySubmittedAtDesc(tid())
                : proofs.findByTenantIdAndStatusOrderBySubmittedAtAsc(tid(), status);
        return rows.stream()
                .map(p -> proofView(p, invoices.findByIdAndTenantId(p.getInvoiceId(), tid()).orElse(null)))
                .toList();
    }

    @Transactional
    public Map<String, Object> acceptProof(UUID proofId, Map<String, Object> body) {
        requireStaff();
        if (body == null) body = Map.of();
        PaymentProof proof = loadProof(proofId);
        if ("accepted".equals(proof.getStatus()) && proof.getPaymentId() != null) {
            return getInvoice(proof.getInvoiceId());
        }
        if (!"pending_review".equals(proof.getStatus())) {
            throw ApiException.badRequest("Only a proof waiting for review can be accepted.");
        }
        Invoice inv = loadInvoice(proof.getInvoiceId());
        BigDecimal due = balanceDue(inv);
        BigDecimal amount = body.get("amount") == null || String.valueOf(body.get("amount")).isBlank()
                ? nz(proof.getAmountClaimed()).setScale(2, RoundingMode.HALF_UP)
                : moneyAmount(body.get("amount"), "amount");
        if (due.signum() <= 0 || amount.compareTo(due) > 0) {
            throw ApiException.badRequest("Payment of " + amount.toPlainString()
                    + " exceeds the balance due of " + due.toPlainString() + ".");
        }
        String method = String.valueOf(body.getOrDefault("method", "eft")).trim().toLowerCase(Locale.ROOT);
        if (!CLIENT_METHODS.contains(method)) {
            throw ApiException.badRequest("Method must be cash, eft, card, or other.");
        }
        Map<String, Object> pay = new HashMap<>();
        pay.put("amount", amount.toPlainString());
        pay.put("method", method);
        pay.put("note", body.get("note") == null ? "Proof of payment accepted" : body.get("note"));
        if (proof.getReference() != null) pay.put("reference", proof.getReference());
        applyPayment(inv.getId(), pay, false, proof.getId());
        proof.setStatus("accepted");
        proof.setReviewedBy(TenantContext.getUserId());
        proof.setReviewedAt(Instant.now());
        if (body.get("note") != null && !String.valueOf(body.get("note")).isBlank()) {
            proof.setReviewNote(String.valueOf(body.get("note")).trim());
        }
        payments.findByTenantIdAndProofId(tid(), proof.getId()).ifPresent(p -> proof.setPaymentId(p.getId()));
        proofs.save(proof);
        return invoiceView(loadInvoice(inv.getId()));
    }

    @Transactional
    public Map<String, Object> rejectProof(UUID proofId, Map<String, Object> body) {
        requireStaff();
        PaymentProof proof = loadProof(proofId);
        if (!"pending_review".equals(proof.getStatus())) {
            throw ApiException.badRequest("Only a proof waiting for review can be rejected.");
        }
        String note = body == null ? "" : text(body.get("note"));
        if (note.length() < 3) {
            throw ApiException.badRequest("A rejection note is required.");
        }
        proof.setStatus("rejected");
        proof.setReviewNote(note);
        proof.setReviewedBy(TenantContext.getUserId());
        proof.setReviewedAt(Instant.now());
        proofs.save(proof);
        return proofView(proof, loadInvoice(proof.getInvoiceId()));
    }

    public DocumentFile proofDocument(UUID id) {
        PaymentProof proof = loadProofForRead(id);
        return documents.findByIdAndTenantId(proof.getDocumentId(), tid())
                .orElseThrow(() -> ApiException.notFound("Proof file not found"));
    }

    public Resource openProof(UUID id) {
        DocumentFile doc = proofDocument(id);
        if (doc.getStoragePath() == null || doc.getStoragePath().startsWith("seed://")) {
            throw ApiException.notFound("Proof file not found");
        }
        return new FileSystemResource(doc.getStoragePath());
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
        Compliance.requireFfcForTrust(tenants.findById(tid()).orElse(null));
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

    @Transactional
    public Map<String, Object> importBankCsv(Map<String, Object> body) {
        UUID accountId = UUID.fromString(String.valueOf(body.get("accountId")));
        TrustAccount acct = trusts.findByIdAndTenantId(accountId, tid())
                .orElseThrow(() -> ApiException.notFound("Trust account not found"));
        String csv = body.get("csv") == null ? "" : String.valueOf(body.get("csv"));
        BankCsv.Result parsed;
        try {
            parsed = BankCsv.parse(csv);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        acct.setBankBalance(parsed.closingBalance());
        trusts.save(acct);
        Tenant tenant = tenants.findById(tid()).orElseThrow();
        tenant.setBankFeedImportedAt(Instant.now());
        tenant.setLastBankFeedSource(parsed.source());
        tenants.save(tenant);
        Map<String, Object> recon = reconcile(Map.of(
                "accountId", accountId.toString(),
                "bankBalance", parsed.closingBalance().toPlainString(),
                "certify", "false",
                "notes", "Imported from bank CSV (" + parsed.rows().size() + " rows). Closing " + parsed.closingBalance().toPlainString() + "."));
        Map<String, Object> out = new HashMap<>();
        out.put("closingBalance", parsed.closingBalance());
        out.put("rows", parsed.rows().size());
        out.put("source", parsed.source());
        out.put("recon", recon);
        return out;
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
        m.put("amountPaid", nz(i.getAmountPaid()));
        m.put("writeOffAmount", nz(i.getWriteOffAmount()));
        m.put("writeOffNote", i.getWriteOffNote());
        m.put("writeOffAt", i.getWriteOffAt());
        m.put("writeOffBy", i.getWriteOffBy());
        m.put("balanceDue", balanceDue(i));
        m.put("lineItems", JsonLists.strings(i.getLineItemsJson()).isEmpty()
                ? JsonLists.map("{\"items\":" + (i.getLineItemsJson() == null ? "[]" : i.getLineItemsJson()) + "}")
                : List.of());
        m.put("rawLineItems", i.getLineItemsJson());
        m.put("notes", i.getNotes());
        m.put("payments", payments.findByTenantIdAndInvoiceIdOrderByPaidAtAscCreatedAtAsc(tid(), i.getId()).stream()
                .map(this::paymentView).toList());
        m.put("writeOffs", writeOffs.findByTenantIdAndInvoiceIdOrderByCreatedAtAsc(tid(), i.getId()).stream()
                .map(this::writeOffView).toList());
        m.put("proofs", proofs.findByTenantIdAndInvoiceIdOrderBySubmittedAtDesc(tid(), i.getId()).stream()
                .map(p -> proofView(p, i)).toList());
        if (i.getClientId() != null && !"client".equalsIgnoreCase(TenantContext.getRole())) {
            List<Map<String, Object>> accounts = new ArrayList<>();
            BigDecimal available = BigDecimal.ZERO;
            for (TrustAccount acct : trusts.findByTenantId(tid())) {
                BigDecimal ledger = clientLedger(acct.getId(), i.getClientId());
                Map<String, Object> row = new HashMap<>();
                row.put("accountId", acct.getId());
                row.put("accountName", acct.getAccountName());
                row.put("clientLedger", ledger);
                accounts.add(row);
                available = available.add(ledger);
            }
            m.put("trustAccounts", accounts);
            m.put("clientTrustAvailable", available);
        }
        return m;
    }

    private Map<String, Object> applyPayment(UUID id, Map<String, Object> body, boolean fromTrust, UUID proofId) {
        Invoice inv = loadInvoice(id);
        if ("void".equals(inv.getStatus())) {
            throw ApiException.badRequest("A void invoice cannot take a payment.");
        }
        BigDecimal amount = moneyAmount(body.get("amount"), "amount");
        BigDecimal due = balanceDue(inv);
        if (due.signum() <= 0) {
            throw ApiException.badRequest("Invoice has no balance due.");
        }
        if (amount.compareTo(due) > 0) {
            throw ApiException.badRequest("Payment of " + amount.toPlainString()
                    + " exceeds the balance due of " + due.toPlainString() + ".");
        }
        String method = String.valueOf(body.getOrDefault("method", "")).trim().toLowerCase(Locale.ROOT);
        if (fromTrust) {
            method = "trust";
        } else if (!CLIENT_METHODS.contains(method)) {
            throw ApiException.badRequest("Method must be cash, eft, card, or other.");
        }
        InvoicePayment payment = new InvoicePayment();
        payment.setTenantId(tid());
        payment.setInvoiceId(inv.getId());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setPaidAt(parsePaidAt(body.get("paidAt") != null ? body.get("paidAt") : body.get("date")));
        payment.setNote(blankToNull(body.get("note")));
        payment.setReference(blankToNull(body.get("reference")));
        payment.setRecordedBy(TenantContext.getUserId());
        if (body.get("trustTransactionId") != null && !String.valueOf(body.get("trustTransactionId")).isBlank()) {
            payment.setTrustTransactionId(UUID.fromString(String.valueOf(body.get("trustTransactionId"))));
        }
        payment.setProofId(proofId);
        payments.save(payment);
        inv.setAmountPaid(nz(inv.getAmountPaid()).add(amount).setScale(2, RoundingMode.HALF_UP));
        refreshStatus(inv);
        invoices.save(inv);
        return invoiceView(inv);
    }

    private void refreshStatus(Invoice inv) {
        BigDecimal due = balanceDue(inv);
        BigDecimal paid = nz(inv.getAmountPaid());
        BigDecimal written = nz(inv.getWriteOffAmount());
        if (due.signum() <= 0) {
            inv.setStatus(written.signum() > 0 ? "write_off" : "paid");
            return;
        }
        if (paid.signum() > 0 || written.signum() > 0) {
            inv.setStatus("partial");
            return;
        }
        if ("draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) {
            return;
        }
        if (inv.getDateDue() != null && inv.getDateDue().isBefore(LocalDate.now())) {
            inv.setStatus("overdue");
            return;
        }
        inv.setStatus("sent");
    }

    private BigDecimal balanceDue(Invoice inv) {
        BigDecimal due = nz(inv.getTotal()).subtract(nz(inv.getAmountPaid())).subtract(nz(inv.getWriteOffAmount()));
        if (due.signum() < 0) due = BigDecimal.ZERO;
        return due.setScale(2, RoundingMode.HALF_UP);
    }

    private Invoice loadInvoice(UUID id) {
        return invoices.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    private PaymentProof loadProof(UUID id) {
        return proofs.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Proof of payment not found"));
    }

    private PaymentProof loadProofForRead(UUID id) {
        PaymentProof proof = loadProof(id);
        if ("client".equalsIgnoreCase(TenantContext.getRole())) {
            Invoice inv = loadInvoice(proof.getInvoiceId());
            if (inv.getClientId() == null || !inv.getClientId().equals(TenantContext.requireUser())) {
                throw ApiException.forbidden("You can only open proof on your own invoice.");
            }
        }
        return proof;
    }

    private void assertCanSubmitProof(Invoice inv) {
        if ("client".equalsIgnoreCase(TenantContext.getRole())) {
            if (inv.getClientId() == null || !inv.getClientId().equals(TenantContext.requireUser())) {
                throw ApiException.forbidden("You can only submit proof on your own invoice.");
            }
        }
    }

    private void requireStaff() {
        if ("client".equalsIgnoreCase(TenantContext.getRole())) {
            throw ApiException.forbidden("Client portal users cannot change fee invoices.");
        }
    }

    private DocumentFile storeProof(Invoice inv, MultipartFile file) throws IOException {
        Path dir = uploadRoot.resolve(tid().toString());
        Files.createDirectories(dir);
        String stored = UUID.randomUUID() + "-" + safeName(file.getOriginalFilename());
        Path dest = dir.resolve(stored).normalize();
        if (!dest.startsWith(uploadRoot)) {
            throw ApiException.badRequest("Invalid file name.");
        }
        file.transferTo(dest.toFile());
        DocumentFile doc = new DocumentFile();
        doc.setTenantId(tid());
        doc.setCaseId(inv.getCaseId());
        doc.setClientId(inv.getClientId());
        doc.setUploadedBy(TenantContext.getUserId());
        doc.setName(safeName(file.getOriginalFilename()));
        doc.setOriginalName(file.getOriginalFilename());
        doc.setCategory("proof_of_payment");
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        doc.setMimeType(mime);
        doc.setSizeBytes(file.getSize());
        doc.setStoragePath(dest.toString());
        return documents.save(doc);
    }

    private void checkProofFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a PDF, JPEG, or PNG to upload.");
        }
        if (file.getSize() > PROOF_MAX_BYTES) {
            throw ApiException.badRequest("Proof of payment must be 10 MB or smaller.");
        }
        String ext = extension(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        int semi = mime.indexOf(';');
        if (semi >= 0) mime = mime.substring(0, semi).trim();
        boolean typeOk = PROOF_TYPES.contains(mime);
        boolean extOk = PROOF_EXT.contains(ext);
        if (!extOk || (!typeOk && !mime.isBlank() && !"application/octet-stream".equals(mime))) {
            throw ApiException.badRequest("Proof of payment must be a PDF, JPEG, or PNG.");
        }
    }

    private static String extension(String name) {
        String safe = safeName(name);
        int dot = safe.lastIndexOf('.');
        if (dot < 0 || dot == safe.length() - 1) return "";
        return safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeName(String original) {
        String name = original == null ? "proof" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) name = "proof";
        if (name.length() > 120) name = name.substring(name.length() - 120);
        return name;
    }

    private BigDecimal moneyAmount(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw ApiException.badRequest(field + " is required");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(field + " must be a number");
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw ApiException.badRequest(field + " must be greater than zero");
        }
        return value;
    }

    private LocalDate parsePaidAt(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return LocalDate.now();
        try {
            String text = String.valueOf(raw).trim();
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception e) {
            throw ApiException.badRequest("Payment date must be YYYY-MM-DD.");
        }
    }

    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static String blankToNull(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, Object> paymentView(InvoicePayment p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("invoiceId", p.getInvoiceId());
        m.put("amount", p.getAmount());
        m.put("method", p.getMethod());
        m.put("paidAt", p.getPaidAt());
        m.put("note", p.getNote());
        m.put("reference", p.getReference());
        m.put("recordedBy", p.getRecordedBy());
        m.put("trustTransactionId", p.getTrustTransactionId());
        m.put("proofId", p.getProofId());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private Map<String, Object> writeOffView(InvoiceWriteOff row) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", row.getId());
        m.put("invoiceId", row.getInvoiceId());
        m.put("amount", row.getAmount());
        m.put("reason", row.getReason());
        m.put("createdBy", row.getCreatedBy());
        m.put("createdAt", row.getCreatedAt());
        return m;
    }

    private Map<String, Object> proofView(PaymentProof proof, Invoice inv) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", proof.getId());
        m.put("invoiceId", proof.getInvoiceId());
        m.put("documentId", proof.getDocumentId());
        m.put("amountClaimed", proof.getAmountClaimed());
        m.put("reference", proof.getReference());
        m.put("note", proof.getNote());
        m.put("status", proof.getStatus());
        m.put("submittedBy", proof.getSubmittedBy());
        m.put("submittedAt", proof.getSubmittedAt());
        m.put("reviewedBy", proof.getReviewedBy());
        m.put("reviewedAt", proof.getReviewedAt());
        m.put("reviewNote", proof.getReviewNote());
        m.put("paymentId", proof.getPaymentId());
        if (inv != null) {
            m.put("invoiceNumber", inv.getInvoiceNumber());
            m.put("clientId", inv.getClientId());
            m.put("balanceDue", balanceDue(inv));
            m.put("invoiceStatus", inv.getStatus());
        }
        documents.findByIdAndTenantId(proof.getDocumentId(), tid()).ifPresent(doc -> {
            m.put("fileName", doc.getOriginalName() == null ? doc.getName() : doc.getOriginalName());
            m.put("mimeType", doc.getMimeType());
            m.put("sizeBytes", doc.getSizeBytes());
        });
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
        m.put("billed", e.isBilled());
        m.put("invoiceId", e.getInvoiceId());
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
