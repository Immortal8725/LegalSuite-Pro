package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppModule;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LandingPage;
import com.legalsuite.domain.Plan;
import com.legalsuite.domain.RefreshToken;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantModule;
import com.legalsuite.dto.AuthDtos.LoginRequest;
import com.legalsuite.dto.AuthDtos.PortalLoginRequest;
import com.legalsuite.dto.AuthDtos.RegisterRequest;
import com.legalsuite.repo.AppModuleRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LandingPageRepository;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.RefreshTokenRepository;
import com.legalsuite.repo.TenantModuleRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PlanRepository plans;
    private final AppModuleRepository modules;
    private final TenantModuleRepository tenantModules;
    private final LandingPageRepository landingPages;
    private final ClientRepository clients;
    private final JwtService jwt;
    private final PasswordEncoder encoder;

    public AuthService(
            TenantRepository tenants,
            AppUserRepository users,
            RefreshTokenRepository refreshTokens,
            PlanRepository plans,
            AppModuleRepository modules,
            TenantModuleRepository tenantModules,
            LandingPageRepository landingPages,
            ClientRepository clients,
            JwtService jwt,
            PasswordEncoder encoder) {
        this.tenants = tenants;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.plans = plans;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.landingPages = landingPages;
        this.clients = clients;
        this.jwt = jwt;
        this.encoder = encoder;
    }

    @Transactional
    public Map<String, Object> login(LoginRequest req) {
        Tenant tenant = tenants.findBySlug(req.firmSlug().toLowerCase(Locale.ROOT).trim())
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        if (!List.of("active", "trial").contains(tenant.getStatus())) {
            throw ApiException.forbidden("Firm account is " + tenant.getStatus());
        }
        AppUser user = users.findByTenantIdAndEmailIgnoreCase(tenant.getId(), req.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!"active".equals(user.getStatus())) {
            throw ApiException.forbidden("User account is " + user.getStatus());
        }
        if (user.isTotpEnabled()) {
            String code = req.totpCode();
            if (code == null || code.isBlank()) {
                Map<String, Object> challenge = new HashMap<>();
                challenge.put("requiresTotp", true);
                challenge.put("email", user.getEmail());
                return challenge;
            }
            if (!Totp.verify(user.getTotpSecret(), code)) {
                throw ApiException.unauthorized("Invalid authenticator code");
            }
        }
        user.setLastLoginAt(Instant.now());
        user.setOnlineStatus("online");
        users.save(user);
        return tokens(user, tenant);
    }

    @Transactional
    public Map<String, Object> totpStart() {
        AppUser user = users.findByIdAndTenantId(TenantContext.requireUser(), TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isTotpEnabled()) {
            throw ApiException.badRequest("Authenticator is already enabled");
        }
        String secret = Totp.newSecret();
        user.setTotpSecret(secret);
        users.save(user);
        Map<String, Object> m = new HashMap<>();
        m.put("secret", secret);
        m.put("otpauthUrl", Totp.otpauthUrl(user.getEmail(), secret));
        m.put("totpEnabled", false);
        return m;
    }

    @Transactional
    public Map<String, Object> totpConfirm(Map<String, Object> body) {
        AppUser user = users.findByIdAndTenantId(TenantContext.requireUser(), TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            throw ApiException.badRequest("Start authenticator enrolment first");
        }
        String code = body == null || body.get("code") == null ? "" : String.valueOf(body.get("code"));
        if (!Totp.verify(user.getTotpSecret(), code)) {
            throw ApiException.badRequest("Invalid authenticator code");
        }
        user.setTotpEnabled(true);
        users.save(user);
        return Map.of("totpEnabled", true);
    }

    @Transactional
    public Map<String, Object> totpDisable(Map<String, Object> body) {
        AppUser user = users.findByIdAndTenantId(TenantContext.requireUser(), TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!user.isTotpEnabled()) {
            return Map.of("totpEnabled", false);
        }
        String code = body == null || body.get("code") == null ? "" : String.valueOf(body.get("code"));
        String password = body == null || body.get("password") == null ? "" : String.valueOf(body.get("password"));
        boolean okCode = Totp.verify(user.getTotpSecret(), code);
        boolean okPassword = !password.isBlank() && encoder.matches(password, user.getPasswordHash());
        if (!okCode && !okPassword) {
            throw ApiException.unauthorized("Enter the current authenticator code or your password");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        users.save(user);
        return Map.of("totpEnabled", false);
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        String slug = req.firmSlug() == null || req.firmSlug().isBlank()
                ? slugify(req.firmName())
                : slugify(req.firmSlug());
        if (tenants.existsBySlug(slug)) {
            throw ApiException.conflict("Firm slug '" + slug + "' is already taken");
        }
        Plan plan = plans.findBySlug(req.planSlug() == null ? "free" : req.planSlug())
                .orElseGet(() -> plans.findBySlug("free").orElseThrow());
        Tenant tenant = new Tenant();
        tenant.setFirmName(req.firmName());
        tenant.setSlug(slug);
        tenant.setEmail(req.email());
        tenant.setPhone(req.phone());
        tenant.setPlanId(plan.getId());
        tenant.setStatus("trial");
        tenant.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
        tenant.setPracticeAreasJson(JsonLists.toJson(req.practiceAreas()));
        String country = req.country() == null || req.country().isBlank() ? "ZA" : req.country().trim();
        tenant.setCountry(country);
        if (req.state() != null && !req.state().isBlank()) tenant.setState(req.state());
        else if ("ZA".equalsIgnoreCase(country)) tenant.setState("GP");
        if (req.city() != null && !req.city().isBlank()) tenant.setCity(req.city());
        tenant.setTagline("Trusted counsel for every chapter of your case.");
        tenant = tenants.save(tenant);

        AppUser admin = new AppUser();
        admin.setTenantId(tenant.getId());
        admin.setEmail(req.email());
        admin.setPasswordHash(encoder.encode(req.password()));
        admin.setFirstName(req.firstName());
        admin.setLastName(req.lastName());
        admin.setPhone(req.phone());
        admin.setRole("owner");
        admin.setTitle("Managing Partner");
        admin.setStatus("active");
        admin.setEmailVerified(true);
        admin.setHourlyRate(new java.math.BigDecimal("350"));
        admin = users.save(admin);

        for (AppModule module : modules.findByActiveTrueOrderBySortOrderAsc()) {
            if (module.isCore()) {
                TenantModule tm = new TenantModule();
                tm.setTenantId(tenant.getId());
                tm.setModuleId(module.getId());
                tm.setEnabled(true);
                tenantModules.save(tm);
            }
        }

        LandingPage page = new LandingPage();
        page.setTenantId(tenant.getId());
        page.setTemplate("classic");
        page.setHeroTitle(req.firmName());
        page.setHeroSubtitle("Clear advice. Relentless advocacy. A firm that answers the phone.");
        page.setAboutText(req.firmName()
                + " represents individuals and businesses with the same care a trusted counselor would give family.");
        page.setColorsJson("{\"primary\":\"#1a365d\",\"accent\":\"#c6a052\"}");
        page.setSeoTitle(req.firmName() + " | Law Firm");
        page.setPublished(true);
        landingPages.save(page);

        return tokens(admin, tenant);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByToken(refreshToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokens.delete(stored);
            throw ApiException.unauthorized("Refresh token expired");
        }
        AppUser user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        Tenant tenant = tenants.findById(user.getTenantId())
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        refreshTokens.delete(stored);
        return tokens(user, tenant);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokens.deleteByUserId(userId);
        users.findById(userId).ifPresent(u -> {
            u.setOnlineStatus("offline");
            users.save(u);
        });
    }

    public boolean slugAvailable(String slug) {
        return !tenants.existsBySlug(slugify(slug));
    }

    public Map<String, Object> me() {
        AppUser user = users.findByIdAndTenantId(TenantContext.requireUser(), TenantContext.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        Map<String, Object> body = new HashMap<>();
        body.put("user", userView(user));
        body.put("tenant", tenantView(tenant));
        return body;
    }

    @Transactional
    public Map<String, Object> portalLogin(PortalLoginRequest req) {
        Tenant tenant = tenants.findBySlug(slugify(req.firmSlug()))
                .orElseThrow(() -> ApiException.notFound("Firm not found"));
        Client client = clients.findByTenantIdAndEmailIgnoreCase(tenant.getId(), req.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid portal credentials"));
        if (!client.isPortalEnabled() || client.getPortalPasswordHash() == null
                || !encoder.matches(req.password(), client.getPortalPasswordHash())) {
            throw ApiException.unauthorized("Invalid portal credentials");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("client", Map.of(
                "id", client.getId(),
                "name", client.displayName(),
                "email", client.getEmail() == null ? "" : client.getEmail(),
                "tenantId", tenant.getId(),
                "firmName", tenant.getFirmName(),
                "slug", tenant.getSlug()));
        out.put("accessToken", jwt.portalToken(client, tenant));
        out.put("refreshToken", jwt.portalToken(client, tenant));
        out.put("role", "client");
        out.put("user", Map.of(
                "id", client.getId(),
                "email", client.getEmail() == null ? "" : client.getEmail(),
                "firstName", client.getFirstName() == null ? client.displayName() : client.getFirstName(),
                "lastName", client.getLastName() == null ? "" : client.getLastName(),
                "fullName", client.displayName(),
                "initials", client.displayName().isBlank() ? "CL" : client.displayName().substring(0, 1).toUpperCase(),
                "role", "client"));
        out.put("tenant", tenantView(tenant));
        return out;
    }

    private Map<String, Object> tokens(AppUser user, Tenant tenant) {
        String access = jwt.accessToken(user);
        String refresh = jwt.refreshToken(user);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setToken(refresh);
        rt.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokens.save(rt);
        Map<String, Object> out = new HashMap<>();
        out.put("accessToken", access);
        out.put("refreshToken", refresh);
        out.put("tokenType", "Bearer");
        out.put("expiresIn", jwt.getAccessMs() / 1000);
        out.put("user", userView(user));
        out.put("tenant", tenantView(tenant));
        return out;
    }

    public Map<String, Object> userView(AppUser user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("email", user.getEmail());
        m.put("firstName", user.getFirstName());
        m.put("lastName", user.getLastName());
        m.put("fullName", user.getFullName());
        m.put("initials", user.getInitials());
        m.put("role", user.getRole());
        m.put("title", user.getTitle());
        m.put("phone", user.getPhone());
        m.put("hourlyRate", user.getHourlyRate());
        m.put("onlineStatus", user.getOnlineStatus());
        m.put("barNumber", user.getBarNumber());
        m.put("totpEnabled", user.isTotpEnabled());
        return m;
    }

    public Map<String, Object> tenantView(Tenant tenant) {
        List<String> enabled = tenantModules.findByTenantId(tenant.getId()).stream()
                .filter(TenantModule::isEnabled)
                .map(tm -> modules.findById(tm.getModuleId()).map(AppModule::getSlug).orElse(null))
                .filter(s -> s != null)
                .toList();
        Plan plan = tenant.getPlanId() == null ? null : plans.findById(tenant.getPlanId()).orElse(null);
        Map<String, Object> m = new HashMap<>();
        m.put("id", tenant.getId());
        m.put("firmName", tenant.getFirmName());
        m.put("slug", tenant.getSlug());
        m.put("logoUrl", tenant.getLogoUrl());
        m.put("planSlug", plan == null ? "free" : plan.getSlug());
        m.put("status", tenant.getStatus());
        m.put("onboardingCompleted", tenant.isOnboardingCompleted());
        m.put("enabledModules", enabled);
        m.put("phone", tenant.getPhone());
        m.put("email", tenant.getEmail());
        m.put("website", tenant.getWebsite());
        m.put("addressLine1", tenant.getAddressLine1());
        m.put("city", tenant.getCity());
        m.put("state", tenant.getState());
        m.put("zip", tenant.getZip());
        m.put("country", tenant.getCountry());
        m.put("jurisdiction", DocketEngine.of(tenant));
        m.put("currency", DocketEngine.currency(tenant));
        m.put("trustLabel", DocketEngine.trustLabel(tenant));
        m.put("tagline", tenant.getTagline());
        m.put("practiceAreas", JsonLists.strings(tenant.getPracticeAreasJson()));
        m.put("ffcNumber", tenant.getFfcNumber());
        m.put("ffcExpiresOn", tenant.getFfcExpiresOn());
        m.put("ffcHolderName", tenant.getFfcHolderName());
        m.put("ffcCurrent", Compliance.ffcCurrent(tenant));
        m.put("bankFeedImportedAt", tenant.getBankFeedImportedAt());
        m.put("lastBankFeedSource", tenant.getLastBankFeedSource());
        m.put("informationOfficerName", tenant.getInformationOfficerName());
        m.put("informationOfficerEmail", tenant.getInformationOfficerEmail());
        m.put("paiaManualBody", tenant.getPaiaManualBody());
        m.put("popiaOperatorAcknowledged", tenant.isPopiaOperatorAcknowledged());
        m.put("popiaReady", Compliance.popiaReady(tenant));
        return m;
    }

    public static String slugify(String input) {
        String base = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        return base.isBlank() ? "firm" : base;
    }
}
