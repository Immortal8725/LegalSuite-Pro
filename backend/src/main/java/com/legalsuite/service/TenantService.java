package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppModule;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.LandingPage;
import com.legalsuite.domain.Plan;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantModule;
import com.legalsuite.repo.AppModuleRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.LandingPageRepository;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.TenantModuleRepository;
import com.legalsuite.repo.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final TenantRepository tenants;
    private final PlanRepository plans;
    private final AppModuleRepository modules;
    private final TenantModuleRepository tenantModules;
    private final AppUserRepository users;
    private final LandingPageRepository landingPages;
    private final PasswordEncoder encoder;
    private final AuthService authService;

    public TenantService(
            TenantRepository tenants,
            PlanRepository plans,
            AppModuleRepository modules,
            TenantModuleRepository tenantModules,
            AppUserRepository users,
            LandingPageRepository landingPages,
            PasswordEncoder encoder,
            AuthService authService) {
        this.tenants = tenants;
        this.plans = plans;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.users = users;
        this.landingPages = landingPages;
        this.encoder = encoder;
        this.authService = authService;
    }

    public List<Map<String, Object>> plans() {
        return plans.findByActiveTrueOrderBySortOrderAsc().stream().map(this::planView).toList();
    }

    public List<Map<String, Object>> modules() {
        return modules.findByActiveTrueOrderBySortOrderAsc().stream().map(this::moduleView).toList();
    }

    public List<Map<String, Object>> tenantModules(UUID tenantId) {
        assertTenant(tenantId);
        Map<UUID, AppModule> byId = new HashMap<>();
        modules.findAll().forEach(m -> byId.put(m.getId(), m));
        return tenantModules.findByTenantId(tenantId).stream().map(tm -> {
            AppModule mod = byId.get(tm.getModuleId());
            Map<String, Object> m = moduleView(mod);
            m.put("enabled", tm.isEnabled());
            m.put("tenantModuleId", tm.getId());
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> toggleModule(UUID tenantId, UUID moduleId, boolean enabled) {
        assertTenant(tenantId);
        AppModule module = modules.findById(moduleId).orElseThrow(() -> ApiException.notFound("Module not found"));
        if (module.isCore() && !enabled) {
            throw ApiException.badRequest("Core modules stay on. Upgrade or keep using them.");
        }
        TenantModule tm = tenantModules.findByTenantIdAndModuleId(tenantId, moduleId)
                .orElseGet(() -> {
                    TenantModule created = new TenantModule();
                    created.setTenantId(tenantId);
                    created.setModuleId(moduleId);
                    return created;
                });
        tm.setEnabled(enabled);
        tenantModules.save(tm);
        return Map.of("module", module.getSlug(), "enabled", enabled);
    }

    @Transactional
    public Map<String, Object> updateFirm(Map<String, Object> body) {
        Tenant tenant = tenants.findById(TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (body.get("firmName") != null) tenant.setFirmName(String.valueOf(body.get("firmName")));
        if (body.get("phone") != null) tenant.setPhone(String.valueOf(body.get("phone")));
        if (body.get("email") != null) tenant.setEmail(String.valueOf(body.get("email")));
        if (body.get("website") != null) tenant.setWebsite(String.valueOf(body.get("website")));
        if (body.get("addressLine1") != null) tenant.setAddressLine1(String.valueOf(body.get("addressLine1")));
        if (body.get("city") != null) tenant.setCity(String.valueOf(body.get("city")));
        if (body.get("state") != null) tenant.setState(String.valueOf(body.get("state")));
        if (body.get("zip") != null) tenant.setZip(String.valueOf(body.get("zip")));
        if (body.get("country") != null) tenant.setCountry(String.valueOf(body.get("country")));
        if (body.get("tagline") != null) tenant.setTagline(String.valueOf(body.get("tagline")));
        if (body.get("onboardingCompleted") != null) {
            tenant.setOnboardingCompleted(Boolean.parseBoolean(String.valueOf(body.get("onboardingCompleted"))));
        }
        if (body.get("practiceAreas") instanceof List<?> list) {
            tenant.setPracticeAreasJson(JsonLists.toJson(list));
        }
        if (body.get("ffcNumber") != null) tenant.setFfcNumber(blankToNull(String.valueOf(body.get("ffcNumber"))));
        if (body.get("ffcExpiresOn") != null) tenant.setFfcExpiresOn(TexasDocketRules.parseDate(body.get("ffcExpiresOn")));
        if (body.get("ffcHolderName") != null) tenant.setFfcHolderName(blankToNull(String.valueOf(body.get("ffcHolderName"))));
        if (body.get("informationOfficerName") != null) {
            tenant.setInformationOfficerName(blankToNull(String.valueOf(body.get("informationOfficerName"))));
        }
        if (body.get("informationOfficerEmail") != null) {
            tenant.setInformationOfficerEmail(blankToNull(String.valueOf(body.get("informationOfficerEmail"))));
        }
        if (body.get("popiaOperatorAcknowledged") != null) {
            tenant.setPopiaOperatorAcknowledged(Boolean.parseBoolean(String.valueOf(body.get("popiaOperatorAcknowledged"))));
        }
        tenant.setUpdatedAt(Instant.now());
        tenants.save(tenant);
        if (body.get("template") != null || body.get("heroSubtitle") != null) {
            LandingPage page = landingPages.findByTenantId(tenant.getId()).orElseGet(() -> {
                LandingPage p = new LandingPage();
                p.setTenantId(tenant.getId());
                return p;
            });
            if (body.get("template") != null) page.setTemplate(String.valueOf(body.get("template")));
            if (body.get("heroSubtitle") != null) page.setHeroSubtitle(String.valueOf(body.get("heroSubtitle")));
            page.setHeroTitle(tenant.getFirmName());
            page.setUpdatedAt(Instant.now());
            landingPages.save(page);
        }
        return authService.tenantView(tenant);
    }

    @Transactional
    public Map<String, Object> generatePaia() {
        Tenant tenant = tenants.findById(TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (tenant.getInformationOfficerName() == null || tenant.getInformationOfficerName().isBlank()) {
            throw ApiException.badRequest("Appoint an information officer before generating the PAIA manual.");
        }
        tenant.setPaiaManualBody(PaiaManual.generate(tenant));
        tenant.setUpdatedAt(Instant.now());
        tenants.save(tenant);
        return authService.tenantView(tenant);
    }

    public List<Map<String, Object>> team() {
        return users.findByTenantIdOrderByLastNameAsc(TenantContext.requireTenant()).stream()
                .map(authService::userView)
                .toList();
    }

    @Transactional
    public Map<String, Object> invite(Map<String, Object> body) {
        UUID tenantId = TenantContext.requireTenant();
        String email = String.valueOf(body.get("email"));
        if (users.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
            throw ApiException.conflict("A user with that email already exists");
        }
        rejectPlatformRole(body.get("role"));
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setFirstName(String.valueOf(body.getOrDefault("firstName", "New")));
        user.setLastName(String.valueOf(body.getOrDefault("lastName", "Hire")));
        user.setRole(String.valueOf(body.getOrDefault("role", "associate")));
        user.setTitle(body.get("title") == null ? null : String.valueOf(body.get("title")));
        user.setStatus("active");
        user.setPasswordHash(encoder.encode(body.get("password") == null ? "changeme123" : String.valueOf(body.get("password"))));
        if (body.get("hourlyRate") != null) {
            user.setHourlyRate(new BigDecimal(String.valueOf(body.get("hourlyRate"))));
        }
        users.save(user);
        return authService.userView(user);
    }

    @Transactional
    public Map<String, Object> updateUser(UUID id, Map<String, Object> body) {
        AppUser user = users.findByIdAndTenantId(id, TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        rejectPlatformRole(body.get("role"));
        if (body.get("role") != null) user.setRole(String.valueOf(body.get("role")));
        if (body.get("status") != null) user.setStatus(String.valueOf(body.get("status")));
        if (body.get("title") != null) user.setTitle(String.valueOf(body.get("title")));
        if (body.get("hourlyRate") != null) user.setHourlyRate(new BigDecimal(String.valueOf(body.get("hourlyRate"))));
        users.save(user);
        return authService.userView(user);
    }

    private static void rejectPlatformRole(Object role) {
        if (role != null && "superadmin".equalsIgnoreCase(String.valueOf(role).trim())) {
            throw ApiException.forbidden("That role is reserved for the platform operator.");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() || "null".equals(v) ? null : v.trim();
    }

    private void assertTenant(UUID tenantId) {
        if (!TenantContext.requireTenant().equals(tenantId)) {
            throw ApiException.forbidden("Cannot modify another firm");
        }
    }

    private Map<String, Object> planView(Plan p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("slug", p.getSlug());
        m.put("description", p.getDescription());
        m.put("priceMonthly", p.getPriceMonthly());
        m.put("maxUsers", p.getMaxUsers());
        m.put("maxCases", p.getMaxCases());
        m.put("includedVoiceMinutes", Pricing.capIncludedMinutes(p.getIncludedVoiceMinutes()));
        m.put("currency", "ZAR");
        m.put("phoneBilling", "pay-what-you-use");
        m.put("didMonthly", Pricing.DID_MONTHLY_ZAR);
        m.put("trustIncluded", true);
        m.put("features", JsonLists.map(p.getFeaturesJson()));
        return m;
    }

    private Map<String, Object> moduleView(AppModule p) {
        Map<String, Object> m = new HashMap<>();
        if (p == null) return m;
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("slug", p.getSlug());
        m.put("description", p.getDescription());
        m.put("category", p.getCategory());
        m.put("icon", p.getIcon());
        m.put("priceMonthly", p.getPriceMonthly());
        m.put("core", p.isCore());
        m.put("minPlanSlug", p.getMinPlanSlug());
        return m;
    }
}
