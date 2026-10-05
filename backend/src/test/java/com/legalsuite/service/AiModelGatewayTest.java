package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiModelGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void missingKeyDoesNotCallTheNetwork() {
        AiProperties props = new AiProperties();
        props.setProvider("openai");
        props.setOpenaiApiKey("  ");
        AiModelGateway gateway = new AiModelGateway(props, mapper, RestClient.builder().build());
        AiModelGateway.Completion result = gateway.complete("system", "What is the clock?");
        assertEquals(AiModelGateway.Status.MISSING_KEY, result.status());
        assertNull(result.text());
        assertTrue(result.notice().contains("OPENAI_API_KEY"));
    }

    @Test
    void localProviderDoesNotCallTheNetwork() {
        AiProperties props = new AiProperties();
        props.setProvider("local");
        AiModelGateway gateway = new AiModelGateway(props, mapper, RestClient.builder().build());
        AiModelGateway.Completion result = gateway.complete("system", "hello");
        assertEquals(AiModelGateway.Status.LOCAL, result.status());
        assertNull(result.text());
    }

    @Test
    void openaiPathParsesTheCompletion() {
        AiProperties props = new AiProperties();
        props.setProvider("openai");
        props.setOpenaiApiKey("test-key");
        props.setOpenaiModel("gpt-4o-mini");
        props.setOpenaiBaseUrl("https://api.openai.com/v1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"Use the RAF Act s 23 clock already on the file.\"}}]}",
                        MediaType.APPLICATION_JSON));
        AiModelGateway gateway = new AiModelGateway(props, mapper, builder.build());
        AiModelGateway.Completion result = gateway.complete("stay on the matter", "What is the deadline?");
        assertEquals(AiModelGateway.Status.CONFIGURED, result.status());
        assertEquals("openai", result.provider());
        assertTrue(result.text().contains("RAF Act s 23"));
        server.verify();
    }

    @Test
    void anthropicPathParsesTheCompletion() {
        AiProperties props = new AiProperties();
        props.setProvider("anthropic");
        props.setAnthropicApiKey("test-key");
        props.setAnthropicBaseUrl("https://api.anthropic.com/v1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(header("x-api-key", "test-key"))
                .andRespond(withSuccess(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"Parties stay as recorded on the matter.\"}]}",
                        MediaType.APPLICATION_JSON));
        AiModelGateway gateway = new AiModelGateway(props, mapper, builder.build());
        AiModelGateway.Completion result = gateway.complete("system", "Who is the client?");
        assertEquals(AiModelGateway.Status.CONFIGURED, result.status());
        assertTrue(result.text().contains("Parties stay as recorded"));
        server.verify();
    }
}
