package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentTemplate;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentTemplateRepository;
import com.legalsuite.repo.LeadRepository;
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
    private final FinanceService finance;
    private final AuditService audit;

    public RetainService(
            LeadRepository leads,
            ClientRepository clients,
            DocumentTemplateRepository templates,
            TrustAccountRepository trusts,
            CommsService comms,
            PracticeService practice,
            TemplateService templatesEngine,
            SignatureService signatures,
            FinanceService finance,
            AuditService audit) {
        this.leads = leads;
        this.clients = clients;
        this.templates = templates;
        this.trusts = trusts;
        this.comms = comms;
        this.practice = practice;
        this.templatesEngine = templatesEngine;
        this.signatures = signatures;
        this.finance = finance;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> retain(UUID leadId, Map<String, Object> body) {
        UUID tid = TenantContext.requireTenant();
        Lead lead = leads.findByIdAndTenantId(leadId, tid).orElseThrow(() -> ApiException.notFound("Lead not found"));
        boolean force = Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", "false")));
        Map<String, Object> conflict = comms.conflictCheck(lead.getName());
        int matches = ((Number) conflict.getOrDefault("matchCount", 0)).intValue();
        if (matches > 0 && !force && !"cleared".equals(conflict.get("status"))) {
            Map<String, Object> blocked = new HashMap<>();
            blocked.put("blocked", true);
            blocked.put("reason", "Conflict hits on this name. Clear them or retain with force after a written waiver.");
            blocked.put("conflict", conflict);
            blocked.put("leadId", lead.getId());
            return blocked;
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
        Map<String, Object> caseBody = new HashMap<>();
        caseBody.put("clientId", client.getId());
        caseBody.put("title", area + " — " + client.displayName());
        caseBody.put("practiceArea", area);
        caseBody.put("caseType", area);
        caseBody.put("description", lead.getDescription());
        caseBody.put("status", "intake");
        caseBody.put("statuteOfLimitations", defaultSol(area).toString());
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

        Map<String, Object> sig = signatures.create(Map.of(
                "title", "Engagement letter — " + client.displayName(),
                "documentBody", merged,
                "signerName", client.displayName(),
                "signerEmail", client.getEmail() == null ? "" : client.getEmail(),
                "caseId", caseId,
                "clientId", client.getId()
        ));

        BigDecimal retainer = new BigDecimal(String.valueOf(body.getOrDefault("retainerAmount", "2500")));
        Map<String, Object> trust = Map.of();
        List<TrustAccount> accounts = trusts.findByTenantId(tid);
        if (!accounts.isEmpty() && retainer.signum() > 0) {
            trust = finance.trustMove(Map.of(
                    "accountId", accounts.get(0).getId(),
                    "type", "deposit",
                    "amount", retainer,
                    "clientId", client.getId(),
                    "caseId", caseId,
                    "description", "Retainer on hire from website intake"
            ));
        }

        lead.setStatus("retained");
        leads.save(lead);
        audit.record("retain.complete", "lead", lead.getId().toString(), client.displayName());

        Map<String, Object> out = new HashMap<>();
        out.put("blocked", false);
        out.put("leadId", lead.getId());
        out.put("client", practice.clientView(client));
        out.put("matter", matter);
        out.put("conflict", conflict);
        out.put("signature", sig);
        out.put("signUrl", sig.get("signUrl"));
        out.put("trust", trust);
        out.put("message", "Conflict ran. Matter opened. Engagement is out for signature. Retainer posted to trust when an IOLTA exists.");
        return out;
    }

    private String fallbackLetter(Client client, Map<String, Object> matter) {
        return """
                ENGAGEMENT AGREEMENT

                This confirms that the firm will represent %s in %s (%s). Fees are hourly. Trust funds, if deposited, sit in the firm IOLTA and apply only to earned fees and costs.

                Sign below to retain the firm. This is not a guarantee of result.
                """.formatted(client.displayName(), matter.get("title"), matter.get("caseNumber"));
    }

    static LocalDate defaultSol(String area) {
        String a = area == null ? "" : area.toLowerCase(Locale.ROOT);
        if (a.contains("injur") || a.contains("accident") || a.contains("malpractice")) {
            return LocalDate.now().plusYears(2);
        }
        if (a.contains("estate") || a.contains("probate")) {
            return LocalDate.now().plusYears(4);
        }
        return LocalDate.now().plusYears(2);
    }
}
