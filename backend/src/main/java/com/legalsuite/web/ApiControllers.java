package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.CommsService;
import com.legalsuite.service.DashboardService;
import com.legalsuite.service.FinanceService;
import com.legalsuite.service.PracticeService;
import com.legalsuite.service.TenantService;
import com.legalsuite.service.TexasDocketRules;
import com.legalsuite.service.VoiceService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ApiControllers {
    private final TenantService tenants;
    private final PracticeService practice;
    private final FinanceService finance;
    private final VoiceService voice;
    private final CommsService comms;
    private final DashboardService dashboard;

    public ApiControllers(
            TenantService tenants,
            PracticeService practice,
            FinanceService finance,
            VoiceService voice,
            CommsService comms,
            DashboardService dashboard) {
        this.tenants = tenants;
        this.practice = practice;
        this.finance = finance;
        this.voice = voice;
        this.comms = comms;
        this.dashboard = dashboard;
    }

    @GetMapping("/api/v1/plans")
    public ApiResponse<?> plans() { return ApiResponse.ok(tenants.plans()); }

    @GetMapping("/api/v1/modules")
    public ApiResponse<?> modules() { return ApiResponse.ok(tenants.modules()); }

    @GetMapping("/api/v1/tenants/{id}/modules")
    public ApiResponse<?> tenantModules(@PathVariable UUID id) { return ApiResponse.ok(tenants.tenantModules(id)); }

    @PostMapping("/api/v1/tenants/{id}/modules")
    public ApiResponse<?> toggle(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID moduleId = UUID.fromString(String.valueOf(body.get("moduleId")));
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        return ApiResponse.ok(tenants.toggleModule(id, moduleId, enabled));
    }

    @PutMapping("/api/v1/tenants/me")
    public ApiResponse<?> updateFirm(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tenants.updateFirm(body));
    }

    @GetMapping("/api/v1/users")
    public ApiResponse<?> team() { return ApiResponse.ok(tenants.team()); }

    @PostMapping("/api/v1/users/invite")
    public ApiResponse<?> invite(@RequestBody Map<String, Object> body) { return ApiResponse.ok(tenants.invite(body)); }

    @PatchMapping("/api/v1/users/{id}")
    public ApiResponse<?> updateUser(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tenants.updateUser(id, body));
    }

    @GetMapping("/api/v1/clients")
    public ApiResponse<?> clients() { return ApiResponse.ok(practice.listClients()); }

    @GetMapping("/api/v1/clients/{id}")
    public ApiResponse<?> client(@PathVariable UUID id) { return ApiResponse.ok(practice.getClient(id)); }

    @PostMapping("/api/v1/clients")
    public ApiResponse<?> createClient(@RequestBody Map<String, Object> body) { return ApiResponse.ok(practice.saveClient(null, body)); }

    @PutMapping("/api/v1/clients/{id}")
    public ApiResponse<?> updateClient(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.saveClient(id, body));
    }

    @GetMapping("/api/v1/contacts")
    public ApiResponse<?> contacts() { return ApiResponse.ok(practice.contacts()); }

    @PostMapping("/api/v1/contacts")
    public ApiResponse<?> createContact(@RequestBody Map<String, Object> body) { return ApiResponse.ok(practice.saveContact(body)); }

    @GetMapping("/api/v1/cases")
    public ApiResponse<?> cases() { return ApiResponse.ok(practice.listCases()); }

    @GetMapping("/api/v1/cases/{id}")
    public ApiResponse<?> oneCase(@PathVariable String id) { return ApiResponse.ok(practice.getCase(id)); }

    @PostMapping("/api/v1/cases")
    public ApiResponse<?> createCase(@RequestBody Map<String, Object> body) { return ApiResponse.ok(practice.saveCase(null, body)); }

    @PutMapping("/api/v1/cases/{id}")
    public ApiResponse<?> updateCase(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.saveCase(id, body));
    }

    @PatchMapping("/api/v1/cases/{id}/status")
    public ApiResponse<?> caseStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(practice.changeCaseStatus(id, body.get("status")));
    }

    @PostMapping("/api/v1/cases/{id}/notes")
    public ApiResponse<?> addNote(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.addNote(id, body));
    }

    @GetMapping("/api/v1/documents")
    public ApiResponse<?> documents() { return ApiResponse.ok(practice.documents()); }

    @PostMapping("/api/v1/documents/upload")
    public ApiResponse<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caseId", required = false) UUID caseId,
            @RequestParam(value = "clientId", required = false) UUID clientId,
            @RequestParam(value = "category", required = false) String category) throws Exception {
        return ApiResponse.ok(practice.upload(file, caseId, clientId, category));
    }

    @GetMapping("/api/v1/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        var meta = practice.documentMeta(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(practice.download(id));
    }

    @GetMapping("/api/v1/events")
    public ApiResponse<?> events() {
        Instant from = Instant.now().minus(40, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(40, ChronoUnit.DAYS);
        return ApiResponse.ok(practice.eventsBetween(from, to));
    }

    @GetMapping("/api/v1/events/upcoming")
    public ApiResponse<?> upcoming() { return ApiResponse.ok(practice.upcomingEvents()); }

    @PostMapping("/api/v1/events")
    public ApiResponse<?> createEvent(@RequestBody Map<String, Object> body) { return ApiResponse.ok(practice.saveEvent(body)); }

    @GetMapping("/api/v1/tasks")
    public ApiResponse<?> tasks() { return ApiResponse.ok(practice.listTasks()); }

    @PostMapping("/api/v1/tasks")
    public ApiResponse<?> createTask(@RequestBody Map<String, Object> body) { return ApiResponse.ok(practice.saveTask(null, body)); }

    @PutMapping("/api/v1/tasks/{id}")
    public ApiResponse<?> updateTask(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.saveTask(id, body));
    }

    @PatchMapping("/api/v1/tasks/{id}/status")
    public ApiResponse<?> taskStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.saveTask(id, body));
    }

    @GetMapping("/api/v1/time-entries")
    public ApiResponse<?> time() { return ApiResponse.ok(finance.timeEntries()); }

    @PostMapping("/api/v1/time-entries")
    public ApiResponse<?> logTime(@RequestBody Map<String, Object> body) { return ApiResponse.ok(finance.logTime(body)); }

    @PostMapping("/api/v1/timers/start")
    public ApiResponse<?> startTimer(@RequestBody(required = false) Map<String, Object> body) {
        UUID caseId = body != null && body.get("caseId") != null ? UUID.fromString(String.valueOf(body.get("caseId"))) : null;
        return ApiResponse.ok(finance.startTimer(caseId));
    }

    @GetMapping("/api/v1/timers/active")
    public ApiResponse<?> activeTimer() { return ApiResponse.ok(finance.activeTimer()); }

    @PostMapping("/api/v1/timers/stop")
    public ApiResponse<?> stopTimer(@RequestBody(required = false) Map<String, Object> body) {
        String desc = body == null || body.get("description") == null ? null : String.valueOf(body.get("description"));
        return ApiResponse.ok(finance.stopTimer(desc));
    }

    @GetMapping("/api/v1/invoices")
    public ApiResponse<?> invoices() { return ApiResponse.ok(finance.invoices()); }

    @GetMapping("/api/v1/invoices/{id}")
    public ApiResponse<?> invoice(@PathVariable UUID id) { return ApiResponse.ok(finance.getInvoice(id)); }

    @PostMapping("/api/v1/invoices/generate")
    public ApiResponse<?> generate(@RequestBody Map<String, Object> body) {
        UUID clientId = UUID.fromString(String.valueOf(body.get("clientId")));
        UUID caseId = body.get("caseId") == null ? null : UUID.fromString(String.valueOf(body.get("caseId")));
        return ApiResponse.ok(finance.generateInvoice(clientId, caseId));
    }

    @PostMapping("/api/v1/invoices/{id}/pay")
    public ApiResponse<?> pay(@PathVariable UUID id) {
        return ApiResponse.ok(finance.updateInvoice(id, Map.of("status", "paid")));
    }

    @PostMapping("/api/v1/invoices/{id}/send")
    public ApiResponse<?> sendInv(@PathVariable UUID id) {
        return ApiResponse.ok(finance.updateInvoice(id, Map.of("status", "sent")));
    }

    @GetMapping("/api/v1/expenses")
    public ApiResponse<?> expenses() { return ApiResponse.ok(finance.expenses()); }

    @PostMapping("/api/v1/expenses")
    public ApiResponse<?> addExpense(@RequestBody Map<String, Object> body) { return ApiResponse.ok(finance.addExpense(body)); }

    @GetMapping("/api/v1/trust/accounts")
    public ApiResponse<?> trust() { return ApiResponse.ok(finance.trustAccounts()); }

    @GetMapping("/api/v1/trust/accounts/{id}/ledger")
    public ApiResponse<?> ledger(@PathVariable UUID id) { return ApiResponse.ok(finance.trustLedger(id)); }

    @PostMapping("/api/v1/trust/move")
    public ApiResponse<?> trustMove(@RequestBody Map<String, Object> body) { return ApiResponse.ok(finance.trustMove(body)); }

    @GetMapping("/api/v1/calls")
    public ApiResponse<?> calls() { return ApiResponse.ok(voice.history()); }

    @PostMapping("/api/v1/calls/initiate")
    public ApiResponse<?> initiate(@RequestBody Map<String, Object> body) { return ApiResponse.ok(voice.initiate(body)); }

    @PostMapping("/api/v1/calls/{id}/answer")
    public ApiResponse<?> answer(@PathVariable UUID id) { return ApiResponse.ok(voice.answer(id)); }

    @PostMapping("/api/v1/calls/{id}/end")
    public ApiResponse<?> end(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(voice.end(id, body));
    }

    @GetMapping("/api/v1/calls/registry")
    public ApiResponse<?> registry() { return ApiResponse.ok(voice.registry()); }

    @GetMapping("/api/v1/voice/ice-servers")
    public ApiResponse<?> ice() { return ApiResponse.ok(voice.iceServers()); }

    @GetMapping("/api/v1/voice/ethics")
    public ApiResponse<?> voiceEthics() { return ApiResponse.ok(voice.ethics()); }

    @GetMapping("/api/v1/docket")
    public ApiResponse<?> docket() {
        return ApiResponse.ok(dashboard.docket(com.legalsuite.common.TenantContext.requireTenant()));
    }

    @PostMapping("/api/v1/docket/preview")
    public ApiResponse<?> docketPreview(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(TexasDocketRules.compute(TexasDocketRules.Facts.from(body)).asMap());
    }

    @GetMapping("/api/v1/conversations")
    public ApiResponse<?> convos() { return ApiResponse.ok(comms.conversations()); }

    @PostMapping("/api/v1/conversations")
    public ApiResponse<?> createConvo(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(comms.createConversation(body));
    }

    @PostMapping("/api/v1/messages")
    public ApiResponse<?> send(@RequestBody Map<String, Object> body) {
        UUID cid = body.get("conversationId") == null ? new UUID(0, 0) : UUID.fromString(String.valueOf(body.get("conversationId")));
        return ApiResponse.ok(comms.sendMessage(cid, String.valueOf(body.get("body"))));
    }

    @GetMapping("/api/v1/notifications")
    public ApiResponse<?> notifs() { return ApiResponse.ok(comms.notifications()); }

    @PatchMapping("/api/v1/notifications/{id}/read")
    public ApiResponse<?> read(@PathVariable UUID id) {
        comms.markRead(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/v1/landing/{slug}")
    public ApiResponse<?> landing(@PathVariable String slug) { return ApiResponse.ok(comms.publicLanding(slug)); }

    @PostMapping("/api/v1/intake/{slug}")
    public ApiResponse<?> intake(@PathVariable String slug, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(comms.intake(slug, body));
    }

    @GetMapping("/api/v1/intake/leads")
    public ApiResponse<?> leads() { return ApiResponse.ok(comms.leads()); }

    @PatchMapping("/api/v1/intake/leads/{id}")
    public ApiResponse<?> updateLead(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(comms.updateLead(id, body));
    }

    @PostMapping("/api/v1/conflicts/check")
    public ApiResponse<?> conflict(@RequestBody Map<String, Object> body) {
        Object name = body.get("name");
        return ApiResponse.ok(comms.conflictCheck(name == null ? "" : String.valueOf(name), body));
    }

    @GetMapping("/api/v1/conflicts")
    public ApiResponse<?> conflictHistory() { return ApiResponse.ok(comms.conflictHistory()); }

    @PostMapping("/api/v1/conflicts/{id}/clear")
    public ApiResponse<?> clearConflict(@PathVariable UUID id) {
        return ApiResponse.ok(comms.clearConflict(id));
    }

    @GetMapping("/api/v1/dashboard")
    public ApiResponse<?> dash() { return ApiResponse.ok(dashboard.overview()); }

    @GetMapping("/api/v1/reports")
    public ApiResponse<?> reports() { return ApiResponse.ok(dashboard.reports()); }

    @GetMapping("/api/v1/search")
    public ApiResponse<?> search(@RequestParam("q") String q) {
        String needle = q.toLowerCase();
        var cases = practice.listCases().stream()
                .filter(c -> String.valueOf(c.get("title")).toLowerCase().contains(needle)
                        || String.valueOf(c.get("caseNumber")).toLowerCase().contains(needle))
                .toList();
        var clients = practice.listClients().stream()
                .filter(c -> String.valueOf(c.get("displayName")).toLowerCase().contains(needle)
                        || String.valueOf(c.get("email")).toLowerCase().contains(needle))
                .toList();
        return ApiResponse.ok(Map.of("cases", cases, "clients", clients, "query", q));
    }

    @GetMapping("/api/v1/portal/cases")
    public ApiResponse<?> portalCases() {
        return ApiResponse.ok(comms.portalCases(
                com.legalsuite.common.TenantContext.requireUser(),
                com.legalsuite.common.TenantContext.requireTenant()));
    }

    @GetMapping("/api/v1/portal/invoices")
    public ApiResponse<?> portalInvoices() {
        return ApiResponse.ok(finance.invoicesForClient(com.legalsuite.common.TenantContext.requireUser()));
    }

    @PostMapping("/api/v1/voice/signal")
    public ApiResponse<?> pushSignal(@RequestBody Map<String, Object> body) {
        voice.pushSignal(body);
        return ApiResponse.ok(Map.of("queued", true));
    }

    @GetMapping("/api/v1/voice/inbox")
    public ApiResponse<?> drainSignals() {
        return ApiResponse.ok(voice.drainInbox());
    }
}
