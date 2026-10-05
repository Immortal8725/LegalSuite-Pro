package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.HealthService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthService health;

    public HealthController(HealthService health) {
        this.health = health;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> body = health.status();
        boolean up = Boolean.TRUE.equals(body.get("databaseUp"));
        ApiResponse<Map<String, Object>> payload = up
                ? ApiResponse.ok(body)
                : new ApiResponse<>(false, "database down", body, null, Instant.now());
        return ResponseEntity.status(up ? 200 : 503).body(payload);
    }
}
