package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.PublicSitePhoto;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.PublicSitePhotoRepository;
import com.legalsuite.storage.FileStorage;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The firm's own photographs on its public site. Background removal happens in the browser, so the
 * server only checks and stores the finished image. Edits stay in draft until branding is approved.
 */
@Service
public class PublicSitePhotoService {
    public static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> TENANT_ADMINS = Set.of("owner", "partner", "director");
    private static final Pattern PERSON_SLOT = Pattern.compile("person-[0-9a-fA-F-]{36}");

    private final PublicSitePhotoRepository photos;
    private final AppUserRepository users;
    private final FileStorage storage;
    private final AuditService audit;

    public PublicSitePhotoService(PublicSitePhotoRepository photos, AppUserRepository users, FileStorage storage, AuditService audit) {
        this.photos = photos;
        this.users = users;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> upload(String slot, MultipartFile file) {
        UUID tenantId = requireTenantAdmin();
        String clean = requireSlot(tenantId, slot);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a photograph to upload");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("Photographs can be up to 15 MB");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read that file");
        }
        String type = sniff(bytes);
        if (type == null) throw ApiException.badRequest("Use a PNG, WebP or JPEG photograph");
        PublicSitePhoto row = photos.findByTenantIdAndSlot(tenantId, clean).orElseGet(() -> {
            PublicSitePhoto p = new PublicSitePhoto();
            p.setTenantId(tenantId);
            p.setSlot(clean);
            return p;
        });
        String ext = switch (type) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String ref;
        try {
            ref = storage.put(tenantId + "/public-site/" + clean + "-" + UUID.randomUUID() + "." + ext, bytes, type);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not save the photograph. Try again.");
        }
        dropIfUnused(row.getDraftRef(), row.getLiveRef());
        row.setDraftRef(ref);
        row.setDraftType(type);
        row.setDraftVersion(System.currentTimeMillis());
        row.setUpdatedAt(Instant.now());
        photos.save(row);
        audit.record("public_site.photo_upload", "public_site", tenantId.toString(), clean);
        return describe(row);
    }

    @Transactional
    public Map<String, Object> remove(String slot) {
        UUID tenantId = requireTenantAdmin();
        String clean = requireSlot(tenantId, slot);
        PublicSitePhoto row = photos.findByTenantIdAndSlot(tenantId, clean).orElse(null);
        if (row == null) return Map.of("slot", clean, "draft", false, "live", false, "pending", false);
        dropIfUnused(row.getDraftRef(), row.getLiveRef());
        row.setDraftRef(null);
        row.setDraftType(null);
        row.setDraftVersion(null);
        row.setUpdatedAt(Instant.now());
        photos.save(row);
        audit.record("public_site.photo_remove", "public_site", tenantId.toString(), clean);
        return describe(row);
    }

    /** The firm's own draft copy, for the preview on the website settings page. */
    public Map.Entry<String, byte[]> draft(String slot) {
        UUID tenantId = requireTenantAdmin();
        PublicSitePhoto row = photos.findByTenantIdAndSlot(tenantId, normalize(slot))
                .filter(p -> p.getDraftRef() != null)
                .orElseThrow(() -> ApiException.notFound("No photograph yet"));
        return Map.entry(row.getDraftType(), read(row.getDraftRef()));
    }

    /** The approved copy, for visitors. Caller checks that the site is published. */
    public Map.Entry<String, byte[]> live(UUID tenantId, String slot) {
        PublicSitePhoto row = photos.findByTenantIdAndSlot(tenantId, normalize(slot))
                .filter(p -> p.getLiveRef() != null)
                .orElseThrow(() -> ApiException.notFound("Photograph not found"));
        return Map.entry(row.getLiveType(), read(row.getLiveRef()));
    }

    /** Public URLs of approved photographs, keyed by slot. */
    public Map<String, String> liveUrls(UUID tenantId, String slug) {
        Map<String, String> out = new LinkedHashMap<>();
        for (PublicSitePhoto p : photos.findByTenantId(tenantId)) {
            if (p.getLiveRef() != null) {
                out.put(p.getSlot(), "/api/v1/public/sites/" + slug + "/photos/" + p.getSlot() + "?v=" + p.getLiveVersion());
            }
        }
        return out;
    }

    public Map<String, Map<String, Object>> adminState(UUID tenantId) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (PublicSitePhoto p : photos.findByTenantId(tenantId)) out.put(p.getSlot(), describe(p));
        return out;
    }

    public boolean anyPending(UUID tenantId) {
        return photos.findByTenantId(tenantId).stream().anyMatch(PublicSitePhotoService::pending);
    }

    /** Branding approved: the drafts become what visitors see. */
    @Transactional
    public void promote(UUID tenantId) {
        for (PublicSitePhoto p : photos.findByTenantId(tenantId)) {
            if (!pending(p)) continue;
            String oldLive = p.getLiveRef();
            p.setLiveRef(p.getDraftRef());
            p.setLiveType(p.getDraftType());
            p.setLiveVersion(p.getDraftVersion());
            p.setUpdatedAt(Instant.now());
            photos.save(p);
            dropIfUnused(oldLive, p.getDraftRef());
        }
    }

    static boolean pending(PublicSitePhoto p) {
        return !Objects.equals(p.getDraftRef(), p.getLiveRef());
    }

    /** PNG, WebP or JPEG by magic bytes; anything else is refused. */
    static String sniff(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return "image/jpeg";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private Map<String, Object> describe(PublicSitePhoto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slot", p.getSlot());
        m.put("draft", p.getDraftRef() != null);
        m.put("draftVersion", p.getDraftVersion());
        m.put("live", p.getLiveRef() != null);
        m.put("pending", pending(p));
        return m;
    }

    private String requireSlot(UUID tenantId, String slot) {
        String clean = normalize(slot);
        if ("hero".equals(clean)) return clean;
        if (PERSON_SLOT.matcher(clean).matches()) {
            UUID userId = UUID.fromString(clean.substring("person-".length()));
            AppUser user = users.findById(userId).orElse(null);
            if (user != null && tenantId.equals(user.getTenantId())) return clean;
        }
        throw ApiException.badRequest("Unknown photograph slot");
    }

    private static String normalize(String slot) {
        return slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] read(String ref) {
        try {
            return storage.read(ref);
        } catch (IOException e) {
            throw ApiException.notFound("Photograph not found");
        }
    }

    private void dropIfUnused(String ref, String keep) {
        if (ref != null && !ref.equals(keep)) storage.delete(ref);
    }

    private UUID requireTenantAdmin() {
        String role = TenantContext.getRole();
        if (role == null || !TENANT_ADMINS.contains(role.toLowerCase(Locale.ROOT))) {
            throw ApiException.forbidden("Only a firm owner can change the public site.");
        }
        return TenantContext.requireTenant();
    }
}
