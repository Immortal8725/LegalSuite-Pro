package com.legalsuite.milestones;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.inbox.MatterInboxService;
import com.legalsuite.litigation.LitigationCatalog;
import com.legalsuite.paylinks.PayLinkService;
import com.legalsuite.push.PushService;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.Compliance;
import com.legalsuite.service.DocketEngine;
import com.legalsuite.service.DocumentHash;
import com.legalsuite.service.FinanceService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposit and milestone billing.
 *
 * Legal Practice Act 28 of 2014 s 86: money a client pays in advance for fees not yet earned is trust money. So a
 * "deposit" milestone is a request for a deposit into trust: when it is paid online the money is posted to the
 * client's trust ledger, never to the business account. A "stage" milestone is a fee earned when the matter reaches
 * a litigation stage: it becomes a normal fee invoice, which the firm may then settle from the client's trust
 * deposit (a fee transfer) or the client pays directly.
 */
@Service
public class MilestoneService {
    private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);
    private final MatterMilestoneRepository repo;
    private final LegalCaseRepository cases;
    private final InvoiceRepository invoices;
    private final TenantRepository tenants;
    private final TrustAccountRepository trusts;
    private final LitigationCatalog catalog;
    private final AuditService audit;
    private final ObjectProvider<FinanceService> finance;
    private final ObjectProvider<PayLinkService> payLinks;
    private final ObjectProvider<MatterInboxService> inbox;
    private final ObjectProvider<PushService> push;

    public MilestoneService(MatterMilestoneRepository repo, LegalCaseRepository cases, InvoiceRepository invoices,
                            TenantRepository tenants, TrustAccountRepository trusts, LitigationCatalog catalog, AuditService audit,
                            ObjectProvider<FinanceService> finance, ObjectProvider<PayLinkService> payLinks,
                            ObjectProvider<MatterInboxService> inbox, ObjectProvider<PushService> push) {
        this.repo = repo;
        this.cases = cases;
        this.invoices = invoices;
        this.tenants = tenants;
        this.trusts = trusts;
        this.catalog = catalog;
        this.audit = audit;
        this.finance = finance;
        this.payLinks = payLinks;
        this.inbox = inbox;
        this.push = push;
    }

    private LegalCase staffCase(UUID caseId) {
        if ("client".equalsIgnoreCase(String.valueOf(TenantContext.getRole()))) throw ApiException.forbidden("Staff only");
        return cases.findByIdAndTenantId(caseId, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Matter not found"));
    }

    public Map<String, Object> plan(UUID caseId) {
        LegalCase c = staffCase(caseId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", c.getId());
        out.put("track", c.getLitigationTrack());
        out.put("currentStage", c.getLitigationStage());
        LitigationCatalog.Track t = c.getLitigationTrack() == null ? null : catalog.track(c.getLitigationTrack());
        List<Map<String, Object>> stages = new ArrayList<>();
        if (t != null) for (LitigationCatalog.Stage s : t.stages()) stages.add(Map.of("id", s.id(), "label", s.label()));
        out.put("stages", stages);
        out.put("milestones", repo.findByTenantIdAndCaseIdOrderBySortOrderAsc(c.getTenantId(), c.getId()).stream().map(m -> view(m, t)).toList());
        return out;
    }

    /** Replace the matter's pending milestones. Milestones already invoiced or paid are kept as they are. */
    @Transactional
    public Map<String, Object> savePlan(UUID caseId, Map<String, Object> body) {
        LegalCase c = staffCase(caseId);
        if (c.getClientId() == null) throw ApiException.badRequest("Link a client to this matter before adding a fee plan.");
        Object raw = body == null ? null : body.get("milestones");
        if (!(raw instanceof List<?> list)) throw ApiException.badRequest("Send the milestones as a list.");
        if (list.size() > 30) throw ApiException.badRequest("A fee plan can have at most 30 milestones.");
        LitigationCatalog.Track t = c.getLitigationTrack() == null ? null : catalog.track(c.getLitigationTrack());
        List<MatterMilestone> existing = repo.findByTenantIdAndCaseIdOrderBySortOrderAsc(c.getTenantId(), c.getId());
        List<MatterMilestone> keep = new ArrayList<>();
        for (MatterMilestone m : existing) {
            if ("pending".equals(m.getStatus())) repo.delete(m); else keep.add(m);
        }
        int order = keep.size();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) throw ApiException.badRequest("Each milestone needs a label, kind and amount.");
            if (row.get("id") != null && keep.stream().anyMatch(k -> k.getId().toString().equals(String.valueOf(row.get("id"))))) continue;
            String kind = String.valueOf(row.get("kind") == null ? "stage" : row.get("kind")).trim().toLowerCase();
            if (!kind.equals("deposit") && !kind.equals("stage")) throw ApiException.badRequest("A milestone is either a deposit or a stage fee.");
            String label = row.get("label") == null ? "" : String.valueOf(row.get("label")).trim();
            if (label.isEmpty()) label = kind.equals("deposit") ? "Deposit on fees" : "Stage fee";
            if (label.length() > 200) label = label.substring(0, 200);
            BigDecimal amount;
            try { amount = new BigDecimal(String.valueOf(row.get("amount")).trim()).setScale(2, RoundingMode.HALF_UP); }
            catch (Exception e) { throw ApiException.badRequest("Enter each milestone amount as a number."); }
            if (amount.signum() <= 0) throw ApiException.badRequest("Milestone amounts must be more than zero.");
            String stage = null;
            if (kind.equals("stage")) {
                stage = row.get("stage") == null ? "" : String.valueOf(row.get("stage")).trim();
                if (t == null) throw ApiException.badRequest("Choose a litigation track for this matter before tying fees to stages.");
                if (t.stage(stage) == null) throw ApiException.badRequest("Unknown stage '" + stage + "' for this litigation track.");
            }
            MatterMilestone m = new MatterMilestone();
            m.setTenantId(c.getTenantId());
            m.setCaseId(c.getId());
            m.setClientId(c.getClientId());
            m.setKind(kind);
            m.setLabel(label);
            m.setStage(stage);
            m.setAmount(amount);
            m.setSortOrder(order++);
            repo.save(m);
        }
        audit.record("fee_plan.save", "case", c.getId().toString(), c.getCaseNumber() + " " + list.size() + " milestones");
        return plan(caseId);
    }

    /** Staff: bill a milestone now (a deposit is usually requested straight away). */
    @Transactional
    public Map<String, Object> bill(UUID milestoneId) {
        MatterMilestone m = repo.findByIdAndTenantId(milestoneId, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Milestone not found"));
        LegalCase c = staffCase(m.getCaseId());
        if (!"pending".equals(m.getStatus())) throw ApiException.badRequest("This milestone has already been billed.");
        issue(c, m);
        return plan(c.getId());
    }

    /** Called by LitigationService when a matter enters a stage. Bills every pending milestone tied to that stage. */
    @Transactional
    public List<Map<String, Object>> onStage(LegalCase c, String stage) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (c.getClientId() == null || stage == null) return out;
        for (MatterMilestone m : repo.findByTenantIdAndCaseIdOrderBySortOrderAsc(c.getTenantId(), c.getId())) {
            if ("pending".equals(m.getStatus()) && "stage".equals(m.getKind()) && stage.equals(m.getStage())) {
                issue(c, m);
                out.add(view(m, null));
            }
        }
        return out;
    }

    private void issue(LegalCase c, MatterMilestone m) {
        UUID tid = c.getTenantId();
        Tenant tenant = tenants.findById(tid).orElse(null);
        boolean deposit = "deposit".equals(m.getKind());
        Invoice inv = new Invoice();
        inv.setTenantId(tid);
        inv.setClientId(c.getClientId());
        inv.setCaseId(c.getId());
        inv.setInvoiceNumber((deposit ? "DEP-" : "INV-") + (1000 + invoices.countByTenantId(tid) + 1));
        inv.setStatus("sent");
        inv.setDateIssued(LocalDate.now());
        inv.setDateDue(LocalDate.now().plusDays(deposit ? 7 : 30));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("kind", deposit ? "trust_deposit" : "fee");
        line.put("description", m.getLabel());
        line.put("amount", m.getAmount());
        inv.setLineItemsJson(JsonLists.toJson(List.of(line)));
        inv.setSubtotal(m.getAmount());
        if (deposit) {
            inv.setTaxAmount(BigDecimal.ZERO);
            inv.setTotal(m.getAmount());
            inv.setNotes("Request for a deposit on fees. Not a tax invoice. Paid into the firm's trust account and held for you "
                    + "(Legal Practice Act 28 of 2014 s 86) until fees are earned and invoiced.");
        } else if ("ZA".equals(DocketEngine.of(tenant))) {
            BigDecimal vat = m.getAmount().multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            inv.setTaxAmount(vat);
            inv.setTotal(m.getAmount().add(vat));
            inv.setNotes("Milestone fee: " + m.getLabel() + ". VAT 15% (Value-Added Tax Act 89 of 1991 s 7).");
        } else {
            inv.setTotal(m.getAmount());
            inv.setNotes("Milestone fee: " + m.getLabel() + ".");
        }
        invoices.save(inv);
        m.setInvoiceId(inv.getId());
        m.setStatus("invoiced");
        m.setTriggeredAt(Instant.now());
        String url = null;
        try {
            PayLinkService p = payLinks.getIfAvailable();
            if (p != null) url = String.valueOf(p.create(inv.getId()).get("url"));
        } catch (ApiException e) {
            log.info("No pay link for milestone {}: {}", m.getId(), e.getMessage());
        }
        m.setPayUrl(url);
        repo.save(m);
        audit.record("milestone.bill", "invoice", inv.getId().toString(), c.getCaseNumber() + " " + m.getLabel() + " " + inv.getTotal());
        String text = (deposit
                ? "We have requested a deposit of R " + inv.getTotal().toPlainString() + " (" + m.getLabel() + "), reference " + inv.getInvoiceNumber()
                  + ". It is paid into our trust account and held for you until fees are earned."
                : "Your matter has reached a new stage, so the agreed fee of R " + inv.getTotal().toPlainString() + " (" + m.getLabel()
                  + ", invoice " + inv.getInvoiceNumber() + ") is now due.")
                + (url == null ? " You can pay by EFT using the reference." : " Pay securely online: " + url);
        try {
            MatterInboxService ib = inbox.getIfAvailable();
            if (ib != null) ib.staffSend(c.getId(), text, null);
        } catch (ApiException e) {
            log.info("Milestone inbox message not sent for {}: {}", m.getId(), e.getMessage());
        }
        PushService ps = push.getIfAvailable();
        if (ps != null) ps.toClient(tid, c.getClientId(), deposit ? "Deposit requested" : "Fee due", "You have a new payment request.", "/portal");
    }

    /** True when the PayFast ITN for this invoice was handled here (a trust deposit), so the caller must not book it as fees. */
    public boolean isTrustDeposit(UUID invoiceId) {
        return repo.findFirstByInvoiceId(invoiceId).map(m -> "deposit".equals(m.getKind())).orElse(false);
    }

    /** Online payment of a deposit: post it to the client's trust ledger. Runs with the tenant set by the ITN handler. */
    @Transactional
    public void depositPaid(UUID invoiceId, BigDecimal gross, String reference) {
        MatterMilestone m = repo.findFirstByInvoiceId(invoiceId).orElseThrow();
        Invoice inv = invoices.findById(invoiceId).orElseThrow();
        m.setPaidAt(Instant.now());
        inv.setAmountPaid(gross);
        inv.setStatus("paid");
        inv.setNotes((inv.getNotes() == null ? "" : inv.getNotes() + " ") + "Received " + gross.toPlainString() + " (" + reference + ").");
        invoices.save(inv);
        m.setStatus("held");
        repo.save(m);
        postToTrust(m, gross, reference);
    }

    /** Staff: retry posting a paid deposit into trust (for example after the mandate is signed). */
    @Transactional
    public Map<String, Object> retryTrust(UUID milestoneId) {
        MatterMilestone m = repo.findByIdAndTenantId(milestoneId, TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Milestone not found"));
        staffCase(m.getCaseId());
        if (!"held".equals(m.getStatus())) throw ApiException.badRequest("Only a paid deposit waiting for trust can be posted.");
        Invoice inv = invoices.findById(m.getInvoiceId()).orElseThrow();
        String why = postToTrust(m, inv.getAmountPaid(), inv.getInvoiceNumber());
        if (why != null) throw ApiException.badRequest(why);
        return plan(m.getCaseId());
    }

    /** Returns null when posted, otherwise why it is still held. Checks first so a failure never rolls back the payment. */
    private String postToTrust(MatterMilestone m, BigDecimal amount, String reference) {
        Tenant tenant = tenants.findById(m.getTenantId()).orElse(null);
        String why = null;
        try { Compliance.requireFfcForTrust(tenant); } catch (ApiException e) { why = e.getMessage(); }
        LegalCase c = cases.findByIdAndTenantId(m.getCaseId(), m.getTenantId()).orElse(null);
        if (why == null && c != null && !DocumentHash.appearanceAuthorized(c.getEngagementStatus(), c.isAppearanceAuthorized())) {
            why = "The mandate is not signed yet, so the deposit is held until it is.";
        }
        TrustAccount acct = trusts.findByTenantId(m.getTenantId()).stream().filter(a -> !"closed".equals(a.getStatus())).findFirst().orElse(null);
        if (why == null && acct == null) why = "Open a trust account first.";
        if (why != null) {
            audit.recordFor(m.getTenantId(), "milestone.deposit_held", "case", m.getCaseId().toString(), why);
            return why;
        }
        Map<String, Object> move = new HashMap<>();
        move.put("accountId", acct.getId().toString());
        move.put("type", "deposit");
        move.put("amount", amount.toPlainString());
        move.put("clientId", m.getClientId().toString());
        move.put("caseId", m.getCaseId().toString());
        move.put("description", "Deposit on fees, " + m.getLabel() + " (" + reference + ")");
        Map<String, Object> res = finance.getObject().trustMove(move, false);
        Object tx = res.get("transaction");
        if (tx instanceof Map<?, ?> t && t.get("id") != null) m.setTrustTransactionId(UUID.fromString(String.valueOf(t.get("id"))));
        m.setStatus("paid");
        repo.save(m);
        audit.recordFor(m.getTenantId(), "milestone.deposit_trust", "case", m.getCaseId().toString(), amount.toPlainString());
        return null;
    }

    /** Portal: the client's own milestones on all their matters. */
    public List<Map<String, Object>> forClient(UUID clientId) {
        UUID tid = TenantContext.requireTenant();
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatterMilestone m : repo.findByTenantIdAndClientIdOrderByCreatedAtAsc(tid, clientId)) {
            LegalCase c = cases.findByIdAndTenantId(m.getCaseId(), tid).orElse(null);
            if (c == null) continue;
            LitigationCatalog.Track t = c.getLitigationTrack() == null ? null : catalog.track(c.getLitigationTrack());
            Map<String, Object> v = view(m, t);
            v.put("caseTitle", c.getTitle());
            v.put("caseNumber", c.getCaseNumber());
            out.add(v);
        }
        return out;
    }

    private Map<String, Object> view(MatterMilestone m, LitigationCatalog.Track t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("caseId", m.getCaseId());
        v.put("label", m.getLabel());
        v.put("kind", m.getKind());
        v.put("stage", m.getStage());
        if (t != null && m.getStage() != null && t.stage(m.getStage()) != null) v.put("stageLabel", t.stage(m.getStage()).label());
        v.put("amount", m.getAmount());
        v.put("status", m.getStatus());
        v.put("invoiceId", m.getInvoiceId());
        v.put("payUrl", "invoiced".equals(m.getStatus()) ? m.getPayUrl() : null);
        v.put("triggeredAt", m.getTriggeredAt());
        v.put("paidAt", m.getPaidAt());
        v.put("intoTrust", "deposit".equals(m.getKind()));
        return v;
    }
}
