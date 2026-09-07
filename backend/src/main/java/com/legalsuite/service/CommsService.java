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
        lead.setStatus("new");
        leads.save(lead);
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
        return leads.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenant()).stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("name", l.getName());
            m.put("email", l.getEmail());
            m.put("phone", l.getPhone());
            m.put("caseType", l.getCaseType());
            m.put("description", l.getDescription());
            m.put("status", l.getStatus());
            m.put("createdAt", l.getCreatedAt());
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> conflictCheck(String name) {
        String q = name.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Client c : clients.findByTenantIdOrderByLastNameAsc(TenantContext.requireTenant())) {
            if (c.displayName().toLowerCase(Locale.ROOT).contains(q) || (c.getEmail() != null && c.getEmail().toLowerCase(Locale.ROOT).contains(q))) {
                matches.add(Map.of("type", "client", "name", c.displayName(), "detail", c.getEmail() == null ? "" : c.getEmail(), "confidence", 0.92));
            }
        }
        for (Contact c : contacts.findByTenantIdOrderByLastNameAsc(TenantContext.requireTenant())) {
            String n = ((c.getFirstName() == null ? "" : c.getFirstName()) + " " + (c.getLastName() == null ? "" : c.getLastName())).trim();
            if (n.toLowerCase(Locale.ROOT).contains(q) || (c.getCompany() != null && c.getCompany().toLowerCase(Locale.ROOT).contains(q))) {
                matches.add(Map.of("type", c.getType(), "name", n, "detail", c.getCompany() == null ? "" : c.getCompany(), "confidence", 0.8));
            }
        }
        for (LegalCase c : cases.findByTenantIdOrderByUpdatedAtDesc(TenantContext.requireTenant())) {
            if ((c.getOpposingParty() != null && c.getOpposingParty().toLowerCase(Locale.ROOT).contains(q))
                    || (c.getTitle() != null && c.getTitle().toLowerCase(Locale.ROOT).contains(q))) {
                matches.add(Map.of("type", "case", "name", c.getTitle(), "detail", c.getCaseNumber(), "confidence", 0.7));
            }
        }
        ConflictCheck rec = new ConflictCheck();
        rec.setTenantId(TenantContext.requireTenant());
        rec.setSearchName(name);
        rec.setMatchCount(matches.size());
        rec.setStatus(matches.isEmpty() ? "clear" : "potential_conflict");
        rec.setResultsJson(JsonLists.toJson(matches));
        rec.setCreatedBy(TenantContext.requireUser());
        conflicts.save(rec);
        Map<String, Object> out = new HashMap<>();
        out.put("id", rec.getId());
        out.put("searchName", name);
        out.put("status", rec.getStatus());
        out.put("matchCount", matches.size());
        out.put("matches", matches);
        return out;
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
