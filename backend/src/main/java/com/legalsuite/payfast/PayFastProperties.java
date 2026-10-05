package com.legalsuite.payfast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayFast Aggregation credentials for LegalSuite product billing.
 * Values come from the process environment. They are never written to the database.
 */
@Component
@ConfigurationProperties(prefix = "legalsuite.payfast")
public class PayFastProperties {
    private String merchantId = "";
    private String merchantKey = "";
    private String passphrase = "";
    private String env = "sandbox";
    private String returnUrl = "";
    private String cancelUrl = "";
    private String notifyUrl = "";

    public boolean sandbox() {
        return env == null || env.isBlank() || "sandbox".equalsIgnoreCase(env.trim());
    }

    public boolean live() {
        return env != null && "live".equalsIgnoreCase(env.trim());
    }

    public boolean envKnown() {
        return sandbox() || live();
    }

    public String environment() {
        return live() ? "live" : "sandbox";
    }

    public boolean merchantPresent() {
        return present(merchantId) && present(merchantKey);
    }

    public boolean readyForCheckout() {
        return merchantPresent() && envKnown() && httpUrl(returnUrl) && httpUrl(cancelUrl) && httpUrl(notifyUrl);
    }

    public String processUrl() {
        return live() ? "https://www.payfast.co.za/eng/process" : "https://sandbox.payfast.co.za/eng/process";
    }

    public String validateUrl() {
        return live()
                ? "https://www.payfast.co.za/eng/query/validate"
                : "https://sandbox.payfast.co.za/eng/query/validate";
    }

    public String adhocUrl(String token) {
        String base = "https://api.payfast.co.za/subscriptions/" + token + "/adhoc";
        return live() ? base : base + "?testing=true";
    }

    public java.util.List<String> missingCheckoutSettings() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!present(merchantId)) missing.add("PAYFAST_MERCHANT_ID");
        if (!present(merchantKey)) missing.add("PAYFAST_MERCHANT_KEY");
        if (!envKnown()) missing.add("PAYFAST_ENV");
        if (!httpUrl(returnUrl)) missing.add("PAYFAST_RETURN_URL");
        if (!httpUrl(cancelUrl)) missing.add("PAYFAST_CANCEL_URL");
        if (!httpUrl(notifyUrl)) missing.add("PAYFAST_NOTIFY_URL");
        return missing;
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    static boolean httpUrl(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.length() > 500 || trimmed.contains(" ")) return false;
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId == null ? "" : merchantId.trim(); }
    public String getMerchantKey() { return merchantKey; }
    public void setMerchantKey(String merchantKey) { this.merchantKey = merchantKey == null ? "" : merchantKey.trim(); }
    public String getPassphrase() { return passphrase; }
    public void setPassphrase(String passphrase) { this.passphrase = passphrase == null ? "" : passphrase; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env == null || env.isBlank() ? "sandbox" : env.trim(); }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl == null ? "" : returnUrl.trim(); }
    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl == null ? "" : cancelUrl.trim(); }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl == null ? "" : notifyUrl.trim(); }
}
