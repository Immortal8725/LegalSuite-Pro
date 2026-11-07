package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.PublicSitePhotoService;
import com.legalsuite.service.PublicSiteService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
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
    private final PublicSitePhotoService photos;

    public PublicSiteController(PublicSiteService sites, PublicSitePhotoService photos) {
        this.sites = sites;
        this.photos = photos;
    }

    @GetMapping("/api/v1/public/sites/{slug}/photos/{slot}")
    public ResponseEntity<byte[]> livePhoto(@PathVariable String slug, @PathVariable String slot,
                                            @RequestParam(value = "v", required = false) Long version) {
        var photo = photos.live(sites.publishedTenantId(slug), slot);
        boolean immutable = version != null && version.equals(photo.version());
        CacheControl cache = CacheControl.maxAge(PublicSitePhotoService.liveCacheAge(version, photo.version())).cachePublic();
        if (immutable) cache = cache.immutable();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.type()))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }

    @PostMapping("/api/v1/public-site/photos/{slot}")
    public ApiResponse<?> uploadPhoto(@PathVariable String slot, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(photos.upload(slot, file), "Photograph saved. Submit for approval to put it on the site.");
    }

    @DeleteMapping("/api/v1/public-site/photos/{slot}")
    public ApiResponse<?> removePhoto(@PathVariable String slot) {
        return ApiResponse.ok(photos.remove(slot), "Photograph removed from the draft");
    }

    @GetMapping("/api/v1/public-site/photos/{slot}")
    public ResponseEntity<byte[]> draftPhoto(@PathVariable String slot) {
        var photo = photos.draft(slot);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getKey()))
                .cacheControl(CacheControl.noStore())
                .body(photo.getValue());
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
