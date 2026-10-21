package com.legalsuite.service;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.legalsuite.config.AiProperties;
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

/** All four AI features go through the model when one is configured, with guardrails and a monthly cap. */
@SpringBootTest
@AutoConfigureMockMvc
class AiGeminiFeaturesTest {
    @Autowired MockMvc mvc;
    @Autowired AiProperties props;
    @MockitoBean AiModelGateway gateway;

    String token;
    String matterId;

    @BeforeEach
    void setUp() throws Exception {
        props.setMonthlyModelActions(100);
        when(gateway.peek()).thenReturn(new AiModelGateway.Completion(AiModelGateway.Status.CONFIGURED, "gemini", null, null));
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(
                AiModelGateway.Status.CONFIGURED, "gemini", "MODEL REPLY", "This answer used Google Gemini."));
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(login, "$.data.accessToken");
        String cases = mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        matterId = ((java.util.List<String>) JsonPath.read(cases, "$.data[?(@.caseNumber == 'C-2001')].id")).get(0);
    }

    private org.springframework.test.web.servlet.ResultActions call(String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/ai/" + path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void allFourFeaturesUseTheModelWithGuardrails() throws Exception {
        call("chat", "{\"prompt\":\"What clocks apply?\",\"caseId\":\"" + matterId + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("gemini"))
                .andExpect(jsonPath("$.data.reply", containsString("MODEL REPLY")));
        call("summarize", "{\"caseId\":\"" + matterId + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary").value("MODEL REPLY"))
                .andExpect(jsonPath("$.data.mode").value("gemini"));
        call("draft-email", "{\"kind\":\"status\",\"caseId\":\"" + matterId + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draft").value("MODEL REPLY"));
        call("screen-intake", "{\"name\":\"Test Person\",\"caseType\":\"RAF\",\"description\":\"Car accident last month\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary").value("MODEL REPLY"))
                .andExpect(jsonPath("$.data.band").exists());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        Mockito.verify(gateway, Mockito.atLeast(4)).complete(system.capture(), anyString());
        for (String s : system.getAllValues()) {
            org.junit.jupiter.api.Assertions.assertTrue(s.contains("Never predict the outcome"));
            org.junit.jupiter.api.Assertions.assertTrue(s.contains("Do not draft full legal documents"));
        }
    }

    @Test
    void vendorErrorFallsBackToLocal() throws Exception {
        when(gateway.complete(anyString(), anyString())).thenReturn(new AiModelGateway.Completion(
                AiModelGateway.Status.VENDOR_ERROR, "gemini", null, "The AI service has reached its free limit for now."));
        call("summarize", "{\"caseId\":\"" + matterId + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("local-heuristic"))
                .andExpect(jsonPath("$.data.notice", containsString("free limit")));
    }

    @Test
    void monthlyCapStopsModelCalls() throws Exception {
        props.setMonthlyModelActions(0);
        call("draft-email", "{\"kind\":\"status\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("local-heuristic"))
                .andExpect(jsonPath("$.data.notice", containsString("AI actions for this month")));
        Mockito.verify(gateway, Mockito.never()).complete(anyString(), anyString());
        props.setMonthlyModelActions(100);
    }
}
