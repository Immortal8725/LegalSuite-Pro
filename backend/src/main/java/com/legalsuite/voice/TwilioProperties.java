package com.legalsuite.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Twilio credentials come only from the process environment.
 * They are never accepted from the staff UI and never written to the database.
 */
@Component
@ConfigurationProperties(prefix = "legalsuite.twilio")
public class TwilioProperties {
    private String accountSid = "";
    private String authToken = "";
    private String publicBaseUrl = "";
    /** Optional E.164 From when the firm has not rented a DID or verified a number in the app. */
    private String voiceFrom = "";
    /** Override in tests. Production stays on the Twilio API host. */
    private String apiRoot = "https://api.twilio.com";

    public boolean configured() {
        return !accountSid.isBlank() && !authToken.isBlank();
    }

    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    public boolean hasVoiceFrom() {
        return voiceFrom != null && !voiceFrom.isBlank();
    }

    public String callback(String path) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!(base.startsWith("https://") || base.startsWith("http://")) || base.contains(" ") || base.contains("@")) {
            throw com.legalsuite.common.ApiException.badRequest(
                    "Set TWILIO_PUBLIC_BASE_URL to the public http or https address of this API so Twilio can fetch the bridge.");
        }
        String suffix = path == null ? "" : path;
        if (!suffix.startsWith("/")) suffix = "/" + suffix;
        return base + suffix;
    }

    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid == null ? "" : accountSid; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken == null ? "" : authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl; }
    public String getVoiceFrom() { return voiceFrom; }
    public void setVoiceFrom(String voiceFrom) { this.voiceFrom = voiceFrom == null ? "" : voiceFrom.trim(); }
    public String getApiRoot() { return apiRoot; }
    public void setApiRoot(String apiRoot) {
        if (apiRoot != null && !apiRoot.isBlank()) this.apiRoot = apiRoot;
    }
}
