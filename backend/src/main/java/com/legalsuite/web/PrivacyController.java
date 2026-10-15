package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.TenantContext;
import com.legalsuite.service.PrivacyService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** POPIA access and deletion requests for portal clients, firm users and firm admins. */
@RestController
public class PrivacyController {
    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    private static String reason(Map<String, Object> body) {
        return body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
    }

    private static ResponseEntity<?> export(Object data, byte[] zip, String name) {
        if (zip != null) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".zip\"").body(zip);
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".json\"")
                .body(ApiResponse.ok(data));
    }

    // Portal client: their own data only (the client id comes from the portal token).
    @GetMapping("/api/v1/portal/privacy/export")
    public ResponseEntity<?> portalExport(@RequestParam(defaultValue = "json") String format) throws IOException {
        UUID me = TenantContext.requireUser();
        return "zip".equalsIgnoreCase(format) ? export(null, privacy.exportClientZip(me), "my-personal-information")
                : export(privacy.exportClient(me), null, "my-personal-information");
    }

    @PostMapping("/api/v1/portal/privacy/erasure")
    public ApiResponse<?> portalErasure(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(privacy.requestErasure("client", TenantContext.requireUser(), reason(body)), "Request sent to your law firm");
    }

    @GetMapping("/api/v1/portal/privacy/requests")
    public ApiResponse<?> portalRequests() {
        return ApiResponse.ok(privacy.myRequests("client", TenantContext.requireUser()));
    }

    // Firm user: their own data.
    @GetMapping("/api/v1/privacy/me/export")
    public ResponseEntity<?> myExport() {
        return export(privacy.exportUser(TenantContext.requireUser()), null, "my-personal-information");
    }

    @PostMapping("/api/v1/privacy/me/erasure")
    public ApiResponse<?> myErasure(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(privacy.requestErasure("user", TenantContext.requireUser(), reason(body)), "Request sent to the firm admin");
    }

    @GetMapping("/api/v1/privacy/me/requests")
    public ApiResponse<?> myRequests() {
        return ApiResponse.ok(privacy.myRequests("user", TenantContext.requireUser()));
    }

    // Firm admin: act for a client who asks by phone or email, and decide requests.
    @GetMapping("/api/v1/privacy/clients/{id}/export")
    public ResponseEntity<?> clientExport(@PathVariable UUID id, @RequestParam(defaultValue = "json") String format) throws IOException {
        com.legalsuite.common.Roles.requireTenantAdmin("Only a firm admin can export a client's personal information.");
        return "zip".equalsIgnoreCase(format) ? export(null, privacy.exportClientZip(id), "client-personal-information")
                : export(privacy.exportClient(id), null, "client-personal-information");
    }

    @PostMapping("/api/v1/privacy/clients/{id}/erasure")
    public ApiResponse<?> clientErasure(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        com.legalsuite.common.Roles.requireTenantAdmin("Only a firm admin can log a deletion request for a client.");
        return ApiResponse.ok(privacy.requestErasure("client", id, reason(body)), "Request logged");
    }

    @GetMapping("/api/v1/privacy/requests")
    public ApiResponse<?> requests() {
        return ApiResponse.ok(privacy.listRequests());
    }

    @PostMapping("/api/v1/privacy/requests/{id}/approve")
    public ApiResponse<?> approve(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(privacy.decide(id, true, body == null ? null : (String) body.get("note")), "Anonymised");
    }

    @PostMapping("/api/v1/privacy/requests/{id}/reject")
    public ApiResponse<?> reject(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(privacy.decide(id, false, body == null ? null : (String) body.get("note")), "Refused");
    }
}
