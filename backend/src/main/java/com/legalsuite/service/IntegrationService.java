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
                    "description", "Card payments on invoices. Connect in production with a restricted key. The local demo stores the toggle only."),
            Map.of("provider", "twilio", "name", "Twilio", "category", "voice",
                    "description", "PSTN minutes and SMS. In-app WebRTC stays free; this is only for the public network."),
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

    public IntegrationService(ConnectedIntegrationRepository integrations, AuditService audit) {
        this.integrations = integrations;
        this.audit = audit;
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
            row.put("connected", connected);
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
            row.setStatusNote("Connected in this workspace. Live credentials are not required for the local demo.");
        } else {
            row.setStatusNote("Disconnected");
        }
        integrations.save(row);
        audit.record(connected ? "integration.connect" : "integration.disconnect", "integration", slug, slug);
        return Map.of("provider", slug, "connected", connected, "statusNote", row.getStatusNote());
    }
}
