package com.legalsuite.inbox;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.domain.DocumentFile;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MatterInboxController {
    private final MatterInboxService inbox;

    public MatterInboxController(MatterInboxService inbox) {
        this.inbox = inbox;
    }

    // Staff
    @GetMapping("/api/v1/cases/{id}/inbox")
    public ApiResponse<?> thread(@PathVariable UUID id) { return ApiResponse.ok(inbox.staffThread(id)); }

    @PostMapping(value = "/api/v1/cases/{id}/inbox", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<?> send(@PathVariable UUID id, @RequestParam(value = "body", required = false) String body,
                               @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(inbox.staffSend(id, body, file));
    }

    @GetMapping("/api/v1/inbox/unread")
    public ApiResponse<?> unread() { return ApiResponse.ok(inbox.staffUnread()); }

    @GetMapping("/api/v1/cases/{id}/client-summary")
    public ApiResponse<?> summary(@PathVariable UUID id) { return ApiResponse.ok(inbox.staffSummary(id)); }

    @PutMapping("/api/v1/cases/{id}/client-note")
    public ApiResponse<?> note(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(inbox.setClientNote(id, body.get("note")));
    }

    @GetMapping("/api/v1/cases/{id}/stage-message")
    public ApiResponse<?> preview(@PathVariable UUID id, @RequestParam String stage) {
        return ApiResponse.ok(inbox.previewStageMessage(id, stage));
    }

    @PostMapping("/api/v1/clients/{id}/portal-access")
    public ApiResponse<?> portalAccess(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(inbox.setPortalAccess(id, body));
    }

    // Client portal
    @GetMapping("/api/v1/portal/cases/{id}")
    public ApiResponse<?> portalMatter(@PathVariable UUID id) { return ApiResponse.ok(inbox.portalMatter(id)); }

    @PostMapping(value = "/api/v1/portal/cases/{id}/messages", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<?> portalSend(@PathVariable UUID id, @RequestParam(value = "body", required = false) String body,
                                     @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(inbox.portalSend(id, body, file));
    }

    @GetMapping("/api/v1/portal/cases/{id}/messages/{messageId}/attachment")
    public ResponseEntity<Resource> portalAttachment(@PathVariable UUID id, @PathVariable UUID messageId) {
        DocumentFile d = inbox.portalAttachmentMeta(id, messageId);
        Resource r = inbox.portalAttachment(id, messageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.getName()).build().toString())
                .contentType(MediaType.parseMediaType(d.getMimeType() == null ? "application/octet-stream" : d.getMimeType()))
                .body(r);
    }
}
