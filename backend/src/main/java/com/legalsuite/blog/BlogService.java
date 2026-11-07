package com.legalsuite.blog;

import com.legalsuite.common.ApiException;
import com.legalsuite.directory.DirectoryCatalog;
import com.legalsuite.platform.PlatformGuard;
import com.legalsuite.service.AuthService;
import com.legalsuite.service.PublicContentCache;
import com.legalsuite.storage.FileStorage;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Blog posts: the operator writes them, the public reads published ones only. */
@Service
public class BlogService {
    static final int PAGE_SIZE = 10;
    static final long MAX_COVER_BYTES = 2L * 1024 * 1024;
    static final Set<String> COVER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final BlogPostRepository posts;
    private final FileStorage storage;
    private final PublicContentCache cache;

    public BlogService(BlogPostRepository posts, FileStorage storage, PublicContentCache cache) {
        this.posts = posts;
        this.storage = storage;
        this.cache = cache;
    }

    // ---------- public ----------

    @Transactional(readOnly = true)
    public Map<String, Object> publicList(String area, Integer page) {
        int p = page == null || page < 1 ? 1 : page;
        String areaKey = area == null ? "" : area.trim().toLowerCase();
        return cache.get("blog:list:" + areaKey + ":" + p, () -> loadPublicList(area, page));
    }

    private Map<String, Object> loadPublicList(String area, Integer page) {
        int p = page == null || page < 1 ? 1 : page;
        PageRequest req = PageRequest.of(p - 1, PAGE_SIZE);
        Page<BlogPost> rows = area == null || area.isBlank()
                ? posts.findByStatusOrderByPublishedAtDesc("published", req)
                : posts.findByStatusAndPracticeAreaOrderByPublishedAtDesc("published", area.trim(), req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", rows.getContent().stream().map(b -> view(b, false)).toList());
        out.put("page", p);
        out.put("pageSize", PAGE_SIZE);
        out.put("total", rows.getTotalElements());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicPost(String slug) {
        String key = "blog:post:" + (slug == null ? "" : slug.trim().toLowerCase());
        return cache.get(key, () -> view(publishedOr404(slug), true));
    }

    @Transactional(readOnly = true)
    public BlogPost publicCover(String slug) {
        BlogPost b = publishedOr404(slug);
        if (b.getCoverRef() == null) throw ApiException.notFound("No cover image");
        return b;
    }

    public byte[] readCover(BlogPost b) {
        try {
            return storage.read(b.getCoverRef());
        } catch (IOException e) {
            throw ApiException.notFound("No cover image");
        }
    }

    private BlogPost publishedOr404(String slug) {
        BlogPost b = posts.findBySlug(slug == null ? "" : slug.trim().toLowerCase()).orElse(null);
        if (b == null || !"published".equals(b.getStatus())) throw ApiException.notFound("Post not found");
        return b;
    }

    // ---------- operator ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList() {
        PlatformGuard.requireSuperadmin();
        return posts.findAllByOrderByUpdatedAtDesc().stream().map(b -> view(b, false)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGet(UUID id) {
        PlatformGuard.requireSuperadmin();
        return view(find(id), true);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = new BlogPost();
        b.setId(UUID.randomUUID());
        b.setCreatedAt(Instant.now());
        b.setStatus("draft");
        apply(b, body == null ? Map.of() : body, true);
        cache.evictBlog();
        return view(posts.save(b), true);
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        apply(b, body == null ? Map.of() : body, false);
        cache.evictBlog();
        return view(posts.save(b), true);
    }

    @Transactional
    public Map<String, Object> setPublished(UUID id, boolean publish) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        if (publish) {
            if (b.getBody() == null || b.getBody().isBlank()) throw ApiException.badRequest("Write the post before you publish it.");
            b.setStatus("published");
            if (b.getPublishedAt() == null) b.setPublishedAt(Instant.now());
        } else {
            b.setStatus("draft");
        }
        b.setUpdatedAt(Instant.now());
        cache.evictBlog();
        return view(posts.save(b), true);
    }

    @Transactional
    public void delete(UUID id) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        if (b.getCoverRef() != null) storage.delete(b.getCoverRef());
        posts.delete(b);
        cache.evictBlog();
    }

    @Transactional
    public Map<String, Object> setCover(UUID id, MultipartFile file) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose an image.");
        if (file.getSize() > MAX_COVER_BYTES) throw ApiException.badRequest("Cover images can be up to 2 MB.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the image.");
        }
        String type = sniff(bytes);
        if (type == null) throw ApiException.badRequest("Use a JPG, PNG or WebP image.");
        String old = b.getCoverRef();
        try {
            b.setCoverRef(storage.put("blog/" + b.getId() + "/" + UUID.randomUUID() + ext(type), bytes, type));
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Could not save the image. Try again.");
        }
        b.setCoverType(type);
        b.setUpdatedAt(Instant.now());
        posts.save(b);
        if (old != null) storage.delete(old);
        cache.evictBlog();
        return view(b, true);
    }

    @Transactional
    public Map<String, Object> removeCover(UUID id) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        if (b.getCoverRef() != null) storage.delete(b.getCoverRef());
        b.setCoverRef(null);
        b.setCoverType(null);
        b.setUpdatedAt(Instant.now());
        cache.evictBlog();
        return view(posts.save(b), true);
    }

    @Transactional(readOnly = true)
    public BlogPost adminCover(UUID id) {
        PlatformGuard.requireSuperadmin();
        BlogPost b = find(id);
        if (b.getCoverRef() == null) throw ApiException.notFound("No cover image");
        return b;
    }

    // ---------- helpers ----------

    private BlogPost find(UUID id) {
        return posts.findById(id).orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private static String ext(String type) {
        return switch (type) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    static String slugify(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD).toLowerCase();
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (n.length() > 100) n = n.substring(0, 100).replaceAll("-+$", "");
        return n;
    }

    private static String str(Map<String, Object> body, String key, int max) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.length() > max) throw ApiException.badRequest("The " + key + " is too long (up to " + max + " characters).");
        return s;
    }

    private void apply(BlogPost b, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("title")) {
            String title = str(body, "title", 200);
            if (title == null) throw ApiException.badRequest("Give the post a title.");
            b.setTitle(title);
        }
        if (creating || body.containsKey("slug")) {
            String raw = str(body, "slug", 120);
            String slug = slugify(raw == null ? b.getTitle() : raw);
            if (slug.isEmpty()) throw ApiException.badRequest("Give the post a web address made of letters or numbers.");
            if (AuthService.RESERVED_SLUGS.contains(slug) || "rss-xml".equals(slug) || "rss".equals(slug)) {
                throw ApiException.badRequest("That web address is reserved by Owlaw. Choose another.");
            }
            if (!slug.equals(b.getSlug()) && posts.existsBySlug(slug)) {
                throw ApiException.conflict("Another post already uses that web address.");
            }
            b.setSlug(slug);
        }
        if (body.containsKey("summary") || creating) b.setSummary(str(body, "summary", 400));
        if (body.containsKey("body") || creating) b.setBody(str(body, "body", 100_000));
        if (body.containsKey("practiceArea") || creating) {
            String a = str(body, "practiceArea", 80);
            if (a != null && DirectoryCatalog.area(a).isEmpty()) throw ApiException.badRequest("Pick a practice area from the list.");
            b.setPracticeArea(a);
        }
        if (body.containsKey("city") || creating) {
            String c = str(body, "city", 80);
            if (c != null && DirectoryCatalog.CITIES.stream().noneMatch(x -> x.slug().equals(c))) {
                throw ApiException.badRequest("Pick a city from the list.");
            }
            b.setCity(c);
        }
        if (body.containsKey("authorName") || creating) {
            String a = str(body, "authorName", 120);
            b.setAuthorName(a == null ? "Owlaw" : a);
        }
        if (body.containsKey("seoTitle") || creating) b.setSeoTitle(str(body, "seoTitle", 200));
        if (body.containsKey("seoDescription") || creating) b.setSeoDescription(str(body, "seoDescription", 300));
        b.setUpdatedAt(Instant.now());
    }

    private static Map<String, Object> view(BlogPost b, boolean withBody) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("slug", b.getSlug());
        m.put("title", b.getTitle());
        m.put("summary", b.getSummary());
        if (withBody) m.put("body", b.getBody());
        m.put("practiceArea", b.getPracticeArea());
        m.put("practiceAreaLabel", b.getPracticeArea() == null ? null
                : DirectoryCatalog.area(b.getPracticeArea()).map(DirectoryCatalog.Option::label).orElse(null));
        m.put("city", b.getCity());
        m.put("cityLabel", b.getCity() == null ? null
                : DirectoryCatalog.CITIES.stream().filter(c -> c.slug().equals(b.getCity())).map(DirectoryCatalog.City::name).findFirst().orElse(null));
        m.put("authorName", b.getAuthorName());
        m.put("status", b.getStatus());
        m.put("hasCover", b.getCoverRef() != null);
        m.put("seoTitle", b.getSeoTitle());
        m.put("seoDescription", b.getSeoDescription());
        m.put("publishedAt", b.getPublishedAt());
        m.put("updatedAt", b.getUpdatedAt());
        return m;
    }
}
