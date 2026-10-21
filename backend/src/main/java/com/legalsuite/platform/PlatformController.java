package com.legalsuite.platform;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Operator (superadmin) firm management. Every change is written to the platform log and the firm's log. */
@RestController
public class PlatformController {
    private final PlatformFirmService firms;
    private final PlatformAuditService audit;

    public PlatformController(PlatformFirmService firms, PlatformAuditService audit) {
        this.firms = firms;
        this.audit = audit;
    }

    @GetMapping("/api/v1/platform/audit")
    public ApiResponse<?> platformAudit(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String action) {
        return ApiResponse.ok(audit.platformLog(tenantId, action));
    }

    @GetMapping("/api/v1/staff-activity")
    public ApiResponse<?> staffActivity() {
        return ApiResponse.ok(audit.staffActivity());
    }

    @GetMapping("/api/v1/platform/firms")
    public ApiResponse<?> firms() {
        return ApiResponse.ok(firms.list());
    }

    @GetMapping("/api/v1/platform/plans")
    public ApiResponse<?> plans() {
        return ApiResponse.ok(firms.planOptions());
    }

    @PostMapping("/api/v1/platform/firms/{id}/suspend")
    public ApiResponse<?> suspend(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.suspend(id, body), "Firm suspended");
    }

    @PostMapping("/api/v1/platform/firms/{id}/reactivate")
    public ApiResponse<?> reactivate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.reactivate(id, body), "Firm reactivated");
    }

    @PostMapping("/api/v1/platform/firms/{id}/extend-trial")
    public ApiResponse<?> extendTrial(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.extendTrial(id, body), "Trial extended");
    }

    @PostMapping("/api/v1/platform/firms/{id}/plan")
    public ApiResponse<?> plan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.changePlan(id, body), "Plan changed");
    }
}
