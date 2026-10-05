package com.legalsuite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Model vendor settings. The default provider is {@code local}, which never sends a prompt off the tenant.
 * Set {@code LEGALSUITE_AI_PROVIDER} to {@code openai} or {@code anthropic} and supply the matching key to opt in.
 */
@ConfigurationProperties(prefix = "legalsuite.ai")
public class AiProperties {
    private String provider = "local";
    private String openaiApiKey = "";
    private String anthropicApiKey = "";
    private String openaiModel = "gpt-4o-mini";
    private String anthropicModel = "claude-3-5-haiku-latest";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private String anthropicBaseUrl = "https://api.anthropic.com/v1";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
    }

    public String getOpenaiModel() {
        return openaiModel;
    }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
    }

    public String getAnthropicModel() {
        return anthropicModel;
    }

    public void setAnthropicModel(String anthropicModel) {
        this.anthropicModel = anthropicModel;
    }

    public String getOpenaiBaseUrl() {
        return openaiBaseUrl;
    }

    public void setOpenaiBaseUrl(String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
    }

    public String getAnthropicBaseUrl() {
        return anthropicBaseUrl;
    }

    public void setAnthropicBaseUrl(String anthropicBaseUrl) {
        this.anthropicBaseUrl = anthropicBaseUrl;
    }
}
