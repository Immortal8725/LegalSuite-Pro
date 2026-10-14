package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.PasswordSetupToken;
import com.legalsuite.domain.Tenant;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.mail.SmtpProperties;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.PasswordSetupTokenRepository;
import com.legalsuite.repo.RefreshTokenRepository;
import com.legalsuite.repo.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite delivery. With SMTP configured the invitee gets a one-time set-password link by email.
 * Without SMTP, or when delivery fails, the inviting admin gets a one-time temporary password and
 * the same link to pass on by hand. Either way the user must choose their own password.
 */
@Service
public class InviteService {
    private static final Logger log = LoggerFactory.getLogger(InviteService.class);
    static final Duration LINK_LIFETIME = Duration.ofHours(72);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordSetupTokenRepository setupTokens;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final RefreshTokenRepository refreshTokens;
    private final MailGateway mail;
    private final SmtpProperties smtp;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final AuditService audit;
    private final String appUrl;

    public InviteService(
            PasswordSetupTokenRepository setupTokens,
            AppUserRepository users,
            TenantRepository tenants,
            RefreshTokenRepository refreshTokens,
            MailGateway mail,
            SmtpProperties smtp,
            PasswordEncoder encoder,
            AuthService auth,
            AuditService audit,
            @Value("${legalsuite.app-url:http://localhost:3000}") String appUrl) {
        this.setupTokens = setupTokens;
        this.users = users;
        this.tenants = tenants;
        this.refreshTokens = refreshTokens;
        this.mail = mail;
        this.smtp = smtp;
        this.encoder = encoder;
        this.auth = auth;
        this.audit = audit;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /** Issue a fresh link (older unused links for this user stop working) and try to email it. */
    @Transactional
    public Map<String, Object> deliver(AppUser user, String temporaryPassword) {
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow(() -> ApiException.notFound("Firm not found"));
        Instant now = Instant.now();
        for (PasswordSetupToken old : setupTokens.findByUserIdAndUsedAtIsNull(user.getId())) {
            old.setUsedAt(now);
            setupTokens.save(old);
        }
        String raw = randomToken();
        PasswordSetupToken row = new PasswordSetupToken();
        row.setTenantId(user.getTenantId());
        row.setUserId(user.getId());
        row.setTokenHash(sha256(raw));
        row.setExpiresAt(now.plus(LINK_LIFETIME));
        setupTokens.save(row);
        String link = appUrl + "/set-password?token=" + raw;

        Map<String, Object> out = new HashMap<>();
        out.put("inviteLinkExpiresAt", row.getExpiresAt());
        boolean sent = false;
        String reason = null;
        if (mail.configured()) {
            try {
                mail.send(smtp.getFrom(), user.getEmail(), subject(tenant), body(user, tenant, link));
                sent = true;
            } catch (ApiException ex) {
                reason = ex.getMessage();
                log.warn("Invite email to user {} was not delivered", user.getId());
            }
        } else {
            reason = "Email is not set up on this server (SMTP_HOST and SMTP_FROM), so no invite email was sent.";
        }
        out.put("inviteEmailSent", sent);
        if (!sent) {
            // Fallback: the inviting admin passes these on. Shown once, never stored in clear.
            out.put("inviteEmailProblem", reason);
            out.put("temporaryPassword", temporaryPassword);
            out.put("inviteLink", link);
        }
        audit.record(sent ? "user.invite_emailed" : "user.invite_fallback", "user", user.getId().toString(), user.getEmail());
        return out;
    }

    /** Public: what the set-password page shows before the user chooses a password. */
    public Map<String, Object> describe(String rawToken) {
        PasswordSetupToken row = usable(rawToken);
        AppUser user = users.findById(row.getUserId()).orElseThrow(InviteService::invalid);
        Tenant tenant = tenants.findById(row.getTenantId()).orElseThrow(InviteService::invalid);
        Map<String, Object> m = new HashMap<>();
        m.put("email", user.getEmail());
        m.put("firstName", user.getFirstName());
        m.put("firmName", tenant.getFirmName());
        m.put("firmSlug", tenant.getSlug());
        m.put("expiresAt", row.getExpiresAt());
        return m;
    }

    /** Public: set the password from the link and sign the user in. The link works once. */
    @Transactional
    public Map<String, Object> accept(Map<String, Object> body) {
        String raw = body == null || body.get("token") == null ? "" : String.valueOf(body.get("token"));
        String next = body == null || body.get("newPassword") == null ? "" : String.valueOf(body.get("newPassword"));
        PasswordSetupToken row = usable(raw);
        if (next.length() < 10) {
            throw ApiException.badRequest("Choose a password of at least 10 characters.");
        }
        AppUser user = users.findById(row.getUserId()).orElseThrow(InviteService::invalid);
        Tenant tenant = tenants.findById(row.getTenantId()).orElseThrow(InviteService::invalid);
        if (!"active".equalsIgnoreCase(String.valueOf(user.getStatus()))) {
            throw invalid();
        }
        user.setPasswordHash(encoder.encode(next));
        user.setPasswordChangeRequired(false);
        users.save(user);
        row.setUsedAt(Instant.now());
        setupTokens.save(row);
        refreshTokens.deleteByUserId(user.getId());
        audit.recordFor(tenant.getId(), "user.password_set", "user", user.getId().toString(), user.getEmail() + " from invite link");
        return auth.tokens(user, tenant);
    }

    private PasswordSetupToken usable(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) throw invalid();
        PasswordSetupToken row = setupTokens.findByTokenHash(sha256(raw.trim())).orElseThrow(InviteService::invalid);
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) throw invalid();
        return row;
    }

    private static ApiException invalid() {
        return ApiException.badRequest("This link has expired or was already used. Ask your firm admin to resend the invite.");
    }

    private static String subject(Tenant tenant) {
        return "You have been invited to " + tenant.getFirmName() + " on LegalSuite";
    }

    private static String body(AppUser user, Tenant tenant, String link) {
        String first = user.getFirstName() == null || user.getFirstName().isBlank() ? "there" : user.getFirstName();
        return "Hi " + first + ",\n\n"
                + tenant.getFirmName() + " has added you to LegalSuite.\n\n"
                + "Choose your password here (the link works once and expires in 72 hours):\n"
                + link + "\n\n"
                + "Your sign-in email is " + user.getEmail() + " and the firm code is " + tenant.getSlug() + ".\n\n"
                + "If you were not expecting this, you can ignore this email.\n";
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
