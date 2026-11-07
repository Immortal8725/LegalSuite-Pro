package com.legalsuite.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Solo, Firm and Practice limits from Pricing.java are enforced on the server. */
@SpringBootTest
@AutoConfigureMockMvc
class PlanAccessTest {
    @Autowired
    MockMvc mvc;

    @Test
    void firmPlanCannotRecordAndShowsItsName() throws Exception {
        String token = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenant.planName").value("Firm"))
                .andExpect(jsonPath("$.data.tenant.callRecording").value(false))
                .andExpect(jsonPath("$.data.tenant.websiteIncluded").value(true));
        mvc.perform(post("/api/v1/calls/initiate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callType\":\"webrtc\",\"recordingEnabled\":true,\"recordingConsentGiven\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Call recording is included on the Practice plan."));
    }

    @Test
    void soloIsOneAttorneyWithoutStaffOrAWebsite() throws Exception {
        String slug = "solo-" + UUID.randomUUID().toString().substring(0, 8);
        String token = register(slug);
        mvc.perform(post("/api/v1/users/invite").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"second@" + slug + ".co.za\",\"role\":\"associate\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo includes one attorney. Firm and Practice add more attorneys."));
        mvc.perform(post("/api/v1/users/invite").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"C\",\"lastName\":\"D\",\"email\":\"staff@" + slug + ".co.za\",\"role\":\"paralegal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Staff seats are included on Firm and Practice. Solo is one attorney."));
        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The public website is included on Firm and Practice. On Solo it is an add-on."));
    }

    private String register(String slug) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"Solo " + slug + "\",\"email\":\"o@" + slug + ".co.za\",\"password\":\"Sup3r-Secret-99\",\"firstName\":\"A\",\"lastName\":\"B\",\"firmSlug\":\"" + slug + "\",\"planSlug\":\"solo\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").get("accessToken").asText();
    }

    private String token(String firm, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + firm + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").get("accessToken").asText();
    }
}
