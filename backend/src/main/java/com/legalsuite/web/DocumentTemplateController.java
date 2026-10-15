package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.docgen.BrandingService;
import com.legalsuite.docgen.DocumentGenerationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Firm letterhead branding and the branded document template library. */
@RestController
public class DocumentTemplateController {
    private final BrandingService branding;
    private final DocumentGenerationService generation;

    public DocumentTemplateController(BrandingService branding, DocumentGenerationService generation) {
        this.branding = branding;
        this.generation = generation;
    }

    @GetMapping("/api/v1/firm/branding")
    public ApiResponse<?> branding() {
        return ApiResponse.ok(branding.view());
    }

    @PutMapping("/api/v1/firm/branding")
    public ApiResponse<?> updateBranding(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(branding.update(body), "Letterhead saved");
    }

    @PostMapping("/api/v1/firm/branding/logo")
    public ApiResponse<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(branding.uploadLogo(file), "Logo uploaded");
    }

    @GetMapping("/api/v1/firm/branding/logo")
    public ResponseEntity<byte[]> logo() {
        var logo = branding.logo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getKey()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(logo.getValue());
    }

    @GetMapping("/api/v1/doc-templates")
    public ApiResponse<?> library() {
        return ApiResponse.ok(generation.library());
    }

    @GetMapping("/api/v1/doc-templates/fields")
    public ApiResponse<?> fieldReference() {
        return ApiResponse.ok(generation.fieldReference());
    }

    @GetMapping("/api/v1/doc-templates/{id}/form")
    public ApiResponse<?> form(@PathVariable String id, @RequestParam(value = "caseId", required = false) UUID caseId) {
        return ApiResponse.ok(generation.form(id, caseId));
    }

    @PostMapping("/api/v1/doc-templates/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(generation.preview(id, body == null ? Map.of() : body));
    }

    @PostMapping("/api/v1/doc-templates/{id}/generate")
    public ApiResponse<?> generate(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(generation.generate(id, body == null ? Map.of() : body), "Document generated");
    }

    @PostMapping("/api/v1/doc-templates/upload")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "name", required = false) String name,
                                 @RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "description", required = false) String description) {
        return ApiResponse.ok(generation.upload(file, name, category, description), "Template uploaded");
    }

    @DeleteMapping("/api/v1/doc-templates/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        return ApiResponse.ok(generation.deleteFirmTemplate(id), "Template removed");
    }
}
