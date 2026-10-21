package com.legalsuite.ops;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.HealthService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {
    private final BackupService backups;
    private final HealthService health;

    public OpsController(BackupService backups, HealthService health) {
        this.backups = backups;
        this.health = health;
    }

    /** Pulled nightly by the GitHub "Nightly backup" workflow. Needs the X-Backup-Token header. */
    @PostMapping("/api/v1/ops/backup")
    public ResponseEntity<byte[]> backup(@RequestHeader(value = "X-Backup-Token", required = false) String token) {
        if (!backups.configured()) return ResponseEntity.status(503).build();
        if (!backups.tokenMatches(token)) return ResponseEntity.status(401).build();
        byte[] file = backups.export();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=owlaw-" + LocalDate.now() + ".owlb")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file);
    }

    /** Public, no secrets: what the /status page shows. */
    @GetMapping("/api/v1/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>(health.status());
        body.put("backup", backups.lastRun());
        return ApiResponse.ok(body);
    }
}
