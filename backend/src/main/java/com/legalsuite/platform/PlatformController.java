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
    private final SupportAccessService support;
    private final PlatformBillingService billing;

    public PlatformController(PlatformFirmService firms, PlatformAuditService audit, SupportAccessService support,
            PlatformBillingService billing) {
        this.billing = billing;
        this.firms = firms;
        this.audit = audit;
        this.support = support;
    }

    @GetMapping("/api/v1/support-access")
    public ApiResponse<?> supportAccess() {
        return ApiResponse.ok(support.firmView());
    }

    @PostMapping("/api/v1/support-access")
    public ApiResponse<?> grantSupport(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(support.grant(body), "Owlaw support can now view your firm, read-only");
    }

    @PostMapping("/api/v1/support-access/{id}/revoke")
    public ApiResponse<?> revokeSupport(@PathVariable UUID id) {
        return ApiResponse.ok(support.revoke(id), "Support access revoked");
    }

    @GetMapping("/api/v1/platform/support")
    public ApiResponse<?> activeSupport() {
        return ApiResponse.ok(support.activeGrants());
    }

    @GetMapping("/api/v1/platform/support/{tenantId}/{section}")
    public ApiResponse<?> supportLook(@PathVariable UUID tenantId, @PathVariable String section) {
        return ApiResponse.ok(support.look(tenantId, section));
    }

    @GetMapping("/api/v1/platform/billing")
    public ApiResponse<?> billingOverview() {
        PlatformGuard.requireSuperadmin();
        return ApiResponse.ok(billing.overview());
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

    @PostMapping("/api/v1/platform/firms/{id}/delete")
    public ApiResponse<?> delete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.softDelete(id, body), "Firm deleted. Its data can be purged after 30 days.");
    }

    @PostMapping("/api/v1/platform/firms/{id}/restore")
    public ApiResponse<?> restore(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.restore(id, body), "Firm restored");
    }

    @PostMapping("/api/v1/platform/firms/{id}/purge")
    public ApiResponse<?> purge(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.purge(id, body), "Firm data purged");
    }

    @PostMapping("/api/v1/platform/firms/{id}/plan")
    public ApiResponse<?> plan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(firms.changePlan(id, body), "Plan changed");
    }
}
