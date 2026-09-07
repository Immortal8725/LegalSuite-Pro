package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Note;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final NoteRepository notes;
    private final LeadRepository leads;
    private final AuditService audit;

    public AiService(
            LegalCaseRepository cases,
            ClientRepository clients,
            NoteRepository notes,
            LeadRepository leads,
            AuditService audit) {
        this.cases = cases;
        this.clients = clients;
        this.notes = notes;
        this.leads = leads;
        this.audit = audit;
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        String prompt = String.valueOf(body.getOrDefault("prompt", "")).trim();
        if (prompt.isBlank()) {
            throw ApiException.badRequest("Ask a question about this firm's docket");
        }
        UUID tid = TenantContext.requireTenant();
        List<Map<String, String>> citations = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        for (LegalCase c : cases.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            String blob = c.getTitle() + " " + nz(c.getCaseNumber()) + " " + nz(c.getDescription()) + " " + nz(c.getOpposingParty());
            if (HeuristicAi.matches(blob, prompt) || HeuristicAi.matches(prompt, nz(c.getTitle()))) {
                hits.add("Matter " + c.getCaseNumber() + " — " + c.getTitle() + " (" + c.getStatus() + ").");
                citations.add(Map.of("type", "case", "id", c.getId().toString(), "label", c.getCaseNumber() + " " + c.getTitle()));
            }
        }
        for (Client cl : clients.findByTenantIdOrderByLastNameAsc(tid)) {
            if (HeuristicAi.matches(cl.displayName() + " " + nz(cl.getEmail()) + " " + nz(cl.getNotes()), prompt)
                    || HeuristicAi.matches(prompt, cl.displayName())) {
                hits.add("Client " + cl.displayName() + (cl.getEmail() == null ? "" : " <" + cl.getEmail() + ">") + ".");
                citations.add(Map.of("type", "client", "id", cl.getId().toString(), "label", cl.displayName()));
            }
        }
        for (Lead lead : leads.findByTenantIdOrderByCreatedAtDesc(tid)) {
            if (HeuristicAi.matches(lead.getName() + " " + nz(lead.getDescription()) + " " + nz(lead.getCaseType()), prompt)) {
                hits.add("Intake lead " + lead.getName() + " (" + lead.getStatus() + ").");
                citations.add(Map.of("type", "lead", "id", lead.getId().toString(), "label", lead.getName()));
            }
        }
        String reply;
        String lower = prompt.toLowerCase();
        if (lower.contains("conflict")) {
            reply = "Run a conflict check from Conflicts. Search the prospect's name, then opposing parties and counsel. A hit is not a hard stop — record how you cleared it.";
        } else if (lower.contains("invoice") || lower.contains("bill") || lower.contains("trust")) {
            reply = "Unbilled time lives under Time Tracking. Generate an invoice from Billing, then record a trust deposit if a retainer arrived. IOLTA ledgers refuse an overdraw.";
        } else if (hits.isEmpty()) {
            reply = "I searched this tenant's cases, clients, and intake and did not find a close match for “"
                    + prompt
                    + "”. Try a case number, a last name, or ask how to bill, conflicts, or voice calling. This assistant runs locally — no outside model and no client text leaves the firm.";
        } else {
            reply = "Here is what matches “" + prompt + "” on this docket:\n\n- " + String.join("\n- ", hits.subList(0, Math.min(8, hits.size())))
                    + "\n\nOpen the cited record for the full file. Nothing here was sent to an external AI vendor.";
        }
        audit.record("ai.chat", "ai", null, prompt.length() > 80 ? prompt.substring(0, 80) : prompt);
        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply);
        out.put("citations", citations);
        out.put("mode", "local-heuristic");
        return out;
    }

    public Map<String, Object> summarize(Map<String, Object> body) {
        if (body.get("caseId") == null) throw ApiException.badRequest("caseId is required");
        UUID caseId = UUID.fromString(String.valueOf(body.get("caseId")));
        LegalCase c = cases.findByIdAndTenantId(caseId, TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Matter not found"));
        List<String> noteBodies = notes.findByTenantIdAndCaseIdOrderByCreatedAtDesc(TenantContext.requireTenant(), caseId)
                .stream()
                .map(Note::getBody)
                .toList();
        String summary = HeuristicAi.summarize(c.getTitle(), noteBodies);
        audit.record("ai.summarize", "case", caseId.toString(), c.getTitle());
        return Map.of("caseId", caseId, "title", c.getTitle(), "summary", summary, "noteCount", noteBodies.size());
    }

    public Map<String, Object> draftEmail(Map<String, Object> body) {
        String kind = String.valueOf(body.getOrDefault("kind", "status"));
        String extra = body.get("notes") == null ? "" : String.valueOf(body.get("notes"));
        String resolvedMatter = body.get("matterTitle") == null ? null : String.valueOf(body.get("matterTitle"));
        String resolvedClient = body.get("clientName") == null ? null : String.valueOf(body.get("clientName"));
        if (body.get("caseId") != null) {
            LegalCase c = cases.findByIdAndTenantId(UUID.fromString(String.valueOf(body.get("caseId"))), TenantContext.requireTenant())
                    .orElse(null);
            if (c != null) {
                if (resolvedMatter == null) resolvedMatter = c.getTitle();
                if (resolvedClient == null && c.getClientId() != null) {
                    resolvedClient = clients.findByIdAndTenantId(c.getClientId(), TenantContext.requireTenant())
                            .map(Client::displayName)
                            .orElse(resolvedClient);
                }
            }
        }
        if (body.get("clientId") != null && resolvedClient == null) {
            resolvedClient = clients.findByIdAndTenantId(UUID.fromString(String.valueOf(body.get("clientId"))), TenantContext.requireTenant())
                    .map(Client::displayName)
                    .orElse(null);
        }
        String draft = HeuristicAi.draftEmail(kind, resolvedClient, resolvedMatter, extra);
        audit.record("ai.draft", "email", kind, kind);
        return Map.of("kind", kind, "draft", draft);
    }

    public Map<String, Object> screenIntake(Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        String caseType = body.get("caseType") == null ? "" : String.valueOf(body.get("caseType"));
        String description = body.get("description") == null ? "" : String.valueOf(body.get("description"));
        if (body.get("leadId") != null) {
            Lead lead = leads.findByIdAndTenantId(UUID.fromString(String.valueOf(body.get("leadId"))), TenantContext.requireTenant())
                    .orElseThrow(() -> ApiException.notFound("Lead not found"));
            name = lead.getName();
            caseType = lead.getCaseType();
            description = lead.getDescription();
        }
        Map<String, Object> result = HeuristicAi.screenIntake(name, caseType, description);
        audit.record("ai.screen", "intake", name, String.valueOf(result.get("band")));
        return result;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
