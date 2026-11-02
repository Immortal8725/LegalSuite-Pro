package com.legalsuite.clientchange;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Ids;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.MatterMessage;
import com.legalsuite.domain.SignatureRequest;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.fica.FicaProfile;
import com.legalsuite.fica.FicaProfileRepository;
import com.legalsuite.fica.FicaService;
import com.legalsuite.milestones.MatterMilestone;
import com.legalsuite.milestones.MatterMilestoneRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.MatterMessageRepository;
import com.legalsuite.repo.SignatureRequestRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.CommsService;
import com.legalsuite.service.FinanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Change client on a matter, and merge duplicate client records.
 *
 * Rules: only a firm admin or the matter's lead attorney may change the client; only a firm admin may merge.
 * A reason is required. Messages, documents, fee milestones and unpaid invoices move in one transaction.
 * Paid invoices and trust entries are never edited: a paid invoice gets a credit note, and trust money moves
 * through a trust-to-trust transfer that a second person must approve.
 */
@Service
public class ClientChangeService {
    static final Set<String> CLOSED_INVOICE = Set.of("void", "credit_note");

    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final MatterMessageRepository messages;
    private final DocumentFileRepository documents;
    private final SignatureRequestRepository signatures;
    private final MatterMilestoneRepository milestones;
    private final InvoiceRepository invoices;
    private final TrustAccountRepository trustAccounts;
    private final FicaProfileRepository ficaProfiles;
    private final FicaService fica;
    private final CommsService comms;
    private final FinanceService finance;
    private final ClientChangeRepository changes;
    private final TrustClientTransferRepository transfers;
    private final AuditService audit;

    public ClientChangeService(LegalCaseRepository cases, ClientRepository clients, MatterMessageRepository messages,
            DocumentFileRepository documents, SignatureRequestRepository signatures, MatterMilestoneRepository milestones,
            InvoiceRepository invoices, TrustAccountRepository trustAccounts, FicaProfileRepository ficaProfiles,
            FicaService fica, CommsService comms, FinanceService finance, ClientChangeRepository changes,
            TrustClientTransferRepository transfers, AuditService audit) {
        this.cases = cases;
        this.clients = clients;
        this.messages = messages;
        this.documents = documents;
        this.signatures = signatures;
        this.milestones = milestones;
        this.invoices = invoices;
        this.trustAccounts = trustAccounts;
        this.ficaProfiles = ficaProfiles;
        this.fica = fica;
        this.comms = comms;
        this.finance = finance;
        this.changes = changes;
        this.transfers = transfers;
        this.audit = audit;
    }

    private UUID tid() { return TenantContext.requireTenant(); }

    // ---------- Change client on one matter ----------

    @Transactional
    public Map<String, Object> preview(UUID caseId, UUID toClientId) {
        LegalCase c = matter(caseId);
        requireChanger(c);
        Client from = client(c.getClientId());
        Client to = targetClient(c, toClientId);
        return plan(c, from, to);
    }

    @Transactional
    public Map<String, Object> change(UUID caseId, Map<String, Object> body) {
        LegalCase c = matter(caseId);
        requireChanger(c);
        UUID toId = Ids.requireUuid(body.get("toClientId"), "client", "Choose the right client.");
        String reason = reason(body);
        boolean showHistory = bool(body.get("showHistory"));
        Client from = client(c.getClientId());
        Client to = targetClient(c, toId);
        Map<String, Object> plan = plan(c, from, to);
        @SuppressWarnings("unchecked")
        Map<String, Object> conflict = (Map<String, Object>) plan.get("conflict");
        if (((Number) conflict.get("relevantMatches")).intValue() > 0 && !bool(body.get("conflictAcknowledged"))) {
            throw ApiException.badRequest("The conflict check found possible conflicts for the new client. Review them and confirm before you change the client.");
        }

        UUID tid = tid();
        UUID actor = TenantContext.getUserId();
        Map<String, Object> counts = new LinkedHashMap<>();

        int msgs = 0;
        int hidden = 0;
        for (MatterMessage m : messages.findByTenantIdAndCaseIdOrderByCreatedAtAsc(tid, c.getId())) {
            m.setClientId(to.getId());
            if (!showHistory && !m.isHiddenFromClient()) {
                m.setHiddenFromClient(true);
                hidden++;
            }
            messages.save(m);
            msgs++;
        }
        counts.put("messagesMoved", msgs);
        counts.put("messagesHiddenFromNewClient", hidden);

        int docs = 0;
        for (DocumentFile d : documents.findByTenantIdAndCaseId(tid, c.getId())) {
            if (d.getClientId() == null || from.getId().equals(d.getClientId())) {
                d.setClientId(to.getId());
                documents.save(d);
                docs++;
            }
        }
        counts.put("documentsMoved", docs);

        int voided = 0;
        for (SignatureRequest s : caseSignatures(c)) {
            if ("pending".equals(s.getStatus()) && from.getId().equals(s.getClientId())) {
                s.setStatus("void");
                signatures.save(s);
                voided++;
            }
        }
        counts.put("signatureRequestsCancelled", voided);

        int ms = 0;
        for (MatterMilestone m : milestones.findByTenantIdAndCaseIdOrderBySortOrderAsc(tid, c.getId())) {
            m.setClientId(to.getId());
            milestones.save(m);
            ms++;
        }
        counts.put("milestonesMoved", ms);

        Map<String, Object> inv = moveInvoices(c, from, to, reason, false);
        counts.putAll(inv);

        // Mandate: the old signature was the wrong person's. The matter waits for a new signed mandate.
        String oldEngagement = c.getEngagementStatus();
        c.setEngagementStatus("unsigned");
        c.setAppearanceAuthorized(false);
        c.setEngagementSignatureId(null);
        c.setConflictWaiverSignatureId(null);
        c.setConflictWaiverHash(null);
        c.setConflictChecked(true);
        c.setClientId(to.getId());
        c.setUpdatedAt(Instant.now());
        cases.save(c);
        counts.put("mandateReset", oldEngagement == null ? "unsigned" : oldEngagement);

        ClientChange change = record("change", c.getId(), from, to, reason, actor, showHistory, plan, counts);
        List<Map<String, Object>> trust = requestTrustTransfers(change, c.getId(), from, to, reason);
        counts.put("trustTransfersAwaitingApproval", trust.size());
        change.setCountsJson(json(counts));
        changes.save(change);

        boolean popia = Boolean.TRUE.equals(plan.get("possiblePopiaIncident"));
        audit.record("case.client_change", "case", c.getId().toString(),
                c.getCaseNumber() + " from " + from.displayName() + " (" + from.getId() + ") to " + to.displayName() + " (" + to.getId()
                        + "). Reason: " + reason + ". Actor " + actor + ". " + json(counts));
        if (popia) {
            audit.record("popia.possible_incident", "case", c.getId().toString(),
                    c.getCaseNumber() + ": " + plan.get("popiaDetail") + " Assess under POPIA s 22 whether the Regulator and the data subject must be told.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changeId", change.getId());
        out.put("caseId", c.getId());
        out.put("fromClient", Map.of("id", from.getId(), "name", from.displayName()));
        out.put("toClient", Map.of("id", to.getId(), "name", to.displayName()));
        out.put("counts", counts);
        out.put("trustTransfers", trust);
        out.put("fica", plan.get("fica"));
        out.put("possiblePopiaIncident", popia);
        out.put("popiaDetail", plan.get("popiaDetail"));
        out.put("nextSteps", nextSteps(plan, trust, inv));
        return out;
    }

    public List<Map<String, Object>> history(UUID caseId) {
        LegalCase c = matter(caseId);
        return changes.findByTenantIdAndCaseIdOrderByCreatedAtDesc(tid(), c.getId()).stream().map(this::changeView).toList();
    }

    private Map<String, Object> plan(LegalCase c, Client from, Client to) {
        UUID tid = tid();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", c.getId());
        p.put("caseNumber", c.getCaseNumber());
        p.put("fromClient", Map.of("id", from.getId(), "name", from.displayName()));
        p.put("toClient", Map.of("id", to.getId(), "name", to.displayName()));

        List<MatterMessage> msgs = messages.findByTenantIdAndCaseIdOrderByCreatedAtAsc(tid, c.getId());
        long readByWrong = msgs.stream().filter(m -> !"client".equals(m.getSenderType()) && m.getReadByClientAt() != null
                && from.getId().equals(m.getClientId())).count();
        long sentByWrong = msgs.stream().filter(m -> "client".equals(m.getSenderType()) && from.getId().equals(m.getClientId())).count();
        long docs = documents.findByTenantIdAndCaseId(tid, c.getId()).stream()
                .filter(d -> d.getClientId() == null || from.getId().equals(d.getClientId())).count();
        List<SignatureRequest> sigs = caseSignatures(c);
        long pendingSigs = sigs.stream().filter(s -> "pending".equals(s.getStatus()) && from.getId().equals(s.getClientId())).count();
        long signedByWrong = sigs.stream().filter(s -> "signed".equals(s.getStatus()) && from.getId().equals(s.getClientId())).count();
        long ms = milestones.findByTenantIdAndCaseIdOrderBySortOrderAsc(tid, c.getId()).size();
        long unpaid = 0;
        long paid = 0;
        for (Invoice i : caseInvoices(c, from.getId())) {
            if (isUntouched(i)) unpaid++;
            else paid++;
        }
        List<Map<String, Object>> trust = new ArrayList<>();
        for (TrustAccount a : trustAccounts.findByTenantId(tid)) {
            BigDecimal held = finance.clientMatterLedger(a.getId(), from.getId(), c.getId());
            if (held.signum() > 0) trust.add(Map.of("trustAccountId", a.getId(), "accountName", String.valueOf(a.getAccountName()), "amount", held));
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("messages", msgs.size());
        counts.put("messagesReadByWrongClient", readByWrong);
        counts.put("messagesSentByWrongClient", sentByWrong);
        counts.put("documents", docs);
        counts.put("signatureRequestsToCancel", pendingSigs);
        counts.put("signaturesSignedByWrongClient", signedByWrong);
        counts.put("milestones", ms);
        counts.put("unpaidInvoicesToMove", unpaid);
        counts.put("paidInvoicesNeedingCreditNote", paid);
        p.put("counts", counts);
        p.put("trust", trust);

        Map<String, Object> extra = new LinkedHashMap<>();
        if (c.getOpposingParty() != null) extra.put("opposingParty", c.getOpposingParty());
        if (to.getEmail() != null) extra.put("email", to.getEmail());
        Map<String, Object> raw = comms.conflictCheck(to.displayName(), extra);
        List<Map<String, Object>> relevant = new ArrayList<>();
        Object list = raw.get("matches");
        if (list instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String role = String.valueOf(m.get("role"));
                String name = String.valueOf(m.get("name")).trim().toLowerCase(Locale.ROOT);
                boolean self = "client".equals(role) && (name.equals(to.displayName().trim().toLowerCase(Locale.ROOT))
                        || name.equals(from.displayName().trim().toLowerCase(Locale.ROOT)));
                if (!self) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    relevant.add(mm);
                }
            }
        }
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("relevantMatches", relevant.size());
        conflict.put("matches", relevant);
        p.put("conflict", conflict);

        Map<String, Object> f = fica.get(to.getId());
        Map<String, Object> ficaOut = new LinkedHashMap<>();
        ficaOut.put("status", f.get("status"));
        ficaOut.put("complete", "verified".equals(f.get("status")));
        ficaOut.put("missing", f.getOrDefault("missing", List.of()));
        p.put("fica", ficaOut);
        p.put("mandate", Map.of("current", String.valueOf(c.getEngagementStatus()), "after", "unsigned"));

        List<String> seen = new ArrayList<>();
        if (readByWrong > 0) seen.add(readByWrong + " message(s) were opened by " + from.displayName() + " in the portal");
        if (sentByWrong > 0) seen.add(from.displayName() + " sent " + sentByWrong + " message(s) on this matter");
        if (signedByWrong > 0) seen.add(from.displayName() + " signed " + signedByWrong + " document(s) on this matter");
        boolean popia = !seen.isEmpty();
        p.put("possiblePopiaIncident", popia);
        p.put("popiaDetail", popia ? String.join("; ", seen) + "." : "No sign that the wrong client saw this matter in the portal.");
        return p;
    }

    private List<Map<String, Object>> nextSteps(Map<String, Object> plan, List<Map<String, Object>> trust, Map<String, Object> inv) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of("step", "mandate", "text", "Send the new client a mandate to sign. The matter is limited until it is signed."));
        @SuppressWarnings("unchecked")
        Map<String, Object> f = (Map<String, Object>) plan.get("fica");
        if (!Boolean.TRUE.equals(f.get("complete"))) {
            steps.add(Map.of("step", "fica", "text", "Complete FICA for the new client before you act or take money."));
        }
        if (!trust.isEmpty()) {
            steps.add(Map.of("step", "trust", "text", "A second person must approve the trust transfer between the two client ledgers."));
        }
        Object credits = inv.get("creditNotesIssued");
        if (credits instanceof Number n && n.intValue() > 0) {
            steps.add(Map.of("step", "invoices", "text", "Paid invoices were credited on the old client. Re-issue them to the new client and settle any refund with your bookkeeper."));
        }
        if (Boolean.TRUE.equals(plan.get("possiblePopiaIncident"))) {
            steps.add(Map.of("step", "popia", "text", "Possible POPIA incident: the wrong client may have seen this matter. Your information officer must assess it."));
        }
        return steps;
    }

    // ---------- Merge duplicate clients ----------

    @Transactional
    public Map<String, Object> mergePreview(UUID sourceId, UUID intoId) {
        Roles.requireTenantAdmin("Only a firm admin can merge clients.");
        Client source = client(sourceId);
        Client into = mergeTarget(source, intoId);
        UUID tid = tid();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("source", Map.of("id", source.getId(), "name", source.displayName(), "idNumber", String.valueOf(source.getIdNumber())));
        p.put("into", Map.of("id", into.getId(), "name", into.displayName(), "idNumber", String.valueOf(into.getIdNumber())));
        p.put("matters", cases.findByTenantIdAndClientId(tid, source.getId()).size());
        p.put("documents", documents.findByTenantIdAndClientId(tid, source.getId()).size());
        p.put("messages", messages.findByTenantIdAndClientId(tid, source.getId()).size());
        long unpaid = invoices.findByTenantIdAndClientId(tid, source.getId()).stream().filter(this::isUntouched).count();
        long paid = invoices.findByTenantIdAndClientId(tid, source.getId()).stream().filter(i -> !isUntouched(i) && !CLOSED_INVOICE.contains(i.getStatus())).count();
        p.put("unpaidInvoicesToMove", unpaid);
        p.put("paidInvoicesKeptOnOldRecord", paid);
        List<Map<String, Object>> trust = new ArrayList<>();
        for (TrustAccount a : trustAccounts.findByTenantId(tid)) {
            BigDecimal held = finance.clientMatterLedger(a.getId(), source.getId(), null);
            if (held.signum() > 0) trust.add(Map.of("trustAccountId", a.getId(), "accountName", String.valueOf(a.getAccountName()), "amount", held));
        }
        p.put("trust", trust);
        p.put("sameIdNumber", source.getIdNumber() != null && source.getIdNumber().equalsIgnoreCase(String.valueOf(into.getIdNumber())));
        return p;
    }

    @Transactional
    public Map<String, Object> merge(UUID sourceId, Map<String, Object> body) {
        Roles.requireTenantAdmin("Only a firm admin can merge clients.");
        UUID intoId = Ids.requireUuid(body.get("intoClientId"), "client", "Choose the client record to keep.");
        String reason = reason(body);
        Client source = client(sourceId);
        Client into = mergeTarget(source, intoId);
        UUID tid = tid();
        UUID actor = TenantContext.getUserId();
        Map<String, Object> counts = new LinkedHashMap<>();

        int matters = 0;
        for (LegalCase c : cases.findByTenantIdAndClientId(tid, source.getId())) {
            c.setClientId(into.getId());
            c.setUpdatedAt(Instant.now());
            cases.save(c);
            matters++;
        }
        counts.put("mattersMoved", matters);
        int msgs = 0;
        for (MatterMessage m : messages.findByTenantIdAndClientId(tid, source.getId())) {
            m.setClientId(into.getId());
            messages.save(m);
            msgs++;
        }
        counts.put("messagesMoved", msgs);
        int docs = 0;
        for (DocumentFile d : documents.findByTenantIdAndClientId(tid, source.getId())) {
            d.setClientId(into.getId());
            documents.save(d);
            docs++;
        }
        counts.put("documentsMoved", docs);
        int sigs = 0;
        for (SignatureRequest s : signatures.findByTenantIdAndClientIdOrderByCreatedAtDesc(tid, source.getId())) {
            if ("pending".equals(s.getStatus())) {
                s.setClientId(into.getId());
                signatures.save(s);
                sigs++;
            }
        }
        counts.put("pendingSignatureRequestsMoved", sigs);
        int ms = 0;
        for (MatterMilestone m : milestones.findByTenantIdAndClientIdOrderByCreatedAtAsc(tid, source.getId())) {
            m.setClientId(into.getId());
            milestones.save(m);
            ms++;
        }
        counts.put("milestonesMoved", ms);
        int inv = 0;
        int kept = 0;
        for (Invoice i : invoices.findByTenantIdAndClientId(tid, source.getId())) {
            if (isUntouched(i)) {
                i.setClientId(into.getId());
                invoices.save(i);
                inv++;
            } else {
                kept++;
            }
        }
        counts.put("unpaidInvoicesMoved", inv);
        counts.put("paidInvoicesKeptOnOldRecord", kept);
        boolean ficaMoved = false;
        if (ficaProfiles.findByTenantIdAndClientId(tid, into.getId()).isEmpty()) {
            FicaProfile p = ficaProfiles.findByTenantIdAndClientId(tid, source.getId()).orElse(null);
            if (p != null) {
                p.setClientId(into.getId());
                ficaProfiles.save(p);
                ficaMoved = true;
            }
        }
        counts.put("ficaRecordMoved", ficaMoved);
        if (into.getIdNumber() == null && source.getIdNumber() != null) into.setIdNumber(source.getIdNumber());
        if (into.getEmail() == null && source.getEmail() != null) into.setEmail(source.getEmail());
        if (into.getPhone() == null && source.getPhone() != null) into.setPhone(source.getPhone());
        into.setUpdatedAt(Instant.now());
        clients.save(into);

        source.setStatus("merged");
        source.setPortalEnabled(false);
        source.setPortalPasswordHash(null);
        String note = "Merged into " + into.displayName() + " on " + java.time.LocalDate.now() + ". Reason: " + reason;
        source.setNotes(source.getNotes() == null || source.getNotes().isBlank() ? note : source.getNotes() + "\n" + note);
        source.setUpdatedAt(Instant.now());
        clients.save(source);

        ClientChange change = record("merge", null, source, into, reason, actor, true, Map.of(), counts);
        List<Map<String, Object>> trust = requestTrustTransfers(change, null, source, into, reason);
        counts.put("trustTransfersAwaitingApproval", trust.size());
        change.setCountsJson(json(counts));
        changes.save(change);
        audit.record("client.merge", "client", into.getId().toString(),
                source.displayName() + " (" + source.getId() + ") merged into " + into.displayName() + " (" + into.getId() + "). Reason: "
                        + reason + ". Actor " + actor + ". " + json(counts));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changeId", change.getId());
        out.put("into", Map.of("id", into.getId(), "name", into.displayName()));
        out.put("source", Map.of("id", source.getId(), "name", source.displayName(), "status", "merged"));
        out.put("counts", counts);
        out.put("trustTransfers", trust);
        return out;
    }

    // ---------- Trust transfers between client ledgers ----------

    public List<Map<String, Object>> transfers() {
        return transfers.findByTenantIdOrderByCreatedAtDesc(tid()).stream().map(this::transferView).toList();
    }

    @Transactional
    public Map<String, Object> approveTransfer(UUID id) {
        TrustClientTransfer t = transfer(id);
        if (!"pending".equals(t.getStatus())) throw ApiException.badRequest("This transfer is already " + t.getStatus() + ".");
        if (!Roles.isTenantAdmin(Roles.current())) {
            throw ApiException.forbidden("Only a firm admin (owner, director or partner) can approve a trust transfer.");
        }
        UUID me = TenantContext.getUserId();
        if (me != null && me.equals(t.getRequestedBy())) {
            throw ApiException.forbidden("A second person must approve this. You asked for the transfer, so someone else has to approve it.");
        }
        Client from = client(t.getFromClientId());
        Client to = client(t.getToClientId());
        UUID[] rows = finance.postClientTransfer(t.getTrustAccountId(), from.getId(), to.getId(), t.getCaseId(), t.getAmount(),
                "Client correction: " + from.displayName() + " to " + to.displayName() + ". " + t.getReason());
        t.setOutTransactionId(rows[0]);
        t.setInTransactionId(rows[1]);
        t.setStatus("approved");
        t.setDecidedBy(me);
        t.setDecidedAt(Instant.now());
        transfers.save(t);
        audit.record("trust.client_transfer_approved", "trust_client_transfer", t.getId().toString(),
                t.getAmount().toPlainString() + " requested by " + t.getRequestedBy() + ", approved by " + me);
        return transferView(t);
    }

    @Transactional
    public Map<String, Object> rejectTransfer(UUID id, String note) {
        TrustClientTransfer t = transfer(id);
        if (!"pending".equals(t.getStatus())) throw ApiException.badRequest("This transfer is already " + t.getStatus() + ".");
        UUID me = TenantContext.getUserId();
        if (!Roles.isTenantAdmin(Roles.current()) && !Objects.equals(me, t.getRequestedBy())) {
            throw ApiException.forbidden("Only a firm admin or the person who asked can reject this transfer.");
        }
        t.setStatus("rejected");
        t.setDecidedBy(me);
        t.setDecidedAt(Instant.now());
        transfers.save(t);
        audit.record("trust.client_transfer_rejected", "trust_client_transfer", t.getId().toString(),
                t.getAmount().toPlainString() + (note == null ? "" : ". " + note));
        return transferView(t);
    }

    // ---------- helpers ----------

    private List<Map<String, Object>> requestTrustTransfers(ClientChange change, UUID caseId, Client from, Client to, String reason) {
        UUID tid = tid();
        List<Map<String, Object>> out = new ArrayList<>();
        for (TrustAccount a : trustAccounts.findByTenantId(tid)) {
            for (Map.Entry<UUID, BigDecimal> e : heldByMatter(a.getId(), from.getId(), caseId).entrySet()) {
                out.add(requestTransfer(change, a, e.getKey(), e.getValue(), from, to, reason));
            }
        }
        return out;
    }

    /** For one matter: what the client holds on it. For a merge (caseId null): one amount per matter, plus any unallocated. */
    private Map<UUID, BigDecimal> heldByMatter(UUID accountId, UUID clientId, UUID caseId) {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        if (caseId != null) {
            BigDecimal h = finance.clientMatterLedger(accountId, clientId, caseId);
            if (h.signum() > 0) out.put(caseId, h);
            return out;
        }
        BigDecimal total = finance.clientMatterLedger(accountId, clientId, null);
        BigDecimal allocated = BigDecimal.ZERO;
        for (UUID m : finance.clientTrustMatters(accountId, clientId)) {
            if (m == null) continue;
            BigDecimal h = finance.clientMatterLedger(accountId, clientId, m);
            allocated = allocated.add(h);
            if (h.signum() > 0) out.put(m, h);
        }
        BigDecimal rest = total.subtract(allocated);
        if (rest.signum() > 0) out.put(null, rest);
        return out;
    }

    private Map<String, Object> requestTransfer(ClientChange change, TrustAccount a, UUID caseId, BigDecimal held, Client from, Client to,
            String reason) {
        UUID tid = tid();
        {
            TrustClientTransfer t = new TrustClientTransfer();
            t.setTenantId(tid);
            t.setChangeId(change.getId());
            t.setTrustAccountId(a.getId());
            t.setCaseId(caseId);
            t.setFromClientId(from.getId());
            t.setToClientId(to.getId());
            t.setAmount(held);
            t.setReason(reason);
            t.setRequestedBy(TenantContext.getUserId());
            transfers.save(t);
            audit.record("trust.client_transfer_requested", "trust_client_transfer", t.getId().toString(),
                    held.toPlainString() + " from " + from.displayName() + " to " + to.displayName());
            return transferView(t);
        }
    }

    private Map<String, Object> moveInvoices(LegalCase c, Client from, Client to, String reason, boolean merge) {
        int moved = 0;
        int credited = 0;
        List<String> creditNotes = new ArrayList<>();
        for (Invoice i : caseInvoices(c, from.getId())) {
            if (isUntouched(i)) {
                i.setClientId(to.getId());
                invoices.save(i);
                moved++;
                continue;
            }
            Invoice cn = new Invoice();
            cn.setTenantId(i.getTenantId());
            cn.setClientId(from.getId());
            cn.setCaseId(i.getCaseId());
            cn.setInvoiceNumber(i.getInvoiceNumber() + "-CN");
            cn.setStatus("credit_note");
            cn.setSubtotal(nz(i.getSubtotal()).negate());
            cn.setTaxAmount(nz(i.getTaxAmount()).negate());
            cn.setTotal(nz(i.getTotal()).negate());
            cn.setLineItemsJson(i.getLineItemsJson());
            cn.setNotes("Credit note for " + i.getInvoiceNumber() + ": issued to the wrong client. Reason: " + reason);
            invoices.save(cn);
            credited++;
            creditNotes.add(cn.getInvoiceNumber());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unpaidInvoicesMoved", moved);
        out.put("creditNotesIssued", credited);
        out.put("creditNoteNumbers", creditNotes);
        return out;
    }

    /** Never paid, never written off, still live: safe to point at the right client. */
    boolean isUntouched(Invoice i) {
        return !CLOSED_INVOICE.contains(i.getStatus()) && !"paid".equals(i.getStatus())
                && nz(i.getAmountPaid()).signum() == 0 && nz(i.getWriteOffAmount()).signum() == 0;
    }

    private List<Invoice> caseInvoices(LegalCase c, UUID clientId) {
        return invoices.findByTenantIdAndClientId(tid(), clientId).stream()
                .filter(i -> c.getId().equals(i.getCaseId()) && !CLOSED_INVOICE.contains(i.getStatus())).toList();
    }

    private List<SignatureRequest> caseSignatures(LegalCase c) {
        return signatures.findByTenantIdOrderByCreatedAtDesc(tid()).stream().filter(s -> c.getId().equals(s.getCaseId())).toList();
    }

    private ClientChange record(String kind, UUID caseId, Client from, Client to, String reason, UUID actor, boolean showHistory,
            Map<String, Object> plan, Map<String, Object> counts) {
        ClientChange ch = new ClientChange();
        ch.setTenantId(tid());
        ch.setKind(kind);
        ch.setCaseId(caseId);
        ch.setFromClientId(from.getId());
        ch.setToClientId(to.getId());
        ch.setReason(reason);
        ch.setActorId(actor);
        ch.setShowHistory(showHistory);
        ch.setPopiaFlag(Boolean.TRUE.equals(plan.get("possiblePopiaIncident")));
        Object d = plan.get("popiaDetail");
        ch.setPopiaDetail(ch.isPopiaFlag() && d != null ? String.valueOf(d) : null);
        ch.setCountsJson(json(counts));
        return changes.save(ch);
    }

    private void requireChanger(LegalCase c) {
        UUID me = TenantContext.getUserId();
        boolean lead = me != null && me.equals(c.getLeadAttorneyId());
        if (!Roles.isTenantAdmin(Roles.current()) && !lead) {
            throw ApiException.forbidden("Only a firm admin or the matter's lead attorney can change the client.");
        }
    }

    private LegalCase matter(UUID id) {
        return cases.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Matter not found"));
    }

    private Client client(UUID id) {
        if (id == null) throw ApiException.badRequest("This matter has no client.");
        return clients.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
    }

    private Client targetClient(LegalCase c, UUID toId) {
        if (toId == null) throw ApiException.badRequest("Choose the right client.");
        if (toId.equals(c.getClientId())) throw ApiException.badRequest("That is already the client on this matter.");
        Client to = client(toId);
        if ("merged".equals(to.getStatus())) throw ApiException.badRequest("That client record was merged into another. Choose the kept record.");
        return to;
    }

    private Client mergeTarget(Client source, UUID intoId) {
        if (intoId == null || intoId.equals(source.getId())) throw ApiException.badRequest("Choose a different client record to keep.");
        if ("merged".equals(source.getStatus())) throw ApiException.badRequest("This client was already merged.");
        Client into = client(intoId);
        if ("merged".equals(into.getStatus())) throw ApiException.badRequest("You cannot merge into a record that was itself merged.");
        return into;
    }

    private TrustClientTransfer transfer(UUID id) {
        return transfers.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Transfer not found"));
    }

    private Map<String, Object> transferView(TrustClientTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("trustAccountId", t.getTrustAccountId());
        m.put("caseId", t.getCaseId());
        m.put("fromClientId", t.getFromClientId());
        m.put("toClientId", t.getToClientId());
        m.put("fromClientName", clients.findByIdAndTenantId(t.getFromClientId(), t.getTenantId()).map(Client::displayName).orElse(""));
        m.put("toClientName", clients.findByIdAndTenantId(t.getToClientId(), t.getTenantId()).map(Client::displayName).orElse(""));
        m.put("amount", t.getAmount());
        m.put("status", t.getStatus());
        m.put("reason", t.getReason());
        m.put("requestedBy", t.getRequestedBy());
        m.put("decidedBy", t.getDecidedBy());
        m.put("createdAt", t.getCreatedAt());
        m.put("decidedAt", t.getDecidedAt());
        return m;
    }

    private Map<String, Object> changeView(ClientChange ch) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ch.getId());
        m.put("kind", ch.getKind());
        m.put("fromClientId", ch.getFromClientId());
        m.put("toClientId", ch.getToClientId());
        m.put("fromClientName", clients.findByIdAndTenantId(ch.getFromClientId(), ch.getTenantId()).map(Client::displayName).orElse(""));
        m.put("toClientName", clients.findByIdAndTenantId(ch.getToClientId(), ch.getTenantId()).map(Client::displayName).orElse(""));
        m.put("reason", ch.getReason());
        m.put("actorId", ch.getActorId());
        m.put("showHistory", ch.isShowHistory());
        m.put("popiaFlag", ch.isPopiaFlag());
        m.put("popiaDetail", ch.getPopiaDetail());
        m.put("counts", ch.getCountsJson());
        m.put("createdAt", ch.getCreatedAt());
        return m;
    }

    private static String reason(Map<String, Object> body) {
        String r = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (r.length() < 10) throw ApiException.badRequest("Give a reason of at least 10 characters. It goes in the audit log.");
        if (r.length() > 1000) r = r.substring(0, 1000);
        return r;
    }

    private static boolean bool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
    }

    private static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }

    private static String json(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
