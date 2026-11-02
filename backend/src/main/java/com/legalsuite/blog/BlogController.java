package com.legalsuite.blog;

import com.legalsuite.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
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

/** Public blog reads, and the operator's writing screen. */
@RestController
public class BlogController {
    private final BlogService blog;

    public BlogController(BlogService blog) {
        this.blog = blog;
    }

    @GetMapping("/api/v1/public/blog/posts")
    public ApiResponse<?> list(@RequestParam(required = false) String area, @RequestParam(required = false) Integer page) {
        return ApiResponse.ok(blog.publicList(area, page));
    }

    @GetMapping("/api/v1/public/blog/posts/{slug}")
    public ApiResponse<?> post(@PathVariable String slug) {
        return ApiResponse.ok(blog.publicPost(slug));
    }

    @GetMapping("/api/v1/public/blog/posts/{slug}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable String slug) {
        BlogPost b = blog.publicCover(slug);
        return image(b, blog.readCover(b), true);
    }

    @GetMapping("/api/v1/platform/blog/posts")
    public ApiResponse<?> adminList() {
        return ApiResponse.ok(blog.adminList());
    }

    @GetMapping("/api/v1/platform/blog/posts/{id}")
    public ApiResponse<?> adminGet(@PathVariable UUID id) {
        return ApiResponse.ok(blog.adminGet(id));
    }

    @PostMapping("/api/v1/platform/blog/posts")
    public ApiResponse<?> create(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(blog.create(body), "Draft saved");
    }

    @PutMapping("/api/v1/platform/blog/posts/{id}")
    public ApiResponse<?> update(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(blog.update(id, body), "Saved");
    }

    @PostMapping("/api/v1/platform/blog/posts/{id}/publish")
    public ApiResponse<?> publish(@PathVariable UUID id) {
        return ApiResponse.ok(blog.setPublished(id, true), "Published");
    }

    @PostMapping("/api/v1/platform/blog/posts/{id}/unpublish")
    public ApiResponse<?> unpublish(@PathVariable UUID id) {
        return ApiResponse.ok(blog.setPublished(id, false), "Moved back to drafts");
    }

    @DeleteMapping("/api/v1/platform/blog/posts/{id}")
    public ApiResponse<?> delete(@PathVariable UUID id) {
        blog.delete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Deleted");
    }

    @PostMapping("/api/v1/platform/blog/posts/{id}/cover")
    public ApiResponse<?> setCover(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(blog.setCover(id, file), "Cover image saved");
    }

    @DeleteMapping("/api/v1/platform/blog/posts/{id}/cover")
    public ApiResponse<?> removeCover(@PathVariable UUID id) {
        return ApiResponse.ok(blog.removeCover(id), "Cover image removed");
    }

    @GetMapping("/api/v1/platform/blog/posts/{id}/cover")
    public ResponseEntity<byte[]> adminCover(@PathVariable UUID id) {
        BlogPost b = blog.adminCover(id);
        return image(b, blog.readCover(b), false);
    }

    private static ResponseEntity<byte[]> image(BlogPost b, byte[] bytes, boolean cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(b.getCoverType() == null ? "image/jpeg" : b.getCoverType()))
                .cacheControl(cache ? CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic() : CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
