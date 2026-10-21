package com.legalsuite.platform;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.TenantContext;
import com.legalsuite.directory.DirectoryPublicService;
import com.legalsuite.directory.DirectoryService;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.AuthService;
import com.legalsuite.service.InviteService;
import com.legalsuite.service.PublicSiteService;
import com.legalsuite.service.TenantService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owlaw support staff (role "support") and the single approvals inbox.
 * Support staff sign in with compulsory two-factor sign-in and can only work approval queues. The operator
 * (superadmin) invites and removes them. Every change lands in the platform log.
 */
@RestController
public class SupportStaffController {
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder encoder;
    private final InviteService invites;
    private final AuthService auth;
    private final AuditService audit;
    private final PublicSiteService sites;
    private final DirectoryService directory;
    private final DirectoryPublicService listings;

    public SupportStaffController(AppUserRepository users, TenantRepository tenants, PasswordEncoder encoder, InviteService invites,
            AuthService auth, AuditService audit, PublicSiteService sites, DirectoryService directory, DirectoryPublicService listings) {
        this.users = users;
        this.tenants = tenants;
        this.encoder = encoder;
        this.invites = invites;
        this.auth = auth;
        this.audit = audit;
        this.sites = sites;
        this.directory = directory;
        this.listings = listings;
    }

    private Tenant platformFirm() {
        return tenants.findBySlug(PlatformGuard.PLATFORM_SLUG)
                .orElseGet(() -> tenants.findById(TenantContext.requireTenant()).orElseThrow(() -> ApiException.notFound("Platform firm not found")));
    }

    @GetMapping("/api/v1/platform/support-staff")
    public ApiResponse<?> list() {
        PlatformGuard.requireSuperadmin();
        return ApiResponse.ok(users.findByTenantIdOrderByLastNameAsc(platformFirm().getId()).stream()
                .filter(u -> PlatformGuard.SUPPORT.equalsIgnoreCase(u.getRole()))
                .map(auth::userView).toList());
    }

    @Transactional
    @PostMapping("/api/v1/platform/support-staff")
    public ApiResponse<?> invite(@RequestBody Map<String, Object> body) {
        PlatformGuard.requireSuperadmin();
        Tenant firm = platformFirm();
        String email = String.valueOf(body.getOrDefault("email", "")).trim();
        if (!email.contains("@")) throw ApiException.badRequest("Enter the support person's email address.");
        if (users.existsByTenantIdAndEmailIgnoreCase(firm.getId(), email)) throw ApiException.conflict("That person already has an Owlaw staff account.");
        AppUser u = new AppUser();
        u.setTenantId(firm.getId());
        u.setEmail(email);
        u.setFirstName(String.valueOf(body.getOrDefault("firstName", "Support")).trim());
        u.setLastName(String.valueOf(body.getOrDefault("lastName", "Staff")).trim());
        u.setRole(PlatformGuard.SUPPORT);
        u.setTitle("Owlaw support");
        u.setStatus("active");
        String temporary = TenantService.temporaryPassword();
        u.setPasswordHash(encoder.encode(temporary));
        u.setPasswordChangeRequired(true);
        u.setPublicProfile(false);
        users.save(u);
        audit.platform(null, "platform.support_staff.invited", "user", u.getId().toString(), email);
        Map<String, Object> view = new HashMap<>(auth.userView(u));
        view.putAll(invites.deliver(u, temporary));
        return ApiResponse.ok(view, "Support staff invited. They must set up two-factor sign-in at first sign-in.");
    }

    @Transactional
    @PostMapping("/api/v1/platform/support-staff/{id}/remove")
    public ApiResponse<?> remove(@PathVariable UUID id) {
        PlatformGuard.requireSuperadmin();
        AppUser u = users.findByIdAndTenantId(id, platformFirm().getId()).orElseThrow(() -> ApiException.notFound("Support staff member not found"));
        if (!PlatformGuard.SUPPORT.equalsIgnoreCase(u.getRole())) throw ApiException.badRequest("Only support staff accounts can be removed here.");
        u.setStatus("inactive");
        users.save(u);
        audit.platform(null, "platform.support_staff.removed", "user", u.getId().toString(), u.getEmail());
        return ApiResponse.ok(auth.userView(u), "Support staff removed");
    }

    /** One inbox for every pending approval, with counts. Superadmin and support staff. */
    @GetMapping("/api/v1/platform/approvals")
    public ApiResponse<?> approvals() {
        PlatformGuard.requireApprover();
        List<Map<String, Object>> site = sites.queue();
        List<Map<String, Object>> lpc = directory.verificationQueue("pending");
        List<Map<String, Object>> dir = listings.operatorListings();
        long hidden = dir.stream().filter(m -> Boolean.TRUE.equals(m.get("operatorHidden"))).count();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("publicSites", site.size());
        counts.put("practitioners", lpc.size());
        counts.put("listings", dir.size());
        counts.put("hiddenListings", hidden);
        counts.put("total", site.size() + lpc.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("publicSites", site);
        out.put("practitioners", lpc);
        out.put("role", TenantContext.getRole());
        return ApiResponse.ok(out);
    }
}
