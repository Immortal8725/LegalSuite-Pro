package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.ConnectedIntegration;
import com.legalsuite.repo.ConnectedIntegrationRepository;
import com.legalsuite.voice.TwilioProperties;
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
                    "description", "Card payments on invoices. Connect in production with a restricted key — local demo stores the toggle only."),
            Map.of("provider", "twilio", "name", "Twilio", "category", "voice",
                    "description", "Rent a local number or verify the office landline, then dial out on the public network with that caller ID. Credentials stay in the server environment. This toggle does not store a password."),
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
    private final TwilioProperties twilio;

    public IntegrationService(ConnectedIntegrationRepository integrations, AuditService audit, TwilioProperties twilio) {
        this.integrations = integrations;
        this.audit = audit;
        this.twilio = twilio;
    }

    public boolean isConnected(String provider) {
        return integrations.findByTenantIdAndProvider(TenantContext.requireTenant(), provider.toLowerCase())
                .map(ConnectedIntegration::isConnected)
                .orElse(false);
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
            if ("twilio".equals(item.get("provider"))) {
                row.put("credentialsPresent", twilio.configured());
                row.put("publicBaseUrlSet", twilio.hasPublicBaseUrl());
            }
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
            if ("twilio".equals(slug)) {
                row.setStatusNote(twilio.configured()
                        ? "Connected. Credentials stay in the server environment, not in this database."
                        : "Toggle saved. Add TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN on the server before a real call can leave the firm.");
            } else {
                row.setStatusNote("Connected in this workspace. Live credentials are not required for the local demo.");
            }
        } else {
            row.setStatusNote("Disconnected");
        }
        integrations.save(row);
        audit.record(connected ? "integration.connect" : "integration.disconnect", "integration", slug, slug);
        return Map.of("provider", slug, "connected", connected, "statusNote", row.getStatusNote());
    }
}
