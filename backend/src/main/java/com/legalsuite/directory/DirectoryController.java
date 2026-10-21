package com.legalsuite.directory;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Firm-side directory settings and the operator's LPC verification queue. */
@RestController
public class DirectoryController {
    private final DirectoryService directory;

    public DirectoryController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/api/v1/directory/profile")
    public ApiResponse<?> profile() {
        return ApiResponse.ok(directory.adminView());
    }

    @PutMapping("/api/v1/directory/profile")
    public ApiResponse<?> update(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(directory.update(body), "Directory listing saved");
    }

    @PostMapping("/api/v1/directory/practitioners")
    public ApiResponse<?> addPractitioner(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(directory.addPractitioner(body), "Practitioner added. Owlaw will check the LPC number.");
    }

    @PatchMapping("/api/v1/directory/practitioners/{id}")
    public ApiResponse<?> updatePractitioner(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(directory.updatePractitioner(id, body));
    }

    @DeleteMapping("/api/v1/directory/practitioners/{id}")
    public ApiResponse<?> removePractitioner(@PathVariable UUID id) {
        return ApiResponse.ok(directory.removePractitioner(id));
    }

    @GetMapping("/api/v1/platform/directory/practitioners")
    public ApiResponse<?> queue(@RequestParam(required = false) String status) {
        return ApiResponse.ok(directory.verificationQueue(status));
    }

    @PostMapping("/api/v1/platform/directory/practitioners/{id}/verify")
    public ApiResponse<?> verify(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(directory.verify(id, body));
    }
}
