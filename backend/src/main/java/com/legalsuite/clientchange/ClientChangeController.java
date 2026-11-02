package com.legalsuite.clientchange;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.Ids;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClientChangeController {
    private final ClientChangeService svc;

    public ClientChangeController(ClientChangeService svc) { this.svc = svc; }

    @GetMapping("/api/v1/cases/{id}/change-client/preview")
    public ApiResponse<?> preview(@PathVariable UUID id, @RequestParam("toClientId") String to) {
        return ApiResponse.ok(svc.preview(id, Ids.optUuid(to, "client")));
    }

    @PostMapping("/api/v1/cases/{id}/change-client")
    public ApiResponse<?> change(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.change(id, body));
    }

    @GetMapping("/api/v1/cases/{id}/client-changes")
    public ApiResponse<?> history(@PathVariable UUID id) { return ApiResponse.ok(svc.history(id)); }

    @GetMapping("/api/v1/clients/{id}/merge/preview")
    public ApiResponse<?> mergePreview(@PathVariable UUID id, @RequestParam("intoClientId") String into) {
        return ApiResponse.ok(svc.mergePreview(id, Ids.optUuid(into, "client")));
    }

    @PostMapping("/api/v1/clients/{id}/merge")
    public ApiResponse<?> merge(@PathVariable UUID id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(svc.merge(id, body)); }

    @GetMapping("/api/v1/trust/client-transfers")
    public ApiResponse<?> transfers() { return ApiResponse.ok(svc.transfers()); }

    @PostMapping("/api/v1/trust/client-transfers/{id}/approve")
    public ApiResponse<?> approve(@PathVariable UUID id) { return ApiResponse.ok(svc.approveTransfer(id)); }

    @PostMapping("/api/v1/trust/client-transfers/{id}/reject")
    public ApiResponse<?> reject(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(svc.rejectTransfer(id, body == null || body.get("note") == null ? null : String.valueOf(body.get("note"))));
    }
}
