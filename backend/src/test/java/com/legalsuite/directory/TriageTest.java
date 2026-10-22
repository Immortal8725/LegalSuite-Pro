package com.legalsuite.directory;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalsuite.service.AiModelGateway;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class TriageTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiModelGateway gateway;

    @BeforeEach
    void configured() {
        when(gateway.peek()).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.CONFIGURED, "gemini", null, null));
    }

    private ResultActions ask(String ip, String text, String town) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("text", text, "town", town, "lang", "en"));
        return mvc.perform(post("/api/v1/public/triage").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private static String ip() {
        return "10.9." + (UUID.randomUUID().hashCode() & 0xff) + "." + (UUID.randomUUID().hashCode() & 0xff);
    }

    @Test
    void geminiAnswerIsMappedToDirectoryAreasAndTownWithoutLogin() throws Exception {
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.CONFIGURED, "gemini",
                "```json\n{\"areas\":[\"labour-and-employment\",\"made-up-area\"],\"explanation\":\"This looks like an unfair dismissal matter.\",\"timeLimits\":[\"Refer to the CCMA within 30 days.\"]}\n```", null));
        ask(ip(), "My boss fired me yesterday without a hearing after 6 years.", "Durban")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("gemini"))
                .andExpect(jsonPath("$.data.areas.length()").value(1))
                .andExpect(jsonPath("$.data.areas[0].slug").value("labour-and-employment"))
                .andExpect(jsonPath("$.data.timeLimits[0]", containsString("30 days")))
                .andExpect(jsonPath("$.data.place.city").value("durban"))
                .andExpect(jsonPath("$.data.place.province").value("kwazulu-natal"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gateway).complete(system.capture(), anyString());
        assertTrue(system.getValue().contains("Never judge the merits"));
    }

    @Test
    void idNumbersPhonesAndEmailsAreStrippedBeforeSending() throws Exception {
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.CONFIGURED, "gemini",
                "{\"areas\":[\"raf-claims\"],\"explanation\":\"A road accident claim.\",\"timeLimits\":[]}", null));
        ask(ip(), "Car accident last week. My ID is 8001015009087, call 082 555 1234 or me@example.com", "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.redacted").value(true));
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gateway).complete(anyString(), user.capture());
        assertFalse(user.getValue().contains("8001015009087"));
        assertFalse(user.getValue().contains("555 1234"));
        assertFalse(user.getValue().contains("me@example.com"));
        assertTrue(user.getValue().contains("[ID number removed]"));
    }

    @Test
    void vendorErrorOrQuotaFallsBackToKeywords() throws Exception {
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.VENDOR_ERROR, "gemini", null, "limit"));
        ask(ip(), "I want a divorce and my wife wants custody of the children.", "Pretoria")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("keywords"))
                .andExpect(jsonPath("$.data.areas[0].slug").value("divorce-and-family"));
    }

    @Test
    void garbledModelReplyFallsBackToKeywords() throws Exception {
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.CONFIGURED, "gemini", "Sorry, I cannot.", null));
        ask(ip(), "A taxi crashed into my car on the N3 and I was injured.", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("keywords"))
                .andExpect(jsonPath("$.data.areas[0].slug").value("raf-claims"))
                .andExpect(jsonPath("$.data.timeLimits[0]", containsString("3 years")));
    }

    @Test
    void lengthIsLimited() throws Exception {
        ask(ip(), "too short", "").andExpect(status().isBadRequest());
        ask(ip(), "x".repeat(TriageService.MAX_CHARS + 1), "").andExpect(status().isBadRequest());
    }

    @Test
    void ipRateLimitReturns429() throws Exception {
        when(gateway.peek()).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.LOCAL, "local", null, null));
        String addr = ip();
        for (int i = 0; i < 8; i++) ask(addr, "My landlord wants to evict me next week.", "").andExpect(status().isOk());
        ask(addr, "My landlord wants to evict me next week.", "").andExpect(status().isTooManyRequests());
        ask(ip(), "My landlord wants to evict me next week.", "").andExpect(status().isOk());
    }
}
