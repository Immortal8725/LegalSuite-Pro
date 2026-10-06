package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.PublicSiteService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicSiteController {
    private final PublicSiteService sites;

    public PublicSiteController(PublicSiteService sites) {
        this.sites = sites;
    }

    @GetMapping("/api/v1/public/sites/{slug}")
    public ApiResponse<?> publicSite(@PathVariable String slug) {
        return ApiResponse.ok(sites.publicView(slug));
    }

    @PostMapping("/api/v1/public/sites/{slug}/subscribe")
    public ApiResponse<?> subscribe(@PathVariable String slug, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(sites.subscribe(slug, body));
    }

    @GetMapping("/api/v1/public-site")
    public ApiResponse<?> mine() {
        return ApiResponse.ok(sites.adminView());
    }

    @PutMapping("/api/v1/public-site")
    public ApiResponse<?> update(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(sites.update(body == null ? Map.of() : body));
    }

    @PostMapping("/api/v1/public-site/submit")
    public ApiResponse<?> submit() {
        return ApiResponse.ok(sites.submit(), "Submitted for approval");
    }

    @GetMapping("/api/v1/platform/public-sites/queue")
    public ApiResponse<?> queue() {
        return ApiResponse.ok(sites.queue());
    }

    @PostMapping("/api/v1/platform/public-sites/{tenantId}/publish")
    public ApiResponse<?> publish(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(sites.decidePublish(tenantId, body));
    }

    @PostMapping("/api/v1/platform/public-sites/{tenantId}/branding")
    public ApiResponse<?> branding(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(sites.decideBranding(tenantId, body));
    }

    @PostMapping("/api/v1/platform/public-sites/{tenantId}/features/{key}")
    public ApiResponse<?> feature(
            @PathVariable UUID tenantId, @PathVariable String key, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(sites.decideFeature(tenantId, key, body));
    }
}
