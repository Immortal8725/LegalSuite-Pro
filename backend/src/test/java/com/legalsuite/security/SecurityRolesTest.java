package com.legalsuite.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** B1, B2, B11 and B14 from the A to Z report. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRolesTest {
    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void portalTokenOnlyReachesPortalEndpoints() throws Exception {
        String nomsa = portalToken();
        mvc.perform(get("/api/v1/portal/cases").header("Authorization", "Bearer " + nomsa)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/portal/invoices").header("Authorization", "Bearer " + nomsa)).andExpect(status().isOk());
        for (String path : new String[] {
                "/api/v1/clients", "/api/v1/cases", "/api/v1/trust/accounts", "/api/v1/users", "/api/v1/audit",
                "/api/v1/time-entries", "/api/v1/intake/leads", "/api/v1/invoices", "/api/v1/auth/me", "/api/v1/dashboard"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + nomsa)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + nomsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"P\",\"lastName\":\"X\",\"email\":\"portal-esc@example.com\",\"role\":\"owner\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffTokenCannotUsePortalEndpoints() throws Exception {
        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        mvc.perform(get("/api/v1/portal/cases").header("Authorization", "Bearer " + thabo)).andExpect(status().isForbidden());
    }

    @Test
    void refreshTokenIsNotAnAccessToken() throws Exception {
        MvcResult login = login("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        String refresh = data(login).get("refreshToken").asText();
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void associateCannotRunTheFirm() throws Exception {
        String sipho = token("ndlovu-partners", "sipho@ndlovulaw.co.za", "password");
        String siphoId = me(sipho).get("id").asText();
        String tenantId = tenant(sipho).get("id").asText();

        mvc.perform(patch("/api/v1/users/" + siphoId)
                        .header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"owner\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"assoc-invite@example.com\",\"role\":\"owner\"}"))
                .andExpect(status().isForbidden());
        JsonNode modules = data(mvc.perform(get("/api/v1/modules")).andExpect(status().isOk()).andReturn());
        String addon = null;
        for (JsonNode m : modules) if (!m.get("core").asBoolean()) addon = m.get("id").asText();
        mvc.perform(post("/api/v1/tenants/" + tenantId + "/modules")
                        .header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleId\":\"" + addon + "\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/tenants/me")
                        .header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"Taken over\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/integrations/stripe/connect").header("Authorization", "Bearer " + sipho))
                .andExpect(status().isForbidden());
        assertEquals("associate", me(sipho).get("role").asText());

        // the associate still does the day job
        mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + sipho)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + sipho)).andExpect(status().isOk());
    }

    @Test
    void ownerInviteGetsOneTimePasswordThatMustBeChanged() throws Exception {
        String john = token("smith-associates", "john@smithlaw.com", "password");
        String email = "invite-" + UUID.randomUUID().toString().substring(0, 8) + "@smithlaw.com";
        JsonNode invited = data(mvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + john)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Zanele\",\"lastName\":\"Mokoena\",\"email\":\"" + email + "\",\"role\":\"paralegal\"}"))
                .andExpect(status().isOk())
                .andReturn());
        String temp = invited.get("temporaryPassword").asText();
        assertTrue(temp.length() >= 12);
        assertNotEquals("changeme123", temp);
        assertTrue(invited.get("mustChangePassword").asBoolean());
        login("smith-associates", email, "changeme123", 401);

        JsonNode first = data(login("smith-associates", email, temp));
        assertTrue(first.get("user").get("mustChangePassword").asBoolean());
        String firstToken = first.get("accessToken").asText();
        mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + firstToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + firstToken)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + temp + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        JsonNode changed = data(mvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + temp + "\",\"newPassword\":\"Sandton-Chambers-2026\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertFalse(changed.get("user").get("mustChangePassword").asBoolean());
        mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + changed.get("accessToken").asText()))
                .andExpect(status().isOk());
        login("smith-associates", email, temp, 401);

        // An owner can change a paralegal's role; nobody can change their own.
        String id = invited.get("id").asText();
        mvc.perform(patch("/api/v1/users/" + id)
                        .header("Authorization", "Bearer " + john)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"associate\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/users/" + me(john).get("id").asText())
                        .header("Authorization", "Bearer " + john)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"associate\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossTenantTrustLedgerIsNotFound() throws Exception {
        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        String john = token("smith-associates", "john@smithlaw.com", "password");
        String account = data(mvc.perform(get("/api/v1/trust/accounts").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        mvc.perform(get("/api/v1/trust/accounts/" + account + "/ledger").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/trust/accounts/" + account + "/ledger").header("Authorization", "Bearer " + john))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantConversationCannotBePostedInto() throws Exception {
        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za", "password");
        String john = token("smith-associates", "john@smithlaw.com", "password");
        String conversation = data(mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + john))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asText();
        mvc.perform(post("/api/v1/messages").header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversation + "\",\"body\":\"cross-tenant\"}"))
                .andExpect(status().isNotFound());
        String after = mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + john))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(after.contains("cross-tenant"));
    }

    private JsonNode me(String token) throws Exception {
        return data(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("user");
    }

    private JsonNode tenant(String token) throws Exception {
        return data(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("tenant");
    }

    private MvcResult login(String slug, String email, String password) throws Exception {
        return login(slug, email, password, 200);
    }

    private MvcResult login(String slug, String email, String password, int expected) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expected))
                .andReturn();
    }

    private String token(String slug, String email, String password) throws Exception {
        return data(login(slug, email, password)).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
