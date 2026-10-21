package com.legalsuite.directory;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppNotification;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TenantPublicSite;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.TenantPublicSiteRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.CommsService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public side of Find a Lawyer: search, firm pages and enquiries, plus the operator's listing controls.
 * No ratings, no paid placement and no per-enquiry fee: order is a daily rotation so no firm sits on top.
 */
@Service
public class DirectoryPublicService {
    public static final String CONSENT_VERSION = "dir-2026-10";
    public static final Duration ENQUIRY_RETENTION = Duration.ofDays(90);
    static final int PAGE_SIZE = 20;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> NOTIFY_ROLES = List.of("owner", "partner", "director", "attorney");

    private final DirectoryService directory;
    private final TenantRepository tenants;
    private final TenantPublicSiteRepository sites;
    private final DirectoryProfileRepository profiles;
    private final PractitionerRegistrationRepository practitioners;
    private final DirectoryEnquiryRepository enquiries;
    private final DirectoryDailyStatRepository stats;
    private final LeadRepository leads;
    private final AppUserRepository users;
    private final AppNotificationRepository notifications;
    private final CommsService comms;
    private final AuditService audit;

    public DirectoryPublicService(
            DirectoryService directory,
            TenantRepository tenants,
            TenantPublicSiteRepository sites,
            DirectoryProfileRepository profiles,
            PractitionerRegistrationRepository practitioners,
            DirectoryEnquiryRepository enquiries,
            DirectoryDailyStatRepository stats,
            LeadRepository leads,
            AppUserRepository users,
            AppNotificationRepository notifications,
            CommsService comms,
            AuditService audit) {
        this.directory = directory;
        this.tenants = tenants;
        this.sites = sites;
        this.profiles = profiles;
        this.practitioners = practitioners;
        this.enquiries = enquiries;
        this.stats = stats;
        this.leads = leads;
        this.users = users;
        this.notifications = notifications;
        this.comms = comms;
        this.audit = audit;
    }

    // ---- Search -------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> search(String area, String province, String city, String language, Integer page) {
        String a = optSlug(area, DirectoryCatalog::area, "practice area");
        String p = optSlug(province, DirectoryCatalog::province, "province");
        String c = city == null || city.isBlank() ? null
                : DirectoryCatalog.city(city).map(DirectoryCatalog.City::slug)
                        .orElseThrow(() -> ApiException.badRequest("Unknown city: " + city));
        String l = optSlug(language, DirectoryCatalog::language, "language");
        LocalDate day = directory.today();
        List<Map<String, Object>> hits = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (DirectoryProfile prof : profiles.findAll()) {
            List<String> areas = JsonLists.strings(prof.getPracticeAreasJson());
            List<String> provinces = JsonLists.strings(prof.getProvincesJson());
            List<String> cities = JsonLists.strings(prof.getCitiesJson());
            List<String> langs = JsonLists.strings(prof.getLanguagesJson());
            if (a != null && !areas.contains(a)) continue;
            if (p != null && !provinces.contains(p)) continue;
            if (c != null && !cities.contains(c)) continue;
            if (l != null && !langs.contains(l)) continue;
            Tenant t = tenants.findById(prof.getTenantId()).orElse(null);
            if (t == null || !directory.listed(t)) continue;
            hits.add(card(t, prof));
            ids.add(t.getId());
        }
        // Daily rotation: fair, repeatable within a day, and not something a firm can buy.
        hits.sort(Comparator.comparingInt(m -> Math.floorMod((m.get("slug") + "|" + day).hashCode(), 1_000_003)));
        int total = hits.size();
        int pg = page == null || page < 1 ? 1 : page;
        int from = Math.min(total, (pg - 1) * PAGE_SIZE);
        List<Map<String, Object>> slice = hits.subList(from, Math.min(total, from + PAGE_SIZE));
        for (Map<String, Object> m : slice) bump((UUID) m.remove("tenantId"), Stat.APPEARANCE);
        for (Map<String, Object> m : hits) m.remove("tenantId");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", new ArrayList<>(slice));
        out.put("total", total);
        out.put("page", pg);
        out.put("pageSize", PAGE_SIZE);
        out.put("order", "Listings rotate daily. Owlaw does not rank firms, sell placement or show ratings.");
        out.put("catalog", DirectoryCatalog.asMap());
        return out;
    }

    private Map<String, Object> card(Tenant t, DirectoryProfile prof) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", t.getId());
        m.put("slug", t.getSlug());
        m.put("firmName", publicName(t));
        m.put("practiceAreas", labels(DirectoryCatalog.PRACTICE_AREAS, prof.getPracticeAreasJson()));
        m.put("cities", JsonLists.strings(prof.getCitiesJson()).stream()
                .map(s -> DirectoryCatalog.city(s).map(DirectoryCatalog.City::name).orElse(s)).toList());
        m.put("languages", labels(DirectoryCatalog.LANGUAGES, prof.getLanguagesJson()));
        m.put("consultOptions", labels(DirectoryCatalog.CONSULT_OPTIONS, prof.getConsultOptionsJson()));
        m.put("responseTime", prof.getResponseTime() == null ? null : DirectoryCatalog.label(DirectoryCatalog.RESPONSE_TIMES, prof.getResponseTime()));
        m.put("verifiedPractitioners", (int) practitioners.findByTenantIdOrderByFullNameAsc(t.getId()).stream()
                .filter(directory::practitionerCurrent).count());
        return m;
    }

    // ---- Firm page ----------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> firm(String slug) {
        Tenant t = directory.listedTenant(slug).orElseThrow(() -> ApiException.notFound("This firm is not listed in Find a Lawyer."));
        DirectoryProfile prof = profiles.findByTenantId(t.getId()).orElseThrow(() -> ApiException.notFound("Firm not found"));
        Map<String, Object> m = card(t, prof);
        m.remove("tenantId");
        m.put("practiceAreaOptions", JsonLists.strings(prof.getPracticeAreasJson()).stream()
                .map(s -> Map.of("slug", s, "label", DirectoryCatalog.label(DirectoryCatalog.PRACTICE_AREAS, s))).toList());
        m.put("provinces", labels(DirectoryCatalog.PROVINCES, prof.getProvincesJson()));
        m.put("bio", prof.getBio() == null ? "" : prof.getBio());
        m.put("fixedConsultFee", prof.getFixedConsultFee());
        m.put("phone", t.getPhone());
        m.put("practitioners", practitioners.findByTenantIdOrderByFullNameAsc(t.getId()).stream()
                .filter(directory::practitionerCurrent)
                .map(p -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("fullName", p.getFullName());
                    x.put("lpcNumber", p.getLpcNumber());
                    x.put("verifiedOn", p.getVerifiedAt() == null ? null : p.getVerifiedAt().atZone(DirectoryService.SA).toLocalDate());
                    return x;
                }).toList());
        m.put("publicSite", directory.publicSitePublished(t.getId()) ? "/site/" + t.getSlug() : null);
        m.put("lpcRegisterUrl", "https://lpc.org.za/");
        m.put("consentVersion", CONSENT_VERSION);
        m.put("consentText", consentText(publicName(t)));
        bump(t.getId(), Stat.VIEW);
        return m;
    }

    static String consentText(String firm) {
        return "I agree that Owlaw may pass my details and this message to " + firm
                + " so that the firm can check for conflicts of interest and reply to me. "
                + "The firm will keep my details as required by POPIA. If I do not become a client, the enquiry is deleted after 90 days.";
    }

    // ---- Enquiry ------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> enquire(String slug, Map<String, Object> body) {
        Tenant t = directory.listedTenant(slug).orElseThrow(() -> ApiException.notFound("This firm is not listed in Find a Lawyer."));
        Map<String, Object> b = body == null ? Map.of() : body;
        if (!DirectoryService.text(b.get("website")).isEmpty()) {
            // Honeypot: a person never fills this hidden field. Answer as if received.
            return received(t);
        }
        Object consent = b.get("consent");
        if (!(Boolean.TRUE.equals(consent) || "true".equalsIgnoreCase(String.valueOf(consent)))) {
            throw ApiException.badRequest("Tick the consent box so the firm can receive your enquiry. The box starts unticked.");
        }
        String name = clip(DirectoryService.text(b.get("name")), 120);
        String email = DirectoryService.text(b.get("email")).toLowerCase(Locale.ROOT);
        String phone = clip(DirectoryService.text(b.get("phone")), 32);
        String message = DirectoryService.text(b.get("message"));
        String opposing = clip(DirectoryService.text(b.get("opposingParty")), 200);
        if (name.length() < 2) throw ApiException.badRequest("Enter your name.");
        if (email.isEmpty() && phone.isEmpty()) throw ApiException.badRequest("Enter an email address or a phone number.");
        if (!email.isEmpty() && (!EMAIL.matcher(email).matches() || email.length() > 200)) throw ApiException.badRequest("Enter a valid email address.");
        if (!phone.isEmpty() && !phone.matches("[+0-9 ()-]{7,32}")) throw ApiException.badRequest("Enter a valid phone number.");
        if (message.length() < 10) throw ApiException.badRequest("Tell the firm a little about your matter.");
        if (message.length() > 2000) throw ApiException.badRequest("Keep the message under 2000 characters.");
        DirectoryProfile prof = profiles.findByTenantId(t.getId()).orElseThrow();
        String area = DirectoryService.text(b.get("practiceArea")).toLowerCase(Locale.ROOT);
        if (!area.isEmpty() && !JsonLists.strings(prof.getPracticeAreasJson()).contains(area)) {
            throw ApiException.badRequest("Choose one of the firm's practice areas.");
        }

        Lead lead = new Lead();
        lead.setTenantId(t.getId());
        lead.setName(name);
        lead.setEmail(email.isEmpty() ? null : email);
        lead.setPhone(phone.isEmpty() ? null : phone);
        lead.setCaseType(area.isEmpty() ? "Find a Lawyer enquiry" : DirectoryCatalog.label(DirectoryCatalog.PRACTICE_AREAS, area));
        lead.setDescription("[Find a Lawyer] " + message);
        lead.setOpposingParty(opposing.isEmpty() ? null : opposing);
        lead.setStatus("new");
        leads.save(lead);

        Map<String, Object> conflict = conflictCheckAs(t, lead);
        DirectoryEnquiry e = new DirectoryEnquiry();
        e.setTenantId(t.getId());
        e.setLeadId(lead.getId());
        e.setPracticeArea(area.isEmpty() ? null : area);
        if (conflict != null) {
            e.setConflictCheckId((UUID) conflict.get("id"));
            e.setConflictStatus(String.valueOf(conflict.get("status")));
        }
        Instant now = Instant.now();
        e.setConsentAt(now);
        e.setConsentVersion(CONSENT_VERSION);
        e.setCreatedAt(now);
        e.setPurgeAfter(now.plus(ENQUIRY_RETENTION));
        enquiries.save(e);

        String flag = "potential_conflict".equals(e.getConflictStatus()) ? " Conflict check found a possible match: review before replying." : "";
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(t.getId())) {
            if (!NOTIFY_ROLES.contains(u.getRole()) || !"active".equals(u.getStatus())) continue;
            AppNotification n = new AppNotification();
            n.setTenantId(t.getId());
            n.setUserId(u.getId());
            n.setTitle("New Find a Lawyer enquiry");
            n.setBody(name + " asked about " + lead.getCaseType() + "." + flag);
            n.setType("intake");
            n.setLink("/leads");
            notifications.save(n);
        }
        bump(t.getId(), Stat.ENQUIRY);
        audit.recordFor(t.getId(), "directory.enquiry.received", "lead", lead.getId().toString(),
                "consent " + CONSENT_VERSION + (e.getConflictStatus() == null ? "" : " · conflict " + e.getConflictStatus()));
        Map<String, Object> out = received(t);
        out.put("id", lead.getId());
        return out;
    }

    private Map<String, Object> received(Tenant t) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "received");
        out.put("message", "Thank you. " + publicName(t) + " has your enquiry and will reply to you directly. Owlaw does not charge you for this.");
        return out;
    }

    /** Runs the firm's own conflict check on the enquirer and the other party, as the firm's first admin. */
    private Map<String, Object> conflictCheckAs(Tenant t, Lead lead) {
        Optional<AppUser> actor = users.findByTenantIdOrderByLastNameAsc(t.getId()).stream()
                .filter(u -> List.of("owner", "partner", "director").contains(u.getRole()))
                .findFirst()
                .or(() -> users.findByTenantIdOrderByLastNameAsc(t.getId()).stream().findFirst());
        if (actor.isEmpty()) return null;
        UUID prevTenant = TenantContext.getTenantId();
        UUID prevUser = TenantContext.getUserId();
        try {
            TenantContext.setTenantId(t.getId());
            TenantContext.setUserId(actor.get().getId());
            Map<String, Object> extra = new LinkedHashMap<>();
            if (lead.getOpposingParty() != null) extra.put("opposingParty", lead.getOpposingParty());
            if (lead.getEmail() != null) extra.put("email", lead.getEmail());
            return comms.conflictCheck(lead.getName(), extra);
        } finally {
            TenantContext.setTenantId(prevTenant);
            TenantContext.setUserId(prevUser);
        }
    }

    /** For the firm's intake screen: which leads came from the directory and their conflict result. */
    public Map<String, Object> enquiryFor(UUID leadId) {
        UUID tid = TenantContext.requireTenant();
        DirectoryEnquiry e = enquiries.findByLeadId(leadId).filter(x -> tid.equals(x.getTenantId()))
                .orElseThrow(() -> ApiException.notFound("Not a directory enquiry"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leadId", e.getLeadId());
        m.put("conflictCheckId", e.getConflictCheckId());
        m.put("conflictStatus", e.getConflictStatus());
        m.put("consentAt", e.getConsentAt());
        m.put("consentVersion", e.getConsentVersion());
        m.put("purgeAfter", e.getPurgeAfter());
        return m;
    }

    /** POPIA: enquiries that never became clients are deleted after the retention period. */
    @Scheduled(cron = "0 15 2 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public int purgeExpired() {
        int n = 0;
        for (DirectoryEnquiry e : enquiries.findByPurgeAfterBefore(Instant.now())) {
            Lead lead = leads.findById(e.getLeadId()).orElse(null);
            if (lead != null && (lead.getCaseId() != null || "converted".equals(lead.getStatus()) || "retained".equals(lead.getStatus()))) {
                e.setPurgeAfter(null);
                enquiries.save(e);
                continue;
            }
            if (lead != null) leads.delete(lead);
            enquiries.delete(e);
            n++;
        }
        return n;
    }

    // ---- Operator ------------------------------------------------------------------------------

    public List<Map<String, Object>> operatorListings() {
        DirectoryService.requireSuperadmin();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DirectoryProfile prof : profiles.findAll()) {
            Tenant t = tenants.findById(prof.getTenantId()).orElse(null);
            if (t == null) continue;
            DirectoryService.Listing listing = directory.listing(t);
            Map<String, Object> m = new LinkedHashMap<>(DirectoryService.profileFields(prof));
            m.put("tenantId", t.getId());
            m.put("firmName", t.getFirmName());
            m.put("slug", t.getSlug());
            m.put("plan", t.getStatus());
            m.put("listedSwitch", directory.switchedOn(t.getId()));
            m.put("listed", listing.listed());
            m.put("reasons", listing.reasons());
            m.put("practitioners", practitioners.findByTenantIdOrderByFullNameAsc(t.getId()).stream().map(directory::practitionerView).toList());
            LocalDate from = directory.today().minusDays(29);
            int views = 0, appearances = 0, enq = 0;
            for (DirectoryDailyStat s : stats.findByTenantIdAndStatDayGreaterThanEqualOrderByStatDayAsc(t.getId(), from)) {
                views += s.getProfileViews();
                appearances += s.getSearchAppearances();
                enq += s.getEnquiries();
            }
            m.put("last30Days", Map.of("profileViews", views, "searchAppearances", appearances, "enquiries", enq));
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("firmName")).toLowerCase(Locale.ROOT)));
        return out;
    }

    @Transactional
    public Map<String, Object> setHidden(UUID tenantId, Map<String, Object> body) {
        DirectoryService.requireSuperadmin();
        Tenant t = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Firm not found"));
        DirectoryProfile prof = directory.ensureProfile(t);
        Object raw = body == null ? null : body.get("hidden");
        boolean hide = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
        String reason = clip(DirectoryService.text(body == null ? null : body.get("reason")), 500);
        if (hide && reason.isEmpty()) throw ApiException.badRequest("Give a reason. The firm sees it.");
        prof.setOperatorHidden(hide);
        prof.setHiddenReason(hide ? reason : null);
        prof.setHiddenAt(hide ? Instant.now() : null);
        prof.setUpdatedAt(Instant.now());
        profiles.save(prof);
        audit.recordFor(t.getId(), hide ? "directory.listing.hidden" : "directory.listing.restored", "directory_profile",
                prof.getId().toString(), hide ? reason : "restored");
        Map<String, Object> m = new LinkedHashMap<>(DirectoryService.profileFields(prof));
        DirectoryService.Listing listing = directory.listing(t);
        m.put("tenantId", t.getId());
        m.put("listed", listing.listed());
        m.put("reasons", listing.reasons());
        return m;
    }

    // ---- Helpers ------------------------------------------------------------------------------

    enum Stat { VIEW, APPEARANCE, ENQUIRY }

    private void bump(UUID tenantId, Stat stat) {
        if (tenantId == null) return;
        LocalDate day = directory.today();
        DirectoryDailyStat s = stats.findByTenantIdAndStatDay(tenantId, day).orElseGet(() -> {
            DirectoryDailyStat x = new DirectoryDailyStat();
            x.setTenantId(tenantId);
            x.setStatDay(day);
            return x;
        });
        switch (stat) {
            case VIEW -> s.setProfileViews(s.getProfileViews() + 1);
            case APPEARANCE -> s.setSearchAppearances(s.getSearchAppearances() + 1);
            case ENQUIRY -> s.setEnquiries(s.getEnquiries() + 1);
        }
        stats.save(s);
    }

    private String publicName(Tenant t) {
        return sites.findByTenantId(t.getId())
                .filter(s -> "published".equals(s.getPublishStatus()))
                .map(TenantPublicSite::getLiveFirmName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(t.getFirmName());
    }

    private static List<String> labels(List<DirectoryCatalog.Option> list, String json) {
        return JsonLists.strings(json).stream().map(s -> DirectoryCatalog.label(list, s)).toList();
    }

    private static String optSlug(String raw, java.util.function.Function<String, Optional<DirectoryCatalog.Option>> lookup, String what) {
        if (raw == null || raw.isBlank()) return null;
        return lookup.apply(raw).map(DirectoryCatalog.Option::slug).orElseThrow(() -> ApiException.badRequest("Unknown " + what + ": " + raw));
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
