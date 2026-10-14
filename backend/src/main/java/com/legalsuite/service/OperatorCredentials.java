package com.legalsuite.service;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads Twilio and PayFast settings from the process environment.
 * Values are never returned to the client and are never logged.
 */
@Component
public class OperatorCredentials {
    private final String stripeSecret;
    private final String stripePublishable;
    private final String twilioSid;
    private final String twilioToken;
    private final String twilioBaseUrl;
    private final String payfastMerchantId;
    private final String payfastMerchantKey;

    public OperatorCredentials(
            @Value("${legalsuite.stripe.secret-key:}") String stripeSecret,
            @Value("${legalsuite.stripe.publishable-key:}") String stripePublishable,
            @Value("${legalsuite.twilio.account-sid:}") String twilioSid,
            @Value("${legalsuite.twilio.auth-token:}") String twilioToken,
            @Value("${legalsuite.twilio.public-base-url:}") String twilioBaseUrl,
            @Value("${legalsuite.payfast.merchant-id:}") String payfastMerchantId,
            @Value("${legalsuite.payfast.merchant-key:}") String payfastMerchantKey) {
        this.stripeSecret = stripeSecret;
        this.stripePublishable = stripePublishable;
        this.twilioSid = twilioSid;
        this.twilioToken = twilioToken;
        this.twilioBaseUrl = twilioBaseUrl;
        this.payfastMerchantId = payfastMerchantId;
        this.payfastMerchantKey = payfastMerchantKey;
    }

    public boolean payfastLive() {
        return present(payfastMerchantId) && present(payfastMerchantKey);
    }

    public boolean stripeLive() {
        return present(stripeSecret) && present(stripePublishable);
    }

    public boolean twilioLive() {
        return present(twilioSid) && present(twilioToken) && present(twilioBaseUrl);
    }

    public boolean live(String provider) {
        if (provider == null) return false;
        return switch (provider) {
            case "stripe" -> stripeLive();
            case "payfast" -> payfastLive();
            case "twilio" -> twilioLive();
            default -> false;
        };
    }

    static boolean present(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !lower.contains("changeme")
                && !lower.startsWith("your-")
                && !lower.contains("paste-")
                && !lower.contains("replace-me");
    }
}
