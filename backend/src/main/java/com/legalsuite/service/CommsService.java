package com.legalsuite.service;

import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppNotification;
import com.legalsuite.domain.ChatMessage;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.ConflictCheck;
import com.legalsuite.domain.Contact;
import com.legalsuite.domain.Conversation;
import com.legalsuite.domain.LandingPage;
import com.legalsuite.domain.Lead;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppNotificationRepository;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ChatMessageRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.ConflictCheckRepository;
import com.legalsuite.repo.ContactRepository;
import com.legalsuite.repo.ConversationRepository;
import com.legalsuite.repo.LandingPageRepository;
import com.legalsuite.repo.LeadRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommsService {
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final AppNotificationRepository notifications;
    private final LandingPageRepository landingPages;
    private final TenantRepository tenants;
    private final LeadRepository leads;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final LegalCaseRepository cases;
    private final ConflictCheckRepository conflicts;
    private final AppUserRepository users;
    private final PracticeService practice;
    private final AuthService auth;

    public CommsService(
            ConversationRepository conversations,
            ChatMessageRepository messages,
            AppNotificationRepository notifications,
            LandingPageRepository landingPages,
            TenantRepository tenants,
            LeadRepository leads,
            ClientRepository clients,
            ContactRepository contacts,
            LegalCaseRepository cases,
            ConflictCheckRepository conflicts,
            AppUserRepository users,
            PracticeService practice,
            AuthService auth) {
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
        this.landingPages = landingPages;
        this.tenants = tenants;
        this.leads = leads;
        this.clients = clients;
        this.contacts = contacts;
        this.cases = cases;
        this.conflicts = conflicts;
        this.users = users;
        this.practice = practice;
        this.auth = auth;
    }

    @Transactional
    public Map<String, Object> createConversation(Map<String, Object> body) {
        Conversation c = new Conversation();
        c.setTenantId(TenantContext.requireTenant());
        c.setTitle(String.valueOf(body.getOrDefault("title", "Team chat")));
        c.setType(String.valueOf(body.getOrDefault("type", "direct")));
        conversations.save(c);
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("title", c.getTitle());
        m.put("type", c.getType());
        m.put("messages", List.of());
        return m;
    }

    public List<Map<String, Object>> conversations() {
        return conversations.findByTenantIdOrderByLastMessageAtDesc(TenantContext.requireTenant()).stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("title", c.getTitle());
                    m.put("type", c.getType());
                    m.put("lastMessageAt", c.getLastMessageAt());
                    m.put("messages", messages.findByConversationIdOrderByCreatedAtAsc(c.getId()).stream().map(msg -> Map.of(
                            "id", msg.getId(),
                            "senderId", msg.getSenderId(),
                            "body", msg.getBody(),
                            "createdAt", msg.getCreatedAt()
                    )).toList());
                    return m;
                }).toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(UUID conversationId, String body) {
        Conversation conv = (conversationId == null || conversationId.getLeastSignificantBits() == 0)
                ? conversations.findByTenantIdOrderByLastMessageAtDesc(TenantContext.requireTenant()).stream().findFirst()
                        .orElseGet(() -> {
                            Conversation c = new Conversation();
                            c.setTenantId(TenantContext.requireTenant());
                            c.setTitle("Team chat");
                            c.setType("direct");
                            return conversations.save(c);
                        })
                : conversations.findById(conversationId).orElseThrow(() -> com.legalsuite.common.ApiException.notFound("Conversation not found"));
        ChatMessage msg = new ChatMessage();
        msg.setTenantId(TenantContext.requireTenant());
        msg.setConversationId(conv.getId());
        msg.setSenderId(TenantContext.requireUser());
        msg.setBody(body);
        messages.save(msg);
        conv.setLastMessageAt(Instant.now());
        conversations.save(conv);
        return Map.of("id", msg.getId(), "body", msg.getBody(), "createdAt", msg.getCreatedAt(), "senderId", msg.getSenderId());
    }

    public List<Map<String, Object>> notifications() {
        return notifications.findByTenantIdAndUserIdOrderByCreatedAtDesc(TenantContext.requireTenant(), TenantContext.requireUser())
                .stream().map(n -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("body", n.getBody());
                    m.put("type", n.getType());
                    m.put("read", n.isRead());
                    m.put("createdAt", n.getCreatedAt());
                    m.put("link", n.getLink());
                    return m;
                }).toList();
    }

    @Transactional
    public void markRead(UUID id) {
        notifications.findById(id).ifPresent(n -> {
            n.setRead(true);
            notifications.save(n);
        });
    }

    public Map<String, Object> publicLanding(String slug) {
        Tenant tenant = tenants.findBySlug(slug).orElseThrow(() -> com.legalsuite.common.ApiException.notFound("Firm not found"));
        LandingPage page = landingPages.findByTenantId(tenant.getId()).orElse(new LandingPage());
        Map<String, Object> m = new HashMap<>();
        m.put("tenant", auth.tenantView(tenant));
        m.put("template", page.getTemplate());
        m.put("heroTitle", page.getHeroTitle() == null ? tenant.getFirmName() : page.getHeroTitle());
        m.put("heroSubtitle", page.getHeroSubtitle());
        m.put("aboutText", page.getAboutText());
        m.put("colors", JsonLists.map(page.getColorsJson()));
        m.put("attorneys", users.findByTenantIdOrderByLastNameAsc(tenant.getId()).stream()
                .filter(u -> List.of("owner", "partner", "attorney", "associate").contains(u.getRole()))
                .map(auth::userView).toList());
        m.put("practiceAreas", JsonLists.strings(tenant.getPracticeAreasJson()));
        return m;
    }

    @Transactional
    public Map<String, Object> intake(String slug, Map<String, Object> body) {
        Tenant tenant = tenants.findBySlug(slug).orElseThrow(() -> com.legalsuite.common.ApiException.notFound("Firm not found"));
        Lead lead = new Lead();
        lead.setTenantId(tenant.getId());
        lead.setName(String.valueOf(body.getOrDefault("name", "")));
        lead.setEmail(String.valueOf(body.getOrDefault("email", "")));
        lead.setPhone(body.get("phone") == null ? null : String.valueOf(body.get("phone")));
        lead.setCaseType(body.get("caseType") == null ? null : String.valueOf(body.get("caseType")));
        lead.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        lead.setOpposingParty(body.get("opposingParty") == null ? null : String.valueOf(body.get("opposingParty")));
        lead.setAccrualDate(TexasDocketRules.parseDate(body.get("accrualDate")));
        lead.setDateOfBirth(TexasDocketRules.parseDate(body.get("dateOfBirth")));
        lead.setGovernmentalDefendant(TexasDocketRules.bool(body.get("governmentalDefendant")));
        lead.setStatus("new");
        leads.save(lead);
        users.findByTenantIdOrderByLastNameAsc(tenant.getId()).stream()
                .filter(u -> List.of("owner", "partner", "attorney").contains(u.getRole()))
                .limit(3)
                .forEach(u -> {
                    AppNotification n = new AppNotification();
                    n.setTenantId(tenant.getId());
                    n.setUserId(u.getId());
                    n.setTitle("New consult from the website");
                    n.setBody(lead.getName() + " asked about " + (lead.getCaseType() == null ? "a matter" : lead.getCaseType()) + ".");
                    n.setType("intake");
                    n.setLink("/leads");
                    notifications.save(n);
                });
        return Map.of("id", lead.getId(), "status", "received",
                "message", "Thank you. A member of " + tenant.getFirmName() + " will reach out shortly.");
    }

    @Transactional
    public Map<String, Object> updateLead(UUID id, Map<String, Object> body) {
        Lead lead = leads.findByIdAndTenantId(id, TenantContext.requireTenant())
                .orElseThrow(() -> com.legalsuite.common.ApiException.notFound("Lead not found"));
        if (body.get("status") != null) lead.setStatus(String.valueOf(body.get("status")));
        leads.save(lead);
        Map<String, Object> m = new HashMap<>();
        m.put("id", lead.getId());
        m.put("name", lead.getName());
        m.put("status", lead.getStatus());
        return m;
    }

    public List<Map<String, Object>> leads() {
        return leads.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenant()).stream().map(this::leadView).toList();
    }

    public Map<String, Object> leadView(Lead l) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("name", l.getName());
        m.put("email", l.getEmail());
        m.put("phone", l.getPhone());
        m.put("caseType", l.getCaseType());
        m.put("description", l.getDescription());
        m.put("opposingParty", l.getOpposingParty());
        m.put("accrualDate", l.getAccrualDate());
        m.put("dateOfBirth", l.getDateOfBirth());
        m.put("governmentalDefendant", l.isGovernmentalDefendant());
        m.put("status", l.getStatus());
        m.put("createdAt", l.getCreatedAt());
        m.put("docket", TexasDocketRules.compute(RetainService.factsFromLead(l)).asMap());
        return m;
    }

    @Transactional
    public Map<String, Object> conflictCheck(String name) {
        return conflictCheck(name, Map.of());
    }

    @Transactional
    public Map<String, Object> conflictCheck(String name, Map<String, ?> extra) {
        UUID tid = TenantContext.requireTenant();
        List<String> queries = new ArrayList<>();
        if (name != null && !name.isBlank()) queries.add(name);
        if (extra != null) {
            Object opp = extra.get("opposingParty");
            if (opp != null && !String.valueOf(opp).isBlank() && !"null".equals(String.valueOf(opp))) {
                queries.add(String.valueOf(opp));
            }
            Object email = extra.get("email");
            if (email != null && String.valueOf(email).contains("@")) {
                queries.add(String.valueOf(email));
            }
        }
        List<ConflictEngine.Party> index = new ArrayList<>();
        for (Client c : clients.findByTenantIdOrderByLastNameAsc(tid)) {
            index.add(new ConflictEngine.Party(
                    "client", c.displayName(), c.getEmail(), c.getLastName(), c.getFirstName(),
                    c.getCompanyName(), null, null));
        }
        for (Contact c : contacts.findByTenantIdOrderByLastNameAsc(tid)) {
            String display = ((c.getFirstName() == null ? "" : c.getFirstName()) + " " + (c.getLastName() == null ? "" : c.getLastName())).trim();
            if (display.isBlank()) display = c.getCompany() == null ? "" : c.getCompany();
            String role = "opposing_counsel".equals(c.getType()) ? "counsel" : ("opposing_party".equals(c.getType()) ? "adverse" : "contact");
            index.add(new ConflictEngine.Party(role, display, c.getEmail(), c.getLastName(), c.getFirstName(), c.getCompany(), null, null));
        }
        for (LegalCase c : cases.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            if (c.getOpposingParty() != null && !c.getOpposingParty().isBlank()) {
                index.add(new ConflictEngine.Party(
                        "adverse", c.getOpposingParty(), null, null, null, c.getOpposingParty(),
                        c.getCaseNumber(), c.getTitle()));
            }
            if (c.getOpposingCounsel() != null && !c.getOpposingCounsel().isBlank()) {
                index.add(new ConflictEngine.Party(
                        "counsel", c.getOpposingCounsel(), null, lastToken(c.getOpposingCounsel()), null, null,
                        c.getCaseNumber(), c.getTitle()));
            }
        }
        List<Map<String, Object>> matches = ConflictEngine.search(queries, index);
        String searchLabel = String.join(" · ", queries);
        ConflictCheck rec = new ConflictCheck();
        rec.setTenantId(tid);
        rec.setSearchName(searchLabel.isBlank() ? (name == null ? "" : name) : searchLabel);
        rec.setMatchCount(matches.size());
        rec.setStatus(matches.isEmpty() ? "clear" : "potential_conflict");
        rec.setResultsJson(JsonLists.toJson(matches));
        rec.setCreatedBy(TenantContext.requireUser());
        conflicts.save(rec);
        Map<String, Object> out = new HashMap<>();
        out.put("id", rec.getId());
        out.put("searchName", rec.getSearchName());
        out.put("status", rec.getStatus());
        out.put("matchCount", matches.size());
        out.put("matches", matches);
        return out;
    }

    private static String lastToken(String name) {
        if (name == null || name.isBlank()) return null;
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    @Transactional
    public Map<String, Object> clearConflict(UUID id) {
        ConflictCheck rec = conflicts.findByIdAndTenantId(id, TenantContext.requireTenant())
                .orElseThrow(() -> com.legalsuite.common.ApiException.notFound("Conflict check not found"));
        rec.setStatus("cleared");
        conflicts.save(rec);
        Map<String, Object> m = new HashMap<>();
        m.put("id", rec.getId());
        m.put("searchName", rec.getSearchName());
        m.put("status", rec.getStatus());
        m.put("matchCount", rec.getMatchCount());
        return m;
    }

    public List<Map<String, Object>> conflictHistory() {
        return conflicts.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenant()).stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("searchName", c.getSearchName());
            m.put("status", c.getStatus());
            m.put("matchCount", c.getMatchCount());
            m.put("createdAt", c.getCreatedAt());
            return m;
        }).toList();
    }

    public Map<String, Object> portalCases(UUID clientId, UUID tenantId) {
        return Map.of("client", practice.clientView(clients.findByIdAndTenantId(clientId, tenantId).orElseThrow()),
                "cases", cases.findByTenantIdAndClientId(tenantId, clientId).stream().map(practice::caseView).toList());
    }
}
