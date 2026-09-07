package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.AiService;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.IntegrationService;
import com.legalsuite.service.RetainService;
import com.legalsuite.service.SignatureService;
import com.legalsuite.service.TemplateService;
import com.legalsuite.service.UsageService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvancedControllers {
    private final TemplateService templates;
    private final SignatureService signatures;
    private final AiService ai;
    private final IntegrationService integrations;
    private final AuditService audit;
    private final RetainService retain;
    private final UsageService usage;

    public AdvancedControllers(
            TemplateService templates,
            SignatureService signatures,
            AiService ai,
            IntegrationService integrations,
            AuditService audit,
            RetainService retain,
            UsageService usage) {
        this.templates = templates;
        this.signatures = signatures;
        this.ai = ai;
        this.integrations = integrations;
        this.audit = audit;
        this.retain = retain;
        this.usage = usage;
    }

    @GetMapping("/api/v1/templates")
    public ApiResponse<?> templates() {
        return ApiResponse.ok(templates.list());
    }

    @GetMapping("/api/v1/templates/{id}")
    public ApiResponse<?> template(@PathVariable UUID id) {
        return ApiResponse.ok(templates.get(id));
    }

    @PostMapping("/api/v1/templates")
    public ApiResponse<?> createTemplate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(templates.save(null, body));
    }

    @PutMapping("/api/v1/templates/{id}")
    public ApiResponse<?> updateTemplate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(templates.save(id, body));
    }

    @PostMapping("/api/v1/templates/{id}/merge")
    public ApiResponse<?> merge(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(templates.merge(id, body == null ? Map.of() : body));
    }

    @GetMapping("/api/v1/signatures")
    public ApiResponse<?> signatureList() {
        return ApiResponse.ok(signatures.list());
    }

    @PostMapping("/api/v1/signatures")
    public ApiResponse<?> createSignature(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(signatures.create(body));
    }

    @PostMapping("/api/v1/signatures/{id}/void")
    public ApiResponse<?> voidSignature(@PathVariable UUID id) {
        return ApiResponse.ok(signatures.voidRequest(id));
    }

    @GetMapping("/api/v1/sign/{id}")
    public ApiResponse<?> publicSign(@PathVariable UUID id) {
        return ApiResponse.ok(signatures.publicView(id));
    }

    @PostMapping("/api/v1/sign/{id}")
    public ApiResponse<?> applySign(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(signatures.sign(id, body));
    }

    @PostMapping("/api/v1/ai/chat")
    public ApiResponse<?> chat(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ai.chat(body));
    }

    @PostMapping("/api/v1/ai/summarize")
    public ApiResponse<?> summarize(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ai.summarize(body));
    }

    @PostMapping("/api/v1/ai/draft-email")
    public ApiResponse<?> draft(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ai.draftEmail(body));
    }

    @PostMapping("/api/v1/ai/screen-intake")
    public ApiResponse<?> screen(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ai.screenIntake(body));
    }

    @GetMapping("/api/v1/integrations")
    public ApiResponse<?> integrations() {
        return ApiResponse.ok(integrations.list());
    }

    @PostMapping("/api/v1/integrations/{provider}/connect")
    public ApiResponse<?> connect(@PathVariable String provider) {
        return ApiResponse.ok(integrations.setConnected(provider, true));
    }

    @PostMapping("/api/v1/integrations/{provider}/disconnect")
    public ApiResponse<?> disconnect(@PathVariable String provider) {
        return ApiResponse.ok(integrations.setConnected(provider, false));
    }

    @GetMapping("/api/v1/audit")
    public ApiResponse<?> auditLog() {
        return ApiResponse.ok(audit.list());
    }

    @PostMapping("/api/v1/retain/{leadId}")
    public ApiResponse<?> retainLead(@PathVariable UUID leadId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(retain.retain(leadId, body == null ? Map.of() : body));
    }

    @GetMapping("/api/v1/usage")
    public ApiResponse<?> usagePreview() {
        return ApiResponse.ok(usage.preview());
    }

    @GetMapping("/api/v1/usage/history")
    public ApiResponse<?> usageHistory() {
        return ApiResponse.ok(usage.history());
    }

    @PostMapping("/api/v1/usage/issue")
    public ApiResponse<?> issueUsage() {
        return ApiResponse.ok(usage.issue());
    }

    @PostMapping("/api/v1/usage/{id}/pay")
    public ApiResponse<?> payUsage(@PathVariable UUID id) {
        return ApiResponse.ok(usage.pay(id));
    }
}
