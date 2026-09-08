package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentTemplate;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.SignatureRequest;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentTemplateRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.SignatureRequestRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TrustAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetainService {
    private final LeadRepository leads;
    private final ClientRepository clients;
    private final DocumentTemplateRepository templates;
    private final TrustAccountRepository trusts;
    private final CommsService comms;
    private final PracticeService practice;
    private final TemplateService templatesEngine;
    private final SignatureService signatures;
    private final SignatureRequestRepository signatureRows;
    private final AuditService audit;
    private final TenantRepository tenants;

    public RetainService(
            LeadRepository leads,
            ClientRepository clients,
            DocumentTemplateRepository templates,
            TrustAccountRepository trusts,
            CommsService comms,
            PracticeService practice,
            TemplateService templatesEngine,
            SignatureService signatures,
            SignatureRequestRepository signatureRows,
            AuditService audit,
            TenantRepository tenants) {
        this.leads = leads;
        this.clients = clients;
        this.templates = templates;
        this.trusts = trusts;
        this.comms = comms;
        this.practice = practice;
        this.templatesEngine = templatesEngine;
        this.signatures = signatures;
        this.signatureRows = signatureRows;
        this.audit = audit;
        this.tenants = tenants;
    }

    @Transactional
    public Map<String, Object> retain(UUID leadId, Map<String, Object> body) {
        UUID tid = TenantContext.requireTenant();
        Lead lead = leads.findByIdAndTenantId(leadId, tid).orElseThrow(() -> ApiException.notFound("Lead not found"));
        boolean force = Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", "false")));
        Map<String, Object> extra = new HashMap<>();
        extra.put("opposingParty", lead.getOpposingParty());
        extra.put("email", lead.getEmail());
        Map<String, Object> conflict = comms.conflictCheck(lead.getName(), extra);
        int matches = ((Number) conflict.getOrDefault("matchCount", 0)).intValue();
        SignatureRequest signedWaiver = signatureRows
                .findFirstByTenantIdAndLeadIdAndPurposeAndStatusOrderByCreatedAtDesc(tid, leadId, "conflict_waiver", "signed")
                .orElse(null);
        if (matches > 0 && !"cleared".equals(conflict.get("status"))) {
            if (!force) {
                Map<String, Object> blocked = new HashMap<>();
                blocked.put("blocked", true);
                blocked.put("waiverRequired", true);
                blocked.put("waiverSigned", signedWaiver != null);
                blocked.put("reason", signedWaiver == null
                        ? "Conflict hits. Issue a written waiver, get it signed, then retain. A button is not informed consent."
                        : "Waiver is signed. Retain with the signed instrument to open a limited file.");
                blocked.put("conflict", conflict);
                blocked.put("leadId", lead.getId());
                if (signedWaiver != null) {
                    blocked.put("waiver", signatures.instrumentView(signedWaiver));
                } else {
                    signatureRows.findFirstByTenantIdAndLeadIdAndPurposeOrderByCreatedAtDesc(tid, leadId, "conflict_waiver")
                            .ifPresent(w -> blocked.put("waiver", signatures.instrumentView(w)));
                }
                return blocked;
            }
            if (signedWaiver == null) {
                throw ApiException.badRequest("No signed conflict waiver on file. Issue the waiver and get a signature first.");
            }
        }

        Client client;
        String email = lead.getEmail() == null ? "" : lead.getEmail().trim();
        if (!email.isBlank()) {
            client = clients.findByTenantIdAndEmailIgnoreCase(tid, email).orElse(null);
        } else {
            client = null;
        }
        if (client == null) {
            Map<String, Object> cbody = new HashMap<>();
            String[] parts = (lead.getName() == null ? "New Client" : lead.getName()).trim().split("\\s+", 2);
            cbody.put("firstName", parts[0]);
            cbody.put("lastName", parts.length > 1 ? parts[1] : "");
            cbody.put("email", email);
            cbody.put("phone", lead.getPhone());
            cbody.put("source", "website");
            cbody.put("status", "active");
            cbody.put("portalEnabled", true);
            Map<String, Object> saved = practice.saveClient(null, cbody);
            client = clients.findByIdAndTenantId((UUID) saved.get("id"), tid).orElseThrow();
        }

        String area = lead.getCaseType() == null || lead.getCaseType().isBlank() ? "General" : lead.getCaseType();
        TexasDocketRules.Facts facts = factsFromLead(lead);
        if (body.get("accrualDate") != null) facts.accrualDate = TexasDocketRules.parseDate(body.get("accrualDate"));
        if (body.get("dateOfBirth") != null) facts.dateOfBirth = TexasDocketRules.parseDate(body.get("dateOfBirth"));
        if (body.get("governmentalDefendant") != null) facts.governmentalDefendant = TexasDocketRules.bool(body.get("governmentalDefendant"));
        if (body.get("hitAndRun") != null) facts.hitAndRun = TexasDocketRules.bool(body.get("hitAndRun"));
        if (body.get("opposingParty") != null) facts.opposingParty = String.valueOf(body.get("opposingParty"));
        com.legalsuite.domain.Tenant tenant = tenants.findById(tid).orElse(null);
        String trustLabel = DocketEngine.trustLabel(tenant);
        TexasDocketRules.Result docket = DocketEngine.compute(tenant, facts);
        Map<String, Object> caseBody = new HashMap<>();
        caseBody.put("clientId", client.getId());
        caseBody.put("title", area + " — " + client.displayName());
        caseBody.put("practiceArea", area);
        caseBody.put("caseType", area);
        caseBody.put("description", lead.getDescription());
        caseBody.put("opposingParty", facts.opposingParty);
        if (facts.accrualDate != null) caseBody.put("accrualDate", facts.accrualDate.toString());
        if (facts.dateOfBirth != null) caseBody.put("dateOfBirth", facts.dateOfBirth.toString());
        if (facts.discoveryDate != null) caseBody.put("discoveryDate", facts.discoveryDate.toString());
        if (facts.probateOpened != null) caseBody.put("probateOpened", facts.probateOpened.toString());
        boolean organ = facts.governmentalDefendant
                || ("ZA".equals(DocketEngine.of(tenant))
                        ? SouthAfricanDocketRules.looksOrganOfState(facts.opposingParty)
                        : TexasDocketRules.looksGovernmental(facts.opposingParty));
        caseBody.put("governmentalDefendant", organ);
        caseBody.put("hitAndRun", facts.hitAndRun || ("ZA".equals(DocketEngine.of(tenant)) && SouthAfricanDocketRules.looksHitAndRun(facts)));
        caseBody.put("status", "limited");
        caseBody.put("engagementStatus", "unsigned");
        caseBody.put("appearanceAuthorized", false);
        BigDecimal retainer = new BigDecimal(String.valueOf(body.getOrDefault("retainerAmount", "2500")));
        List<TrustAccount> accounts = trusts.findByTenantId(tid);
        if (!accounts.isEmpty() && retainer.signum() > 0) {
            caseBody.put("pendingRetainerAmount", retainer);
            caseBody.put("pendingTrustAccountId", accounts.get(0).getId());
        }
        if (signedWaiver != null) {
            caseBody.put("conflictWaiverSignatureId", signedWaiver.getId());
            caseBody.put("conflictWaiverHash", signedWaiver.getSignatureHash());
        }
        Map<String, Object> matter = practice.saveCase(null, caseBody);
        UUID caseId = (UUID) matter.get("id");

        DocumentTemplate template = templates.findByTenantIdOrderByNameAsc(tid).stream()
                .filter(t -> "retainer".equalsIgnoreCase(t.getCategory()) || t.getName().toLowerCase(Locale.ROOT).contains("engagement"))
                .findFirst()
                .orElse(null);
        String merged;
        if (template != null) {
            Map<String, Object> mergedDoc = templatesEngine.merge(template.getId(), Map.of(
                    "caseId", caseId,
                    "clientId", client.getId()));
            merged = String.valueOf(mergedDoc.getOrDefault("merged", fallbackLetter(client, matter)));
        } else {
            merged = fallbackLetter(client, matter);
        }

        Map<String, Object> sigBody = new HashMap<>();
        sigBody.put("title", "Engagement letter — " + client.displayName());
        sigBody.put("documentBody", merged);
        sigBody.put("signerName", client.displayName());
        sigBody.put("signerEmail", client.getEmail() == null ? "" : client.getEmail());
        sigBody.put("caseId", caseId);
        sigBody.put("clientId", client.getId());
        sigBody.put("leadId", lead.getId());
        sigBody.put("purpose", "engagement");
        Map<String, Object> sig = signatures.create(sigBody);
        practice.saveCase(caseId, Map.of("engagementSignatureId", sig.get("id")));

        Map<String, Object> trust = new HashMap<>();
        trust.put("pledged", true);
        trust.put("amount", retainer);
        trust.put("posted", false);
        trust.put("note", "Retainer is pledged. It posts to the " + trustLabel + " when the mandate is signed.");

        lead.setStatus("retained");
        lead.setCaseId(caseId);
        leads.save(lead);
        audit.record("retain.limited", "lead", lead.getId().toString(), client.displayName());

        Map<String, Object> out = new HashMap<>();
        out.put("blocked", false);
        out.put("limited", true);
        out.put("leadId", lead.getId());
        out.put("client", practice.clientView(client));
        out.put("matter", practice.getCase(caseId.toString()));
        out.put("conflict", conflict);
        out.put("signature", sig);
        out.put("signUrl", sig.get("signUrl"));
        out.put("trust", trust);
        out.put("docket", docket.asMap());
        if (signedWaiver != null) {
            out.put("waiver", signatures.instrumentView(signedWaiver));
        }
        out.put("message", "Limited file opened. No appearance. Sign the mandate to authorize the file and post the pledged retainer to the " + trustLabel + ".");
        return out;
    }

    @Transactional
    public Map<String, Object> issueWaiver(UUID leadId) {
        UUID tid = TenantContext.requireTenant();
        Lead lead = leads.findByIdAndTenantId(leadId, tid).orElseThrow(() -> ApiException.notFound("Lead not found"));
        Map<String, Object> extra = new HashMap<>();
        extra.put("opposingParty", lead.getOpposingParty());
        extra.put("email", lead.getEmail());
        Map<String, Object> conflict = comms.conflictCheck(lead.getName(), extra);
        int matches = ((Number) conflict.getOrDefault("matchCount", 0)).intValue();
        if (matches == 0) {
            throw ApiException.badRequest("No conflict hits. A waiver is not required.");
        }
        var existing = signatureRows.findFirstByTenantIdAndLeadIdAndPurposeAndStatusOrderByCreatedAtDesc(
                tid, leadId, "conflict_waiver", "pending");
        if (existing.isPresent()) {
            Map<String, Object> view = signatures.instrumentView(existing.get());
            view.put("conflict", conflict);
            view.put("message", "A waiver is already out for signature.");
            return view;
        }
        Map<String, Object> create = new HashMap<>();
        create.put("title", "Conflict waiver — " + lead.getName());
        create.put("documentBody", waiverLetter(lead, conflict));
        create.put("signerName", lead.getName());
        create.put("signerEmail", lead.getEmail() == null ? "" : lead.getEmail());
        create.put("leadId", lead.getId());
        create.put("conflictCheckId", conflict.get("id"));
        create.put("purpose", "conflict_waiver");
        Map<String, Object> sig = signatures.create(create);
        audit.record("waiver.issue", "lead", lead.getId().toString(), lead.getName());
        Map<String, Object> out = new HashMap<>(sig);
        out.put("conflict", conflict);
        out.put("message", "Waiver issued. Get a signature. Then retain with the signed instrument.");
        return out;
    }

    @SuppressWarnings("unchecked")
    private String waiverLetter(Lead lead, Map<String, Object> conflict) {
        StringBuilder hits = new StringBuilder();
        Object raw = conflict.get("matches");
        if (raw instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m) {
                    hits.append("- ")
                            .append(m.get("role"))
                            .append(": ")
                            .append(m.get("name"))
                            .append(" (")
                            .append(m.get("detail"))
                            .append(")\n");
                }
            }
        }
        return """
                INFORMED CONSENT AND CONFLICT WAIVER

                Prospective client: %s
                Adverse / related hits:
                %s
                I have been told that this firm already has a relationship that may be adverse or substantially related (LPC Code of Conduct — conflicts). I have had a chance to seek independent counsel. I still ask the firm to consider this matter, and I waive the conflict described above to the extent a waiver is permitted.

                This is a signed instrument. A click on “retain anyway” is not consent.
                """.formatted(lead.getName(), hits.toString().isBlank() ? "- (see conflict record)\n" : hits);
    }

    static TexasDocketRules.Facts factsFromLead(Lead lead) {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = lead.getCaseType();
        f.caseType = lead.getCaseType();
        f.description = lead.getDescription();
        f.opposingParty = lead.getOpposingParty();
        f.accrualDate = lead.getAccrualDate();
        f.dateOfBirth = lead.getDateOfBirth();
        f.governmentalDefendant = lead.isGovernmentalDefendant();
        f.hitAndRun = lead.isHitAndRun();
        return f;
    }

    private String fallbackLetter(Client client, Map<String, Object> matter) {
        return """
                ENGAGEMENT AGREEMENT

                This confirms that the firm will represent %s in %s (%s). Fees are hourly. Trust funds, if deposited, sit in the firm trust account (Legal Practice Act s 86 / IOLTA) and apply only to earned fees and costs.

                Sign below to retain the firm. This is not a guarantee of result.
                """.formatted(client.displayName(), matter.get("title"), matter.get("caseNumber"));
    }

    static LocalDate defaultSol(String area) {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = area;
        f.accrualDate = LocalDate.now();
        return TexasDocketRules.compute(f).solDate();
    }
}
