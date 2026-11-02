package com.legalsuite.milestones;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class MilestoneController {
    private final MilestoneService svc;

    public MilestoneController(MilestoneService svc) { this.svc = svc; }

    @GetMapping("/api/v1/cases/{id}/milestones")
    public ApiResponse<?> plan(@PathVariable UUID id) { return ApiResponse.ok(svc.plan(id)); }

    @PutMapping("/api/v1/cases/{id}/milestones")
    public ApiResponse<?> save(@PathVariable UUID id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(svc.savePlan(id, body)); }

    @PostMapping("/api/v1/milestones/{id}/bill")
    public ApiResponse<?> bill(@PathVariable UUID id) { return ApiResponse.ok(svc.bill(id)); }

    @PostMapping("/api/v1/milestones/{id}/post-trust")
    public ApiResponse<?> postTrust(@PathVariable UUID id) { return ApiResponse.ok(svc.retryTrust(id)); }

    @GetMapping("/api/v1/portal/milestones")
    public ApiResponse<?> portal() { return ApiResponse.ok(svc.forClient(TenantContext.requireUser())); }
}
