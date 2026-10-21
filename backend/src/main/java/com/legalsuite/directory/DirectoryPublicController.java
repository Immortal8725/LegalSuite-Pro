package com.legalsuite.directory;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public Find a Lawyer search, firm pages and enquiries, plus the operator's listing controls. */
@RestController
public class DirectoryPublicController {
    private final DirectoryPublicService directory;

    public DirectoryPublicController(DirectoryPublicService directory) {
        this.directory = directory;
    }

    @GetMapping("/api/v1/public/directory/search")
    public ApiResponse<?> search(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer page) {
        return ApiResponse.ok(directory.search(area, province, city, language, page));
    }

    @GetMapping("/api/v1/public/directory/catalog")
    public ApiResponse<?> catalog() {
        return ApiResponse.ok(DirectoryCatalog.asMap());
    }

    @GetMapping("/api/v1/public/directory/firms/{slug}")
    public ApiResponse<?> firm(@PathVariable String slug) {
        return ApiResponse.ok(directory.firm(slug));
    }

    @PostMapping("/api/v1/public/directory/firms/{slug}/enquiries")
    public ApiResponse<?> enquire(@PathVariable String slug, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(directory.enquire(slug, body));
    }

    @GetMapping("/api/v1/directory/enquiries/{leadId}")
    public ApiResponse<?> enquiry(@PathVariable UUID leadId) {
        return ApiResponse.ok(directory.enquiryFor(leadId));
    }

    @GetMapping("/api/v1/platform/directory/listings")
    public ApiResponse<?> listings() {
        return ApiResponse.ok(directory.operatorListings());
    }

    @PostMapping("/api/v1/platform/directory/listings/{tenantId}/visibility")
    public ApiResponse<?> visibility(@PathVariable UUID tenantId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(directory.setHidden(tenantId, body));
    }
}
