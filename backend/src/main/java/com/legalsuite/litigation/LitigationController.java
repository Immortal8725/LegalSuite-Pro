package com.legalsuite.litigation;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class LitigationController {
    private final LitigationService service;

    public LitigationController(LitigationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/litigation/tracks")
    public ApiResponse<?> tracks() { return ApiResponse.ok(service.catalogView()); }

    @GetMapping("/api/v1/litigation/court-days")
    public ApiResponse<?> courtDays(@RequestParam String from, @RequestParam int amount,
                                    @RequestParam(required = false) String unit,
                                    @RequestParam(defaultValue = "true") boolean diesNon) {
        return ApiResponse.ok(service.courtDays(from, amount, unit, diesNon));
    }

    @GetMapping("/api/v1/litigation/pipeline")
    public ApiResponse<?> pipeline(@RequestParam(required = false) String track) { return ApiResponse.ok(service.pipeline(track)); }

    @GetMapping("/api/v1/cases/{id}/litigation")
    public ApiResponse<?> matter(@PathVariable UUID id) { return ApiResponse.ok(service.matterView(id)); }

    @PutMapping("/api/v1/cases/{id}/litigation")
    public ApiResponse<?> setTrack(@PathVariable UUID id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(service.setTrack(id, body)); }

    @PostMapping("/api/v1/cases/{id}/stages")
    public ApiResponse<?> addStage(@PathVariable UUID id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(service.addStage(id, body)); }
}
