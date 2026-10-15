package com.legalsuite.docgen;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** The firm's letterhead: logo, contact details, reference prefix, trust banking details and footer. */
@Service
public class BrandingService {
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
    public static final String LOGO_URL = "/api/v1/firm/branding/logo";

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final AuditService audit;
    private final Path uploadRoot;

    public BrandingService(TenantRepository tenants, AppUserRepository users, AuditService audit,
                           @Value("${legalsuite.upload-dir:./uploads}") String uploadDir) {
        this.tenants = tenants;
        this.users = users;
        this.audit = audit;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath();
    }

    public static String defaultFooter(String name, String title) {
        String n = name == null ? "" : name.trim();
        String t = title == null ? "" : title.trim();
        if (n.isEmpty()) return t;
        return t.isEmpty() ? n : n + ", " + t;
    }

    private Tenant firm() {
        return tenants.findById(TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Firm not found"));
    }

    private AppUser me() {
        return users.findById(TenantContext.requireUser()).orElse(null);
    }

    public boolean footerIsDefault() {
        String f = firm().getFooterText();
        return f == null || f.isBlank();
    }

    /** Merge values for the firm and the signed-in attorney. */
    public Map<String, String> values() {
        Tenant t = firm();
        AppUser u = me();
        Map<String, String> v = new HashMap<>();
        v.put("firm_name", nz(t.getFirmName()));
        List<String> addr = new ArrayList<>();
        for (String s : new String[] {t.getAddressLine1(), t.getCity(), t.getState(), t.getZip()}) if (s != null && !s.isBlank()) addr.add(s.trim());
        v.put("firm_address", String.join(", ", addr));
        v.put("firm_phone", nz(t.getPhone()));
        v.put("firm_email", nz(t.getEmail()));
        v.put("firm_website", nz(t.getWebsite()));
        v.put("information_officer", nz(t.getInformationOfficerName()));
        v.put("trust_account_name", nz(t.getTrustAccountName()));
        v.put("trust_account_number", nz(t.getTrustAccountNumber()));
        v.put("bank_name", nz(t.getBankName()));
        v.put("branch_name", nz(t.getBranchName()));
        v.put("branch_code", nz(t.getBranchCode()));
        String name = u == null ? "" : u.getFullName();
        String title = u == null ? "" : nz(u.getTitle());
        v.put("attorney_name", nz(name));
        v.put("attorney_title", title);
        v.put("attorney_initials", u == null ? "" : nz(u.getInitials()));
        v.put("attorney_email", u == null ? "" : nz(u.getEmail()));
        v.put("firm_ref", t.getRefPrefix() != null && !t.getRefPrefix().isBlank() ? t.getRefPrefix().trim() : v.get("attorney_initials"));
        v.put("footer_text", footerIsDefault(t) ? defaultFooter(name, title) : t.getFooterText().trim());
        return v;
    }

    private static boolean footerIsDefault(Tenant t) {
        return t.getFooterText() == null || t.getFooterText().isBlank();
    }

    public Branding current() {
        return current(values());
    }

    public Branding current(Map<String, String> values) {
        Tenant t = firm();
        return new Branding(values, logoBytes(t), t.getLetterheadColour());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.legalsuite.storage.FileStorage storage;

    private byte[] logoBytes(Tenant t) {
        if (t.getLogoPath() == null) return null;
        try {
            return storage.read(t.getLogoPath());
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- settings API

    public Map<String, Object> view() {
        Tenant t = firm();
        Map<String, String> v = values();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firmName", t.getFirmName());
        m.put("addressLine1", t.getAddressLine1());
        m.put("city", t.getCity());
        m.put("province", t.getState());
        m.put("postalCode", t.getZip());
        m.put("phone", t.getPhone());
        m.put("email", t.getEmail());
        m.put("refPrefix", t.getRefPrefix());
        m.put("footerText", t.getFooterText());
        m.put("defaultFooterText", defaultFooter(v.get("attorney_name"), v.get("attorney_title")));
        m.put("letterheadColour", t.getLetterheadColour() == null ? "#1f2a44" : t.getLetterheadColour());
        m.put("informationOfficer", t.getInformationOfficerName());
        m.put("trustAccountName", t.getTrustAccountName());
        m.put("trustAccountNumber", t.getTrustAccountNumber());
        m.put("bankName", t.getBankName());
        m.put("branchName", t.getBranchName());
        m.put("branchCode", t.getBranchCode());
        m.put("hasLogo", t.getLogoPath() != null);
        m.put("logoUrl", t.getLogoPath() == null ? null : LOGO_URL);
        m.put("attorneyName", v.get("attorney_name"));
        m.put("attorneyTitle", v.get("attorney_title"));
        return m;
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        Roles.requireTenantAdmin("Only a firm owner or admin can change the letterhead.");
        Tenant t = firm();
        if (body.get("firmName") != null) {
            String n = str(body, "firmName");
            if (n == null) throw ApiException.badRequest("Firm name cannot be empty.");
            t.setFirmName(n);
        }
        if (body.containsKey("addressLine1")) t.setAddressLine1(str(body, "addressLine1"));
        if (body.containsKey("city")) t.setCity(str(body, "city"));
        if (body.containsKey("province")) t.setState(str(body, "province"));
        if (body.containsKey("postalCode")) t.setZip(str(body, "postalCode"));
        if (body.containsKey("phone")) t.setPhone(str(body, "phone"));
        if (body.containsKey("email")) t.setEmail(str(body, "email"));
        if (body.containsKey("refPrefix")) t.setRefPrefix(limit(str(body, "refPrefix"), 20));
        if (body.containsKey("footerText")) t.setFooterText(limit(str(body, "footerText"), 250));
        if (body.containsKey("letterheadColour")) {
            String c = str(body, "letterheadColour");
            if (c != null && !c.matches("#[0-9a-fA-F]{6}")) throw ApiException.badRequest("Letterhead colour must look like #1f2a44.");
            t.setLetterheadColour(c);
        }
        if (body.containsKey("informationOfficer")) t.setInformationOfficerName(str(body, "informationOfficer"));
        if (body.containsKey("trustAccountName")) t.setTrustAccountName(str(body, "trustAccountName"));
        if (body.containsKey("trustAccountNumber")) t.setTrustAccountNumber(str(body, "trustAccountNumber"));
        if (body.containsKey("bankName")) t.setBankName(str(body, "bankName"));
        if (body.containsKey("branchName")) t.setBranchName(str(body, "branchName"));
        if (body.containsKey("branchCode")) t.setBranchCode(str(body, "branchCode"));
        t.setUpdatedAt(Instant.now());
        tenants.save(t);
        audit.record("firm.branding", "tenant", t.getId().toString(), String.join(",", body.keySet()));
        return view();
    }

    @Transactional
    public Map<String, Object> uploadLogo(MultipartFile file) {
        Roles.requireTenantAdmin("Only a firm owner or admin can change the logo.");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a PNG or JPEG logo.");
        if (file.getSize() > MAX_LOGO_BYTES) throw ApiException.badRequest("The logo can be up to 2 MB.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String mime = sniff(bytes);
        if (mime == null) throw ApiException.badRequest("The logo must be a PNG or JPEG image.");
        try {
            if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)) == null) {
                throw ApiException.badRequest("The logo image could not be read.");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("The logo image could not be read.");
        }
        Tenant t = firm();
        try {
            String ref = storage.put(t.getId() + "/branding/logo-" + System.currentTimeMillis()
                    + (mime.equals("image/png") ? ".png" : ".jpg"), bytes, mime);
            String old = t.getLogoPath();
            t.setLogoPath(ref);
            t.setLogoMime(mime);
            if (t.getLogoUrl() == null || t.getLogoUrl().isBlank() || LOGO_URL.equals(t.getLogoUrl())) t.setLogoUrl(LOGO_URL);
            t.setUpdatedAt(Instant.now());
            tenants.save(t);
            if (old != null && !old.equals(ref)) storage.delete(old);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        audit.record("firm.logo", "tenant", t.getId().toString(), mime);
        return view();
    }

    /** The signed-in firm's own logo; other firms' logos are unreachable by construction. */
    public Map.Entry<String, byte[]> logo() {
        Tenant t = firm();
        byte[] b = logoBytes(t);
        if (b == null) throw ApiException.notFound("No logo uploaded yet.");
        return Map.entry(t.getLogoMime() == null ? "image/png" : t.getLogoMime(), b);
    }

    static String sniff(byte[] b) {
        if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) return "image/jpeg";
        return null;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String limit(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
