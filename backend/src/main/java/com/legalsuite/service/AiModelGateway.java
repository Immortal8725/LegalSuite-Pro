package com.legalsuite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.config.AiProperties;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Optional model vendor. {@code local} (the default) performs no HTTP call.
 * A missing key or a failed call does not invent an answer; the caller falls back to the on-tenant assistant.
 */
@Service
public class AiModelGateway {
    public enum Status {
        LOCAL,
        MISSING_KEY,
        VENDOR_ERROR,
        CONFIGURED
    }

    public record Completion(Status status, String provider, String text, String notice) {}

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public AiModelGateway(AiProperties props, ObjectMapper mapper) {
        this(props, mapper, defaultClient());
    }

    AiModelGateway(AiProperties props, ObjectMapper mapper, RestClient client) {
        this.props = props;
        this.mapper = mapper;
        this.client = client;
    }

    /** Provider state without an HTTP call. Docket-wide search uses this so a whole firm file is never sent out. */
    public Completion peek() {
        String provider = providerName();
        if ("openai".equals(provider)) {
            if (blank(props.getOpenaiApiKey())) {
                return missing("openai", "OpenAI is selected but OPENAI_API_KEY is not set. This answer stayed on the tenant.");
            }
            return new Completion(Status.CONFIGURED, "openai", null, null);
        }
        if ("anthropic".equals(provider)) {
            if (blank(props.getAnthropicApiKey())) {
                return missing("anthropic", "Anthropic is selected but ANTHROPIC_API_KEY is not set. This answer stayed on the tenant.");
            }
            return new Completion(Status.CONFIGURED, "anthropic", null, null);
        }
        if (!"local".equals(provider) && !"heuristic".equals(provider)) {
            return new Completion(Status.LOCAL, "local", null, "Unknown LEGALSUITE_AI_PROVIDER value. Showing the on-tenant draft.");
        }
        return new Completion(Status.LOCAL, "local", null, null);
    }

    public Completion complete(String system, String userPrompt) {
        String provider = providerName();
        if ("openai".equals(provider)) {
            if (blank(props.getOpenaiApiKey())) {
                return missing("openai", "OpenAI is selected but OPENAI_API_KEY is not set. This answer stayed on the tenant.");
            }
            try {
                String text = openAi(system, userPrompt);
                return new Completion(
                        Status.CONFIGURED,
                        "openai",
                        text,
                        "This answer used the configured OpenAI model. It can be wrong. The attorney remains responsible.");
            } catch (Exception ex) {
                return new Completion(
                        Status.VENDOR_ERROR,
                        "openai",
                        null,
                        "The OpenAI request did not return an answer. Showing the on-tenant draft instead.");
            }
        }
        if ("anthropic".equals(provider)) {
            if (blank(props.getAnthropicApiKey())) {
                return missing("anthropic", "Anthropic is selected but ANTHROPIC_API_KEY is not set. This answer stayed on the tenant.");
            }
            try {
                String text = anthropic(system, userPrompt);
                return new Completion(
                        Status.CONFIGURED,
                        "anthropic",
                        text,
                        "This answer used the configured Anthropic model. It can be wrong. The attorney remains responsible.");
            } catch (Exception ex) {
                return new Completion(
                        Status.VENDOR_ERROR,
                        "anthropic",
                        null,
                        "The Anthropic request did not return an answer. Showing the on-tenant draft instead.");
            }
        }
        if (!"local".equals(provider) && !"heuristic".equals(provider)) {
            return new Completion(Status.LOCAL, "local", null, "Unknown LEGALSUITE_AI_PROVIDER value. Showing the on-tenant draft.");
        }
        return new Completion(Status.LOCAL, "local", null, null);
    }

    private String providerName() {
        String provider = props.getProvider() == null ? "local" : props.getProvider().trim().toLowerCase(Locale.ROOT);
        return provider.isBlank() ? "local" : provider;
    }

    private static Completion missing(String provider, String notice) {
        return new Completion(Status.MISSING_KEY, provider, null, notice);
    }

    private String openAi(String system, String userPrompt) throws Exception {
        String base = trimSlash(props.getOpenaiBaseUrl());
        String model = props.getOpenaiModel() == null || props.getOpenaiModel().isBlank() ? "gpt-4o-mini" : props.getOpenaiModel();
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userPrompt)));
        String json = client.post()
                .uri(base + "/chat/completions")
                .header("Authorization", "Bearer " + props.getOpenaiApiKey().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode content = mapper.readTree(json == null ? "{}" : json).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("empty completion");
        }
        return content.asText();
    }

    private String anthropic(String system, String userPrompt) throws Exception {
        String base = trimSlash(props.getAnthropicBaseUrl());
        String model = props.getAnthropicModel() == null || props.getAnthropicModel().isBlank()
                ? "claude-3-5-haiku-latest"
                : props.getAnthropicModel();
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 800,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)));
        String json = client.post()
                .uri(base + "/messages")
                .header("x-api-key", props.getAnthropicApiKey().trim())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode blocks = mapper.readTree(json == null ? "{}" : json).path("content");
        StringBuilder text = new StringBuilder();
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("empty completion");
        }
        return text.toString();
    }

    private static RestClient defaultClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
