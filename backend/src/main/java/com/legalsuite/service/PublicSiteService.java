package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.NewsletterOptIn;
import com.legalsuite.domain.PublicSiteFeature;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantPublicSite;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.NewsletterOptInRepository;
import com.legalsuite.repo.PublicSiteFeatureRepository;
import com.legalsuite.repo.TenantPublicSiteRepository;
import com.legalsuite.repo.TenantRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicSiteService {
    private static final Set<String> TENANT_ADMINS = Set.of("owner", "partner", "director");
    private static final Set<String> DIRECTORY_ROLES = Set.of("owner", "partner", "attorney", "associate", "director");

    private final TenantRepository tenants;
    private final TenantPublicSiteRepository sites;
    private final PublicSiteFeatureRepository features;
    private final AppUserRepository users;
    private final NewsletterOptInRepository optIns;

    public PublicSiteService(
            TenantRepository tenants,
            TenantPublicSiteRepository sites,
            PublicSiteFeatureRepository features,
            AppUserRepository users,
            NewsletterOptInRepository optIns) {
        this.tenants = tenants;
        this.sites = sites;
        this.features = features;
        this.users = users;
        this.optIns = optIns;
    }

    @Transactional
    public TenantPublicSite ensureDraft(Tenant tenant) {
        TenantPublicSite site = sites.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantPublicSite created = new TenantPublicSite();
            created.setTenantId(tenant.getId());
            created.setPublishStatus("draft");
            created.setFirmName(tenant.getFirmName());
            created.setTagline(tenant.getTagline());
            created.setAccent("navy");
            created.setBrandingStatus("none");
            String city = tenant.getCity() == null || tenant.getCity().isBlank() ? "its office" : tenant.getCity();
            created.setAboutText(tenant.getFirmName() + " advises clients from " + city + ".");
            created.setUpdatedAt(Instant.now());
            return sites.save(created);
        });
        for (PublicSiteCatalog.Feature feature : PublicSiteCatalog.FEATURES) {
            if (features.findByTenantIdAndFeatureKey(tenant.getId(), feature.key()).isEmpty()) {
                PublicSiteFeature row = new PublicSiteFeature();
                row.setTenantId(tenant.getId());
                row.setFeatureKey(feature.key());
                row.setRequested(false);
                row.setApprovalStatus("none");
                row.setUpdatedAt(Instant.now());
                features.save(row);
            }
        }
        return site;
    }

    @Transactional
    public void seed(
            Tenant tenant,
            String accent,
            String publishStatus,
            Map<String, String> featureState,
            String about,
            String tagline,
            String recognitionJson) {
        TenantPublicSite site = ensureDraft(tenant);
        site.setAccent(PublicSiteCatalog.requireAccent(accent));
        site.setFirmName(tenant.getFirmName());
        site.setTagline(tagline);
        site.setAboutText(about);
        site.setPublishStatus(publishStatus);
        site.setRecognitionJson(recognitionJson);
        site.setPublishNote(null);
        if ("published".equals(publishStatus)) {
            copyBrandingLive(site);
            site.setBrandingStatus("approved");
        } else {
            site.setLiveFirmName(null);
            site.setLiveTagline(null);
            site.setLiveAccent(null);
            site.setLiveAboutText(null);
            site.setBrandingStatus("pending");
        }
        site.setUpdatedAt(Instant.now());
        sites.save(site);
        for (PublicSiteCatalog.Feature feature : PublicSiteCatalog.FEATURES) {
            PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenant.getId(), feature.key()).orElseThrow();
            String state = featureState.getOrDefault(feature.key(), "off");
            if ("approved".equals(state) || "pending".equals(state) || "rejected".equals(state)) {
                row.setRequested(true);
                row.setApprovalStatus(state);
            } else {
                row.setRequested(false);
                row.setApprovalStatus("none");
            }
            row.setNote(null);
            row.setUpdatedAt(Instant.now());
            features.save(row);
        }
    }

    public Map<String, Object> publicView(String slug) {
        Tenant tenant = tenants.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        TenantPublicSite site = sites.findByTenantId(tenant.getId()).orElse(null);
        if (site == null || !"published".equals(site.getPublishStatus())) {
            Map<String, Object> closed = new LinkedHashMap<>();
            closed.put("published", false);
            closed.put("slug", tenant.getSlug());
            closed.put("firmName", tenant.getFirmName());
            return closed;
        }
        Map<String, Boolean> live = liveMap(site);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("published", true);
        m.put("slug", tenant.getSlug());
        m.put("firmName", blankTo(site.getLiveFirmName(), tenant.getFirmName()));
        m.put("tagline", site.getLiveTagline() == null ? "" : site.getLiveTagline());
        String accent = site.getLiveAccent() == null ? "navy" : site.getLiveAccent();
        m.put("accent", accent);
        m.put("accentHex", PublicSiteCatalog.hex(accent));
        m.put("phone", tenant.getPhone());
        m.put("email", tenant.getEmail());
        m.put("addressLine1", tenant.getAddressLine1());
        m.put("city", tenant.getCity());
        m.put("state", tenant.getState());
        m.put("zip", tenant.getZip());
        m.put("country", tenant.getCountry());
        m.put("about", site.getLiveAboutText() == null ? "" : site.getLiveAboutText());
        m.put("informationOfficerName", tenant.getInformationOfficerName());
        m.put("informationOfficerEmail", tenant.getInformationOfficerEmail());
        List<Map<String, String>> practices = new ArrayList<>();
        for (String name : JsonLists.strings(tenant.getPracticeAreasJson())) {
            practices.add(Map.of("name", name, "slug", PublicSiteCatalog.slugify(name)));
        }
        m.put("practices", practices);
        List<String> keys = live.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        m.put("features", keys);
        if (live.get("people")) {
            m.put("people", directory(tenant.getId()));
        }
        if (live.get("insights")) {
            m.put("insights", PublicSiteCatalog.insights(tenant.getCountry()).stream().map(this::article).toList());
        }
        if (live.get("situations")) {
            m.put("situations", PublicSiteCatalog.situations(tenant.getCountry()).stream().map(this::situation).toList());
        }
        if (live.get("fees")) {
            m.put("feesNote", PublicSiteCatalog.feesNote(tenant.getCountry()));
        }
        if (live.get("whatsapp")) {
            m.put("whatsappUrl", PublicSiteCatalog.whatsappUrl(tenant.getPhone(), tenant.getCountry()));
        }
        if (live.get("recognition")) {
            m.put("recognition", recognition(site.getRecognitionJson()));
        }
        return m;
    }

    public void requireBookingOpen(String slug) {
        Tenant tenant = tenants.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (!featureLive(tenant.getId(), "booking")) {
            throw ApiException.notFound("Enquiry is not open on this site");
        }
    }

    @Transactional
    public Map<String, Object> subscribe(String slug, Map<String, Object> body) {
        Tenant tenant = tenants.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (!featureLive(tenant.getId(), "newsletter")) {
            throw ApiException.notFound("Newsletter signup is not open on this site");
        }
        Object consent = body == null ? null : body.get("consent");
        boolean yes = Boolean.TRUE.equals(consent) || "true".equalsIgnoreCase(String.valueOf(consent));
        if (!yes) {
            throw ApiException.badRequest("Tick the box to opt in. The box starts unticked.");
        }
        String email = body == null || body.get("email") == null ? "" : String.valueOf(body.get("email")).trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || !email.contains("@") || email.startsWith("@") || email.length() > 200) {
            throw ApiException.badRequest("Enter an email address");
        }
        optIns.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email).orElseGet(() -> {
            NewsletterOptIn row = new NewsletterOptIn();
            row.setTenantId(tenant.getId());
            row.setEmail(email);
            row.setConsent(true);
            row.setConsentedAt(Instant.now());
            return optIns.save(row);
        });
        String name = sites.findByTenantId(tenant.getId()).map(TenantPublicSite::getLiveFirmName).orElse(tenant.getFirmName());
        return Map.of(
                "status", "received",
                "message", name + " recorded your opt-in. You can ask the firm to stop.");
    }

    @Transactional
    public Map<String, Object> adminView() {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        TenantPublicSite site = ensureDraft(tenant);
        return adminView(tenant, site);
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        TenantPublicSite site = ensureDraft(tenant);
        if (body.get("firmName") != null) {
            String name = String.valueOf(body.get("firmName")).trim();
            if (name.isBlank() || "null".equals(name)) {
                throw ApiException.badRequest("Public firm name is required");
            }
            if (name.length() > 255) {
                throw ApiException.badRequest("Public firm name is too long");
            }
            site.setFirmName(name);
        }
        if (body.get("tagline") != null) {
            site.setTagline(clip(String.valueOf(body.get("tagline")), 500));
        }
        if (body.get("accent") != null) {
            site.setAccent(PublicSiteCatalog.requireAccent(String.valueOf(body.get("accent"))));
        }
        if (body.get("about") != null) {
            site.setAboutText(clip(String.valueOf(body.get("about")), 4000));
        }
        if (body.get("features") instanceof Map<?, ?> flags) {
            for (PublicSiteCatalog.Feature feature : PublicSiteCatalog.FEATURES) {
                if (!flags.containsKey(feature.key())) {
                    continue;
                }
                boolean requested = Boolean.parseBoolean(String.valueOf(flags.get(feature.key())));
                PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenant.getId(), feature.key()).orElseThrow();
                row.setRequested(requested);
                if (!requested && "pending".equals(row.getApprovalStatus())) {
                    row.setApprovalStatus("none");
                }
                row.setUpdatedAt(Instant.now());
                features.save(row);
            }
        }
        site.setUpdatedAt(Instant.now());
        sites.save(site);
        return adminView(tenant, site);
    }

    @Transactional
    public Map<String, Object> submit() {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        TenantPublicSite site = ensureDraft(tenant);
        for (PublicSiteFeature row : features.findByTenantIdOrderByFeatureKeyAsc(tenant.getId())) {
            if (row.isRequested() && !"approved".equals(row.getApprovalStatus())) {
                row.setApprovalStatus("pending");
                row.setUpdatedAt(Instant.now());
                features.save(row);
            }
        }
        if (!"published".equals(site.getPublishStatus())) {
            site.setPublishStatus("pending_approval");
        }
        if (brandingDirty(site)) {
            site.setBrandingStatus("pending");
        }
        site.setSubmittedAt(Instant.now());
        site.setUpdatedAt(Instant.now());
        sites.save(site);
        return adminView(tenant, site);
    }

    public List<Map<String, Object>> queue() {
        requireSuperadmin();
        List<Map<String, Object>> items = new ArrayList<>();
        for (TenantPublicSite site : sites.findAll()) {
            Tenant tenant = tenants.findById(site.getTenantId()).orElse(null);
            if (tenant == null || "legalsuite".equals(tenant.getSlug())) {
                continue;
            }
            if ("pending_approval".equals(site.getPublishStatus())) {
                items.add(queueItem(site, tenant, "publish", null));
            } else if ("published".equals(site.getPublishStatus()) && "pending".equals(site.getBrandingStatus())) {
                items.add(queueItem(site, tenant, "branding", null));
            }
            for (PublicSiteFeature row : features.findByTenantIdOrderByFeatureKeyAsc(site.getTenantId())) {
                if ("pending".equals(row.getApprovalStatus())) {
                    items.add(queueItem(site, tenant, "feature", row));
                }
            }
        }
        return items;
    }

    @Transactional
    public Map<String, Object> decidePublish(UUID tenantId, Map<String, Object> body) {
        requireSuperadmin();
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Firm not found"));
        TenantPublicSite site = ensureDraft(tenant);
        String decision = decision(body);
        String note = note(body);
        if ("approved".equals(decision)) {
            site.setPublishStatus("published");
            copyBrandingLive(site);
            site.setBrandingStatus("approved");
            site.setPublishNote(note);
            site.setBrandingNote(note);
        } else {
            site.setPublishStatus("rejected");
            site.setPublishNote(note);
        }
        site.setUpdatedAt(Instant.now());
        sites.save(site);
        return Map.of("publishStatus", site.getPublishStatus(), "slug", tenant.getSlug());
    }

    @Transactional
    public Map<String, Object> decideBranding(UUID tenantId, Map<String, Object> body) {
        requireSuperadmin();
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Firm not found"));
        TenantPublicSite site = ensureDraft(tenant);
        String decision = decision(body);
        String note = note(body);
        if ("approved".equals(decision)) {
            copyBrandingLive(site);
            site.setBrandingStatus("approved");
        } else {
            site.setBrandingStatus("rejected");
        }
        site.setBrandingNote(note);
        site.setUpdatedAt(Instant.now());
        sites.save(site);
        return Map.of("brandingStatus", site.getBrandingStatus(), "slug", tenant.getSlug());
    }

    @Transactional
    public Map<String, Object> decideFeature(UUID tenantId, String key, Map<String, Object> body) {
        requireSuperadmin();
        PublicSiteCatalog.Feature feature = PublicSiteCatalog.requireFeature(key);
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Firm not found"));
        ensureDraft(tenant);
        PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenantId, feature.key()).orElseThrow();
        String decision = decision(body);
        row.setApprovalStatus(decision);
        row.setNote(note(body));
        row.setUpdatedAt(Instant.now());
        features.save(row);
        TenantPublicSite site = sites.findByTenantId(tenantId).orElseThrow();
        boolean onSite = PublicSiteRules.live(site.getPublishStatus(), row.isRequested(), row.getApprovalStatus());
        return Map.of("feature", feature.key(), "approvalStatus", row.getApprovalStatus(), "live", onSite);
    }

    private Map<String, Object> adminView(Tenant tenant, TenantPublicSite site) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", tenant.getId());
        m.put("slug", tenant.getSlug());
        m.put("publishStatus", site.getPublishStatus());
        m.put("publishNote", site.getPublishNote());
        m.put("firmName", site.getFirmName());
        m.put("tagline", site.getTagline() == null ? "" : site.getTagline());
        m.put("accent", site.getAccent());
        m.put("about", site.getAboutText() == null ? "" : site.getAboutText());
        m.put("liveFirmName", site.getLiveFirmName());
        m.put("liveTagline", site.getLiveTagline());
        m.put("liveAccent", site.getLiveAccent());
        m.put("liveAbout", site.getLiveAboutText());
        m.put("brandingStatus", site.getBrandingStatus());
        m.put("brandingNote", site.getBrandingNote());
        m.put("submittedAt", site.getSubmittedAt());
        m.put("accents", PublicSiteCatalog.accentChoices());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PublicSiteCatalog.Feature feature : PublicSiteCatalog.FEATURES) {
            PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenant.getId(), feature.key()).orElseThrow();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", feature.key());
            item.put("label", feature.label());
            item.put("description", feature.description());
            item.put("requested", row.isRequested());
            item.put("approvalStatus", row.getApprovalStatus());
            item.put("note", row.getNote());
            item.put("live", PublicSiteRules.live(site.getPublishStatus(), row.isRequested(), row.getApprovalStatus()));
            rows.add(item);
        }
        m.put("features", rows);
        return m;
    }

    private Map<String, Object> queueItem(TenantPublicSite site, Tenant tenant, String kind, PublicSiteFeature row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", tenant.getId());
        m.put("slug", tenant.getSlug());
        m.put("firmName", tenant.getFirmName());
        m.put("city", tenant.getCity());
        m.put("country", tenant.getCountry());
        m.put("kind", kind);
        m.put("publishStatus", site.getPublishStatus());
        m.put("brandingStatus", site.getBrandingStatus());
        m.put("draftFirmName", site.getFirmName());
        m.put("draftTagline", site.getTagline());
        m.put("draftAccent", site.getAccent());
        m.put("liveFirmName", site.getLiveFirmName());
        m.put("liveTagline", site.getLiveTagline());
        m.put("liveAccent", site.getLiveAccent());
        if (row == null) {
            m.put("note", "branding".equals(kind) ? site.getBrandingNote() : site.getPublishNote());
        } else {
            PublicSiteCatalog.Feature feature = PublicSiteCatalog.requireFeature(row.getFeatureKey());
            m.put("featureKey", feature.key());
            m.put("featureLabel", feature.label());
            m.put("requested", row.isRequested());
            m.put("approvalStatus", row.getApprovalStatus());
            m.put("note", row.getNote());
        }
        return m;
    }

    private Map<String, Boolean> liveMap(TenantPublicSite site) {
        Map<String, Boolean> live = new LinkedHashMap<>();
        for (PublicSiteCatalog.Feature feature : PublicSiteCatalog.FEATURES) {
            PublicSiteFeature row = features.findByTenantIdAndFeatureKey(site.getTenantId(), feature.key()).orElse(null);
            boolean on = row != null && PublicSiteRules.live(site.getPublishStatus(), row.isRequested(), row.getApprovalStatus());
            live.put(feature.key(), on);
        }
        return live;
    }

    private boolean featureLive(UUID tenantId, String key) {
        TenantPublicSite site = sites.findByTenantId(tenantId).orElse(null);
        if (site == null) {
            return false;
        }
        PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenantId, key).orElse(null);
        return row != null && PublicSiteRules.live(site.getPublishStatus(), row.isRequested(), row.getApprovalStatus());
    }

    private List<Map<String, Object>> directory(UUID tenantId) {
        List<Map<String, Object>> people = new ArrayList<>();
        for (AppUser user : users.findByTenantIdOrderByLastNameAsc(tenantId)) {
            String role = user.getRole() == null ? "" : user.getRole().toLowerCase(Locale.ROOT);
            if (!DIRECTORY_ROLES.contains(role) || !"active".equals(user.getStatus())) {
                continue;
            }
            Map<String, Object> person = new LinkedHashMap<>();
            person.put("id", user.getId());
            person.put("fullName", user.getFullName());
            person.put("initials", user.getInitials());
            person.put("title", publicTitle(user));
            person.put("phone", user.getPhone());
            person.put("email", user.getEmail());
            person.put("bio", user.getBio());
            people.add(person);
        }
        return people;
    }

    private static String publicTitle(AppUser user) {
        if (user.getTitle() != null && !user.getTitle().isBlank()) {
            return user.getTitle().trim();
        }
        String role = user.getRole() == null ? "" : user.getRole().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "associate" -> "Associate";
            case "attorney" -> "Attorney";
            case "owner", "partner", "director" -> "Director";
            default -> "Staff";
        };
    }

    private Map<String, String> article(PublicSiteCatalog.Article article) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("slug", article.slug());
        m.put("title", article.title());
        m.put("type", article.type());
        m.put("date", article.date());
        m.put("summary", article.summary());
        m.put("body", article.body());
        return m;
    }

    private Map<String, String> situation(PublicSiteCatalog.Situation situation) {
        return Map.of("slug", situation.slug(), "title", situation.title(), "summary", situation.summary());
    }

    private List<Map<String, String>> recognition(String json) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> row : JsonLists.objects(json)) {
            String title = text(row.get("title"));
            if (title.isBlank()) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("year", text(row.get("year")));
            item.put("source", text(row.get("source")));
            item.put("title", title);
            out.add(item);
        }
        return out;
    }

    private boolean brandingDirty(TenantPublicSite site) {
        return !same(site.getFirmName(), site.getLiveFirmName())
                || !same(site.getTagline(), site.getLiveTagline())
                || !same(site.getAccent(), site.getLiveAccent())
                || !same(site.getAboutText(), site.getLiveAboutText());
    }

    private static void copyBrandingLive(TenantPublicSite site) {
        site.setLiveFirmName(site.getFirmName());
        site.setLiveTagline(site.getTagline());
        site.setLiveAccent(site.getAccent());
        site.setLiveAboutText(site.getAboutText());
    }

    private void requireTenantAdmin() {
        String role = TenantContext.getRole();
        if (role == null || !TENANT_ADMINS.contains(role.toLowerCase(Locale.ROOT))) {
            throw ApiException.forbidden("Only a firm owner can change the public site.");
        }
        TenantContext.requireTenant();
    }

    private void requireSuperadmin() {
        String role = TenantContext.getRole();
        if (role == null || !"superadmin".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("Platform approval is limited to the LegalSuite operator.");
        }
    }

    private Tenant currentTenant() {
        return tenants.findById(TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
    }

    private static String decision(Map<String, Object> body) {
        String raw = body == null || body.get("decision") == null ? "" : String.valueOf(body.get("decision")).trim().toLowerCase(Locale.ROOT);
        if ("approved".equals(raw) || "approve".equals(raw)) {
            return "approved";
        }
        if ("rejected".equals(raw) || "reject".equals(raw)) {
            return "rejected";
        }
        throw ApiException.badRequest("Decision must be approved or rejected");
    }

    private static String note(Map<String, Object> body) {
        if (body == null || body.get("note") == null) {
            return null;
        }
        String value = String.valueOf(body.get("note")).trim();
        if (value.isEmpty() || "null".equals(value)) {
            return null;
        }
        if (value.length() > 500) {
            throw ApiException.badRequest("Note is too long");
        }
        return value;
    }

    private static String clip(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        if ("null".equals(trimmed)) {
            return "";
        }
        if (trimmed.length() > max) {
            throw ApiException.badRequest("That text is too long");
        }
        return trimmed;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean same(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private static String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }
}
