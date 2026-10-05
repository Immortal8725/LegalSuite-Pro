package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppModule;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.TenantModule;
import com.legalsuite.repo.AppModuleRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.TenantModuleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    static final String LOCAL_NOTICE =
            "On-tenant assistant. This prompt was not sent to a model vendor.";

    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final NoteRepository notes;
    private final LeadRepository leads;
    private final DocumentFileRepository documents;
    private final AppModuleRepository modules;
    private final TenantModuleRepository tenantModules;
    private final AuditService audit;
    private final AiModelGateway models;

    public AiService(
            LegalCaseRepository cases,
            ClientRepository clients,
            NoteRepository notes,
            LeadRepository leads,
            DocumentFileRepository documents,
            AppModuleRepository modules,
            TenantModuleRepository tenantModules,
            AuditService audit,
            AiModelGateway models) {
        this.cases = cases;
        this.clients = clients;
        this.notes = notes;
        this.leads = leads;
        this.documents = documents;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.audit = audit;
        this.models = models;
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        requireStaffAssistant();
        String prompt = String.valueOf(body.getOrDefault("prompt", "")).trim();
        if (prompt.isBlank()) {
            throw ApiException.badRequest("Ask a question about this firm's docket");
        }
        Object caseRaw = body.get("caseId");
        if (caseRaw != null && !String.valueOf(caseRaw).isBlank()) {
            return matterChat(prompt, String.valueOf(caseRaw).trim());
        }
        return docketChat(prompt);
    }

    public Map<String, Object> summarize(Map<String, Object> body) {
        requireStaffAssistant();
        if (body.get("caseId") == null) throw ApiException.badRequest("caseId is required");
        UUID caseId = parseCaseId(String.valueOf(body.get("caseId")));
        LegalCase c = cases.findByIdAndTenantId(caseId, TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Matter not found"));
        List<String> noteBodies = notes.findByTenantIdAndCaseIdOrderByCreatedAtDesc(TenantContext.requireTenant(), caseId)
                .stream()
                .map(Note::getBody)
                .toList();
        String summary = HeuristicAi.summarize(c.getTitle(), noteBodies);
        audit.record("ai.summarize", "case", caseId.toString(), c.getTitle());
        return Map.of(
                "caseId", caseId,
                "title", c.getTitle(),
                "summary", summary,
                "noteCount", noteBodies.size(),
                "assistive", true,
                "disclaimer", MatterAssistant.DISCLAIMER);
    }

    public Map<String, Object> draftEmail(Map<String, Object> body) {
        requireStaffAssistant();
        String kind = String.valueOf(body.getOrDefault("kind", "status"));
        String extra = body.get("notes") == null ? "" : String.valueOf(body.get("notes"));
        String resolvedMatter = body.get("matterTitle") == null ? null : String.valueOf(body.get("matterTitle"));
        String resolvedClient = body.get("clientName") == null ? null : String.valueOf(body.get("clientName"));
        if (body.get("caseId") != null && !String.valueOf(body.get("caseId")).isBlank()) {
            LegalCase c = cases.findByIdAndTenantId(parseCaseId(String.valueOf(body.get("caseId"))), TenantContext.requireTenant())
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
        return Map.of("kind", kind, "draft", draft, "assistive", true, "disclaimer", MatterAssistant.DISCLAIMER);
    }

    public Map<String, Object> screenIntake(Map<String, Object> body) {
        requireStaffAssistant();
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
        Map<String, Object> result = new HashMap<>(HeuristicAi.screenIntake(name, caseType, description));
        result.put("assistive", true);
        result.put("disclaimer", MatterAssistant.DISCLAIMER);
        audit.record("ai.screen", "intake", name, String.valueOf(result.get("band")));
        return result;
    }

    private Map<String, Object> matterChat(String prompt, String rawId) {
        UUID caseId = parseCaseId(rawId);
        UUID tenantId = TenantContext.requireTenant();
        LegalCase matter = cases.findByIdAndTenantId(caseId, tenantId).orElse(null);
        if (matter == null) {
            audit.record("ai.chat.denied", "case", rawId, "not on this tenant");
            throw ApiException.notFound("Matter not found");
        }
        MatterPacket packet = packet(matter);
        AiModelGateway.Completion completion = models.complete(MatterAssistant.systemPrompt(packet), prompt);
        String reply;
        String mode;
        String notice;
        if (completion.status() == AiModelGateway.Status.CONFIGURED && completion.text() != null && !completion.text().isBlank()) {
            reply = withDisclaimer(MatterAssistant.guardCitations(completion.text(), packet));
            mode = completion.provider();
            notice = completion.notice();
        } else {
            reply = MatterAssistant.answer(prompt, packet);
            mode = "local-heuristic";
            notice = completion.notice() == null ? LOCAL_NOTICE : completion.notice();
        }
        audit.record("ai.chat", "case", caseId.toString(), clip(prompt, 80));
        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply);
        out.put("citations", citations(packet));
        out.put("mode", mode);
        out.put("providerStatus", completion.status().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        out.put("notice", notice);
        out.put("assistive", true);
        out.put("disclaimer", MatterAssistant.DISCLAIMER);
        out.put("grounded", true);
        out.put("matter", Map.of(
                "id", matter.getId().toString(),
                "caseNumber", nz(matter.getCaseNumber()),
                "title", nz(matter.getTitle())));
        return out;
    }

    private Map<String, Object> docketChat(String prompt) {
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
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("conflict")) {
            reply = "Run a conflict check from Conflicts. Search the prospect's name, then opposing parties and counsel. A hit is not a hard stop — record how you cleared it.";
        } else if (lower.contains("invoice") || lower.contains("bill") || lower.contains("trust")) {
            reply = "Unbilled time lives under Time Tracking. Generate an invoice from Billing, then record a trust deposit if a retainer arrived. A client ledger refuses another client's money.";
        } else if (hits.isEmpty()) {
            reply = "I searched this tenant's matters, clients, and intake and did not find a close match for “"
                    + prompt
                    + "”. Open a matter and ask there, or try a case number or a last name.";
        } else {
            reply = "Here is what matches “" + prompt + "” on this docket:\n\n- "
                    + String.join("\n- ", hits.subList(0, Math.min(8, hits.size())))
                    + "\n\nOpen the matter to ask about its clocks, notes, and file names.";
        }
        reply = reply + "\n\n" + MatterAssistant.DISCLAIMER;
        audit.record("ai.chat", "ai", null, clip(prompt, 80));
        AiModelGateway.Completion completion = models.peek();
        String notice = LOCAL_NOTICE;
        String providerStatus = "local";
        if (completion.status() == AiModelGateway.Status.MISSING_KEY
                || completion.status() == AiModelGateway.Status.CONFIGURED
                || completion.status() == AiModelGateway.Status.VENDOR_ERROR) {
            notice = "Docket search stays on the tenant. Open a matter to ask with that file’s clocks and notes."
                    + (completion.notice() == null ? "" : " " + completion.notice());
            providerStatus = completion.status().name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply);
        out.put("citations", citations);
        out.put("mode", "local-heuristic");
        out.put("providerStatus", providerStatus);
        out.put("notice", notice);
        out.put("assistive", true);
        out.put("disclaimer", MatterAssistant.DISCLAIMER);
        out.put("grounded", false);
        return out;
    }

    private MatterPacket packet(LegalCase c) {
        UUID tenantId = c.getTenantId();
        String clientName = null;
        if (c.getClientId() != null) {
            clientName = clients.findByIdAndTenantId(c.getClientId(), tenantId).map(Client::displayName).orElse(null);
        }
        List<MatterPacket.ClockLine> clocks = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Map<String, Object> clock : JsonLists.objects(c.getDocketClocksJson())) {
            String date = clock.get("date") == null ? "" : String.valueOf(clock.get("date"));
            LocalDate parsed = TexasDocketRules.parseDate(clock.get("date"));
            boolean overdue = parsed != null && parsed.isBefore(today);
            clocks.add(new MatterPacket.ClockLine(
                    str(clock.get("kind")),
                    str(clock.get("title")),
                    date,
                    str(clock.get("citation")),
                    str(clock.get("reason")),
                    overdue));
        }
        List<MatterPacket.NoteLine> noteLines = new ArrayList<>();
        for (Note note : notes.findByTenantIdAndCaseIdOrderByCreatedAtDesc(tenantId, c.getId())) {
            if (noteLines.size() >= 8) break;
            noteLines.add(new MatterPacket.NoteLine(nz(note.getTitle()), clip(nz(note.getBody()), 600)));
        }
        List<MatterPacket.FileLine> files = new ArrayList<>();
        for (DocumentFile doc : documents.findByTenantIdAndCaseId(tenantId, c.getId())) {
            String name = doc.getName() == null || doc.getName().isBlank() ? doc.getOriginalName() : doc.getName();
            files.add(new MatterPacket.FileLine(
                    doc.getId() == null ? "" : doc.getId().toString(),
                    nz(name),
                    nz(doc.getCategory()),
                    doc.isPrivileged()));
        }
        String hold = PracticeService.docketHoldReason(c);
        return new MatterPacket(
                c.getId().toString(),
                nz(c.getCaseNumber()),
                nz(c.getTitle()),
                nz(c.getStatus()),
                nz(c.getPracticeArea()),
                nz(c.getCaseType()),
                nz(c.getDescription()),
                clientName,
                nz(c.getOpposingParty()),
                nz(c.getOpposingCounsel()),
                nz(c.getCourtName()),
                nz(c.getJudgeName()),
                c.isAppearanceAuthorized(),
                nz(c.getEngagementStatus()),
                hold != null,
                hold,
                clocks,
                noteLines,
                files);
    }

    private List<Map<String, String>> citations(MatterPacket packet) {
        List<Map<String, String>> out = new ArrayList<>();
        for (MatterPacket.ClockLine clock : packet.clocks()) {
            String label = (clock.citation() == null || clock.citation().isBlank() ? clock.title() : clock.citation())
                    + (clock.date() == null || clock.date().isBlank() ? "" : " · " + clock.date());
            out.add(Map.of("type", "clock", "id", packet.caseId(), "label", label));
        }
        for (MatterPacket.FileLine file : packet.files()) {
            out.add(Map.of("type", "document", "id", file.id(), "label", file.name()));
        }
        return out;
    }

    private void requireStaffAssistant() {
        String role = TenantContext.getRole();
        if (role == null || role.isBlank()) {
            throw ApiException.unauthorized("Sign in as firm staff to use the assistant");
        }
        if ("client".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("The assistant is for firm staff. Client portal accounts cannot open matter notes.");
        }
        UUID tenantId = TenantContext.requireTenant();
        AppModule ai = modules.findBySlug("ai").orElse(null);
        if (ai == null) return;
        boolean enabled = tenantModules.findByTenantIdAndModuleId(tenantId, ai.getId())
                .map(TenantModule::isEnabled)
                .orElse(false);
        if (!enabled) {
            throw ApiException.forbidden("The AI assistant add-on is off for this firm.");
        }
    }

    private static UUID parseCaseId(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("That matter id is not valid");
        }
    }

    private static String withDisclaimer(String reply) {
        String text = reply == null ? "" : reply.trim();
        if (!text.toLowerCase(Locale.ROOT).contains("attorney remains responsible")) {
            text = text + "\n\n" + MatterAssistant.DISCLAIMER;
        }
        return text;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max - 1) + "…";
    }
}
