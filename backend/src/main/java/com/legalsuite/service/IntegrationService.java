package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.ConnectedIntegration;
import com.legalsuite.repo.ConnectedIntegrationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationService {
    private static final List<Map<String, String>> CATALOG = List.of(
            Map.of("provider", "stripe", "name", "Stripe", "category", "payments",
                    "description", "Subscriptions and usage invoices. The toggle does not store a card or a secret. Live keys belong in the environment."),
            Map.of("provider", "twilio", "name", "Twilio", "category", "voice",
                    "description", "Public-network calls and an optional local number. In-app calls stay on the seat. Keys belong in the environment, not in this app."),
            Map.of("provider", "google_calendar", "name", "Google Calendar", "category", "calendar",
                    "description", "Two-way hearings and deadlines. OAuth is mocked locally."),
            Map.of("provider", "dropbox", "name", "Dropbox", "category", "documents",
                    "description", "Mirror the cabinet to a firm Dropbox folder."),
            Map.of("provider", "quickbooks", "name", "QuickBooks", "category", "accounting",
                    "description", "Push paid invoices and trust deposits to the books."),
            Map.of("provider", "clio", "name", "Clio import", "category", "migration",
                    "description", "One-time matter and contact import. No live sync.")
    );

    private final ConnectedIntegrationRepository integrations;
    private final AuditService audit;
    private final OperatorCredentials credentials;

    public IntegrationService(
            ConnectedIntegrationRepository integrations,
            AuditService audit,
            OperatorCredentials credentials) {
        this.integrations = integrations;
        this.audit = audit;
        this.credentials = credentials;
    }

    public List<Map<String, Object>> list() {
        Map<String, ConnectedIntegration> byProvider = new LinkedHashMap<>();
        integrations.findByTenantIdOrderByProviderAsc(TenantContext.requireTenant())
                .forEach(row -> byProvider.put(row.getProvider(), row));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, String> item : CATALOG) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            ConnectedIntegration saved = byProvider.get(item.get("provider"));
            boolean connected = saved != null && saved.isConnected();
            boolean live = credentials.live(item.get("provider"));
            row.put("connected", connected);
            row.put("liveCredentialsPresent", live);
            row.put("statusNote", saved == null ? "Not connected" : saved.getStatusNote());
            row.put("connectedAt", saved == null ? null : saved.getConnectedAt());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> setConnected(String provider, boolean connected) {
        String slug = provider.toLowerCase();
        boolean known = CATALOG.stream().anyMatch(p -> p.get("provider").equals(slug));
        if (!known) {
            throw com.legalsuite.common.ApiException.notFound("Unknown integration");
        }
        ConnectedIntegration row = integrations.findByTenantIdAndProvider(TenantContext.requireTenant(), slug)
                .orElseGet(() -> {
                    ConnectedIntegration created = new ConnectedIntegration();
                    created.setTenantId(TenantContext.requireTenant());
                    created.setProvider(slug);
                    return created;
                });
        row.setConnected(connected);
        row.setUpdatedAt(Instant.now());
        if (connected) {
            row.setConnectedAt(Instant.now());
            if (("stripe".equals(slug) || "twilio".equals(slug)) && !credentials.live(slug)) {
                row.setStatusNote("Preference saved. Live keys are not on this process. Set them in the environment. Do not paste secrets into the app.");
            } else {
                row.setStatusNote("Marked connected on this workspace.");
            }
        } else {
            row.setStatusNote("Disconnected");
        }
        integrations.save(row);
        audit.record(connected ? "integration.connect" : "integration.disconnect", "integration", slug, slug);
        return Map.of("provider", slug, "connected", connected, "statusNote", row.getStatusNote());
    }
}
