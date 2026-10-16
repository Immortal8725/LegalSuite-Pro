package com.legalsuite.directory;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.PublicSiteFeature;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantPublicSite;
import com.legalsuite.repo.PublicSiteFeatureRepository;
import com.legalsuite.repo.TenantPublicSiteRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directory profile, practitioner verification and the listing rule. A firm is listed only when it
 * switched the directory on, its plan is active, it has practice areas and a city, and at least one
 * practitioner is LPC-verified with a current Fidelity Fund certificate.
 */
@Service
public class DirectoryService {
    public static final String FEATURE_KEY = "directory";
    public static final ZoneId SA = ZoneId.of("Africa/Johannesburg");
    private static final Set<String> TENANT_ADMINS = Set.of("owner", "partner", "director");
    static final int MAX_AREAS = 6;

    private final TenantRepository tenants;
    private final TenantPublicSiteRepository sites;
    private final PublicSiteFeatureRepository features;
    private final DirectoryProfileRepository profiles;
    private final PractitionerRegistrationRepository practitioners;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public DirectoryService(
            TenantRepository tenants,
            TenantPublicSiteRepository sites,
            PublicSiteFeatureRepository features,
            DirectoryProfileRepository profiles,
            PractitionerRegistrationRepository practitioners,
            AuditService audit) {
        this(tenants, sites, features, profiles, practitioners, audit, Clock.system(SA));
    }

    DirectoryService(
            TenantRepository tenants,
            TenantPublicSiteRepository sites,
            PublicSiteFeatureRepository features,
            DirectoryProfileRepository profiles,
            PractitionerRegistrationRepository practitioners,
            AuditService audit,
            Clock clock) {
        this.tenants = tenants;
        this.sites = sites;
        this.features = features;
        this.profiles = profiles;
        this.practitioners = practitioners;
        this.audit = audit;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SA));
    }

    // ---- Listing rule -------------------------------------------------------------------------

    public record Listing(boolean listed, List<String> reasons) {}

    public boolean switchedOn(UUID tenantId) {
        return features.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY).map(PublicSiteFeature::isRequested).orElse(false);
    }

    public static boolean activePlan(Tenant tenant, Instant now) {
        if (tenant == null || tenant.getStatus() == null) return false;
        String status = tenant.getStatus().toLowerCase(Locale.ROOT);
        if ("active".equals(status)) return true;
        return "trial".equals(status) && (tenant.getTrialEndsAt() == null || tenant.getTrialEndsAt().isAfter(now));
    }

    public boolean practitionerCurrent(PractitionerRegistration p) {
        return "verified".equals(p.getStatus())
                && p.getFidelityExpiresOn() != null
                && !p.getFidelityExpiresOn().isBefore(today());
    }

    public Listing listing(Tenant tenant) {
        List<String> reasons = new ArrayList<>();
        if (tenant == null || "legalsuite".equals(tenant.getSlug())) {
            return new Listing(false, List.of("This account cannot be listed."));
        }
        if (!switchedOn(tenant.getId())) reasons.add("Turn on the Find a Lawyer listing.");
        if (!activePlan(tenant, clock.instant())) reasons.add("Your Owlaw plan is not active.");
        DirectoryProfile profile = profiles.findByTenantId(tenant.getId()).orElse(null);
        if (profile == null || JsonLists.strings(profile.getPracticeAreasJson()).isEmpty()) {
            reasons.add("Choose at least one practice area.");
        }
        if (profile == null || JsonLists.strings(profile.getCitiesJson()).isEmpty()) {
            reasons.add("Choose at least one city or town.");
        }
        boolean verified = practitioners.findByTenantIdOrderByFullNameAsc(tenant.getId()).stream().anyMatch(this::practitionerCurrent);
        if (!verified) {
            reasons.add("At least one practitioner must be LPC-verified by Owlaw with a current Fidelity Fund certificate.");
        }
        return new Listing(reasons.isEmpty(), reasons);
    }

    public boolean listed(Tenant tenant) {
        return listing(tenant).listed();
    }

    public Optional<Tenant> listedTenant(String slug) {
        String s = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        return tenants.findBySlug(s).filter(this::listed);
    }

    public boolean publicSitePublished(UUID tenantId) {
        return sites.findByTenantId(tenantId).map(TenantPublicSite::getPublishStatus).map("published"::equals).orElse(false);
    }

    // ---- Firm admin ---------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> adminView() {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        return adminView(tenant, ensureProfile(tenant));
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        DirectoryProfile p = ensureProfile(tenant);
        Map<String, Object> b = body == null ? Map.of() : body;
        if (b.containsKey("practiceAreas")) {
            List<String> areas = slugs(b.get("practiceAreas"), DirectoryCatalog::area, "practice area");
            if (areas.size() > MAX_AREAS) throw ApiException.badRequest("Choose up to " + MAX_AREAS + " practice areas.");
            p.setPracticeAreasJson(JsonLists.toJson(areas));
        }
        if (b.containsKey("cities")) {
            List<String> cities = slugs(b.get("cities"), s -> DirectoryCatalog.city(s).map(c -> new DirectoryCatalog.Option(c.slug(), c.name())), "city");
            if (cities.size() > 10) throw ApiException.badRequest("Choose up to 10 cities or towns.");
            p.setCitiesJson(JsonLists.toJson(cities));
            Set<String> provinces = new LinkedHashSet<>();
            for (String c : cities) DirectoryCatalog.city(c).ifPresent(x -> provinces.add(x.province()));
            p.setProvincesJson(JsonLists.toJson(new ArrayList<>(provinces)));
        }
        if (b.containsKey("languages")) {
            p.setLanguagesJson(JsonLists.toJson(slugs(b.get("languages"), DirectoryCatalog::language, "language")));
        }
        if (b.containsKey("consultOptions")) {
            p.setConsultOptionsJson(JsonLists.toJson(slugs(b.get("consultOptions"), DirectoryCatalog::consult, "consultation option")));
        }
        if (b.containsKey("fixedConsultFee")) {
            Object raw = b.get("fixedConsultFee");
            if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) {
                p.setFixedConsultFee(null);
            } else {
                try {
                    BigDecimal fee = new BigDecimal(String.valueOf(raw).trim().replace("R", "").replace(" ", ""));
                    if (fee.signum() < 0 || fee.compareTo(new BigDecimal("100000")) > 0) throw new NumberFormatException();
                    p.setFixedConsultFee(fee.setScale(2, java.math.RoundingMode.HALF_UP));
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("Enter the consultation fee in rand, for example 500.");
                }
            }
        }
        if (b.containsKey("responseTime")) {
            String rt = text(b.get("responseTime"));
            if (rt.isEmpty()) p.setResponseTime(null);
            else p.setResponseTime(DirectoryCatalog.responseTime(rt).orElseThrow(() -> ApiException.badRequest("Unknown reply time")).slug());
        }
        if (b.containsKey("bio")) {
            String bio = text(b.get("bio"));
            if (bio.length() > 600) throw ApiException.badRequest("Keep the short bio under 600 characters.");
            String banned = PublicWording.bannedWord(bio);
            if (banned != null) {
                throw ApiException.badRequest("Please do not use the word \"" + banned + "\" in the bio. Describe your practice areas instead.");
            }
            p.setBio(bio.isEmpty() ? null : bio);
        }
        if (b.containsKey("listed")) {
            setSwitch(tenant.getId(), Boolean.parseBoolean(String.valueOf(b.get("listed"))));
        }
        p.setUpdatedAt(Instant.now());
        profiles.save(p);
        audit.record("directory.update", "directory_profile", tenant.getId().toString(), String.join(",", b.keySet()));
        return adminView(tenant, p);
    }

    @Transactional
    public Map<String, Object> addPractitioner(Map<String, Object> body) {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        if (practitioners.findByTenantIdOrderByFullNameAsc(tenant.getId()).size() >= 50) {
            throw ApiException.badRequest("A firm can list up to 50 practitioners.");
        }
        PractitionerRegistration p = new PractitionerRegistration();
        p.setTenantId(tenant.getId());
        applyPractitioner(p, body == null ? Map.of() : body, true);
        practitioners.save(p);
        audit.record("directory.practitioner.add", "practitioner_registration", p.getId().toString(), p.getLpcNumber());
        return adminView(tenant, ensureProfile(tenant));
    }

    @Transactional
    public Map<String, Object> updatePractitioner(UUID id, Map<String, Object> body) {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        PractitionerRegistration p = practitioners.findByIdAndTenantId(id, tenant.getId())
                .orElseThrow(() -> ApiException.notFound("Practitioner not found"));
        applyPractitioner(p, body == null ? Map.of() : body, false);
        practitioners.save(p);
        audit.record("directory.practitioner.update", "practitioner_registration", p.getId().toString(), p.getStatus());
        return adminView(tenant, ensureProfile(tenant));
    }

    @Transactional
    public Map<String, Object> removePractitioner(UUID id) {
        requireTenantAdmin();
        Tenant tenant = currentTenant();
        PractitionerRegistration p = practitioners.findByIdAndTenantId(id, tenant.getId())
                .orElseThrow(() -> ApiException.notFound("Practitioner not found"));
        practitioners.delete(p);
        audit.record("directory.practitioner.remove", "practitioner_registration", id.toString(), p.getLpcNumber());
        return adminView(tenant, ensureProfile(tenant));
    }

    private void applyPractitioner(PractitionerRegistration p, Map<String, Object> body, boolean create) {
        boolean recheck = create;
        if (create || body.containsKey("fullName")) {
            String name = text(body.get("fullName"));
            if (name.isEmpty()) throw ApiException.badRequest("Enter the practitioner's full name.");
            if (name.length() > 255) throw ApiException.badRequest("That name is too long.");
            if (!name.equals(p.getFullName())) recheck = true;
            p.setFullName(name);
        }
        if (create || body.containsKey("lpcNumber")) {
            String lpc = text(body.get("lpcNumber")).toUpperCase(Locale.ROOT);
            if (lpc.isEmpty()) throw ApiException.badRequest("Enter the LPC number.");
            if (lpc.length() > 64 || !lpc.matches("[A-Z0-9 /\\-]+")) {
                throw ApiException.badRequest("The LPC number can only have letters, numbers, spaces, - and /.");
            }
            if (!lpc.equals(p.getLpcNumber())) recheck = true;
            p.setLpcNumber(lpc);
        }
        if (create || body.containsKey("fidelityExpiresOn")) {
            String raw = text(body.get("fidelityExpiresOn"));
            LocalDate date;
            try {
                date = raw.isEmpty() ? null : LocalDate.parse(raw);
            } catch (RuntimeException e) {
                throw ApiException.badRequest("Enter the Fidelity Fund certificate expiry as a date.");
            }
            if (date == null) throw ApiException.badRequest("Enter the Fidelity Fund certificate expiry date.");
            if (!date.equals(p.getFidelityExpiresOn())) recheck = true;
            p.setFidelityExpiresOn(date);
        }
        if (recheck) {
            // Any change to what the operator checked sends it back for a fresh check.
            p.setStatus("pending");
            p.setVerifiedAt(null);
            p.setVerifiedBy(null);
            p.setVerificationNote(null);
        }
        p.setUpdatedAt(Instant.now());
    }

    private void setSwitch(UUID tenantId, boolean on) {
        PublicSiteFeature row = features.findByTenantIdAndFeatureKey(tenantId, FEATURE_KEY).orElseGet(() -> {
            PublicSiteFeature created = new PublicSiteFeature();
            created.setTenantId(tenantId);
            created.setFeatureKey(FEATURE_KEY);
            return created;
        });
        row.setRequested(on);
        // The firm controls this switch; verification of practitioners is the gate, not a feature approval.
        row.setApprovalStatus("none");
        row.setUpdatedAt(Instant.now());
        features.save(row);
    }

    DirectoryProfile ensureProfile(Tenant tenant) {
        return profiles.findByTenantId(tenant.getId()).orElseGet(() -> {
            DirectoryProfile p = new DirectoryProfile();
            p.setTenantId(tenant.getId());
            DirectoryCatalog.CITIES.stream()
                    .filter(c -> tenant.getCity() != null && c.name().equalsIgnoreCase(tenant.getCity().trim()))
                    .findFirst()
                    .ifPresent(c -> {
                        p.setCitiesJson(JsonLists.toJson(List.of(c.slug())));
                        p.setProvincesJson(JsonLists.toJson(List.of(c.province())));
                    });
            p.setLanguagesJson(JsonLists.toJson(List.of("english")));
            return profiles.save(p);
        });
    }

    private Map<String, Object> adminView(Tenant tenant, DirectoryProfile p) {
        Map<String, Object> m = new LinkedHashMap<>(profileFields(p));
        Listing listing = listing(tenant);
        m.put("listedSwitch", switchedOn(tenant.getId()));
        m.put("listed", listing.listed());
        m.put("reasons", listing.reasons());
        m.put("slug", tenant.getSlug());
        m.put("publicSitePublished", publicSitePublished(tenant.getId()));
        m.put("practitioners", practitioners.findByTenantIdOrderByFullNameAsc(tenant.getId()).stream().map(this::practitionerView).toList());
        m.put("catalog", DirectoryCatalog.asMap());
        return m;
    }

    static Map<String, Object> profileFields(DirectoryProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("practiceAreas", JsonLists.strings(p.getPracticeAreasJson()));
        m.put("cities", JsonLists.strings(p.getCitiesJson()));
        m.put("provinces", JsonLists.strings(p.getProvincesJson()));
        m.put("languages", JsonLists.strings(p.getLanguagesJson()));
        m.put("consultOptions", JsonLists.strings(p.getConsultOptionsJson()));
        m.put("fixedConsultFee", p.getFixedConsultFee());
        m.put("responseTime", p.getResponseTime());
        m.put("bio", p.getBio() == null ? "" : p.getBio());
        return m;
    }

    Map<String, Object> practitionerView(PractitionerRegistration p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("fullName", p.getFullName());
        m.put("lpcNumber", p.getLpcNumber());
        m.put("fidelityExpiresOn", p.getFidelityExpiresOn());
        m.put("fidelityCurrent", p.getFidelityExpiresOn() != null && !p.getFidelityExpiresOn().isBefore(today()));
        m.put("status", p.getStatus());
        m.put("verificationNote", p.getVerificationNote());
        m.put("verifiedAt", p.getVerifiedAt());
        return m;
    }

    // ---- Operator verification ----------------------------------------------------------------

    public List<Map<String, Object>> verificationQueue(String status) {
        requireSuperadmin();
        String s = status == null || status.isBlank() ? "pending" : status.trim().toLowerCase(Locale.ROOT);
        List<PractitionerRegistration> rows = "all".equals(s)
                ? practitioners.findAll()
                : practitioners.findByStatusOrderByCreatedAtAsc(s);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PractitionerRegistration p : rows) {
            Tenant t = tenants.findById(p.getTenantId()).orElse(null);
            if (t == null) continue;
            Map<String, Object> m = practitionerView(p);
            m.put("tenantId", t.getId());
            m.put("firmName", t.getFirmName());
            m.put("firmSlug", t.getSlug());
            m.put("firmCity", t.getCity());
            m.put("firmListedSwitch", switchedOn(t.getId()));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> verify(UUID id, Map<String, Object> body) {
        requireSuperadmin();
        PractitionerRegistration p = practitioners.findById(id).orElseThrow(() -> ApiException.notFound("Practitioner not found"));
        String decision = text(body == null ? null : body.get("decision")).toLowerCase(Locale.ROOT);
        String note = text(body == null ? null : body.get("note"));
        if (note.length() > 500) throw ApiException.badRequest("Note is too long");
        switch (decision) {
            case "verified", "verify", "approve", "approved" -> {
                p.setStatus("verified");
                p.setVerifiedAt(Instant.now());
                p.setVerifiedBy(TenantContext.getUserId());
            }
            case "rejected", "reject" -> {
                p.setStatus("rejected");
                p.setVerifiedAt(null);
                p.setVerifiedBy(TenantContext.getUserId());
            }
            default -> throw ApiException.badRequest("Decision must be verified or rejected");
        }
        p.setVerificationNote(note.isEmpty() ? null : note);
        p.setUpdatedAt(Instant.now());
        practitioners.save(p);
        audit.recordFor(p.getTenantId(), "directory.practitioner." + p.getStatus(), "practitioner_registration", p.getId().toString(),
                p.getLpcNumber() + (note.isEmpty() ? "" : " · " + note));
        return practitionerView(p);
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private static List<String> slugs(Object raw, Function<String, Optional<DirectoryCatalog.Option>> lookup, String what) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        if (!(raw instanceof List<?> list)) throw ApiException.badRequest("Send a list of " + what + " values.");
        for (Object o : list) {
            String s = text(o);
            DirectoryCatalog.Option opt = lookup.apply(s).orElseThrow(() -> ApiException.badRequest("Unknown " + what + ": " + s));
            if (!out.contains(opt.slug())) out.add(opt.slug());
        }
        return out;
    }

    static String text(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        return "null".equals(s) ? "" : s;
    }

    private void requireTenantAdmin() {
        String role = TenantContext.getRole();
        if (role == null || !TENANT_ADMINS.contains(role.toLowerCase(Locale.ROOT))) {
            throw ApiException.forbidden("Only a firm owner or partner can change the directory listing.");
        }
        TenantContext.requireTenant();
    }

    static void requireSuperadmin() {
        String role = TenantContext.getRole();
        if (role == null || !"superadmin".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("LPC verification is limited to the Owlaw operator.");
        }
    }

    private Tenant currentTenant() {
        return tenants.findById(TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Firm not found"));
    }
}
