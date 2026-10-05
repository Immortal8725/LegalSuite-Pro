package com.legalsuite.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiChatAccessTest {
    @Autowired
    private MockMvc mvc;

    private static String ndlovuToken;
    private static String smithToken;
    private static String ndlovuMatterId;

    @BeforeEach
    void loadSessions() throws Exception {
        if (ndlovuToken == null) {
            ndlovuToken = login("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
            ndlovuMatterId = caseId(ndlovuToken, "C-2001");
        }
        if (smithToken == null) {
            smithToken = login("smith-associates", "john@smithlaw.com", "password");
        }
    }

    @Test
    void sameTenantMatterAnswersFromClocksAndNotes() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + ndlovuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"What statutory clocks apply, and what do the notes say about the hospital?","caseId":"%s"}
                                """.formatted(ndlovuMatterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mode").value("local-heuristic"))
                .andExpect(jsonPath("$.data.assistive").value(true))
                .andExpect(jsonPath("$.data.reply", containsString("Road Accident Fund Act 56 of 1996 s 23")))
                .andExpect(jsonPath("$.data.reply", containsString("Hospital records")))
                .andExpect(jsonPath("$.data.reply", containsString("attorney remains responsible")))
                .andExpect(jsonPath("$.data.matter.caseNumber").value("C-2001"))
                .andExpect(jsonPath("$.data.notice", containsString("not sent to a model vendor")));
    }

    @Test
    void otherTenantMatterIsRefused() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + smithToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Summarize the notes and the clocks","caseId":"%s"}
                                """.formatted(ndlovuMatterId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Matter not found"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Khumalo"));
        assertFalse(body.contains("Hospital records"));
        assertFalse(body.contains("Nomsa"));
    }

    @Test
    void unauthenticatedIsRefused() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"What is on this matter?","caseId":"%s"}
                                """.formatted(ndlovuMatterId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", not(containsString("Khumalo"))))
                .andExpect(jsonPath("$.message", not(containsString("Hospital"))));
    }

    @Test
    void clientPortalCannotReadStaffNotes() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firmSlug":"ndlovu-partners","email":"nomsa@example.com","password":"portal123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        MvcResult result = mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Read the notes","caseId":"%s"}
                                """.formatted(ndlovuMatterId)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Hospital records"));
    }

    @Test
    void fileAnswerUsesNamesNotStoragePaths() throws Exception {
        String caseId = caseId(smithToken, "C-1042");
        mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + smithToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Which files are on this matter?","caseId":"%s"}
                                """.formatted(caseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply", containsString("deposition_transcript.pdf")))
                .andExpect(jsonPath("$.data.reply", not(containsString("seed://"))));
    }

    private String login(String slug, String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firmSlug":"%s","email":"%s","password":"%s"}
                                """.formatted(slug, email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String caseId(String token, String caseNumber) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Object raw = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data[?(@.caseNumber == '" + caseNumber + "')].id");
        if (raw instanceof java.util.List<?> list) {
            return String.valueOf(list.getFirst());
        }
        return String.valueOf(raw);
    }
}
