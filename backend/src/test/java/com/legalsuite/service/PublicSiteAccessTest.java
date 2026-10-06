package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PublicSiteAccessTest {
    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void approvalGateHidesFeaturesAndStaffFields() throws Exception {
        JsonNode ndlovu = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertTrue(ndlovu.get("published").asBoolean());
        assertEquals("dark", ndlovu.get("theme").asText());
        assertEquals("copper", ndlovu.get("accent").asText());
        assertEquals(
                Set.of("people", "insights", "recognition", "whatsapp", "booking"),
                textSet(ndlovu.get("features")));
        assertTrue(ndlovu.get("people").isArray());
        assertEquals(3, ndlovu.get("people").size());
        boolean director = false;
        for (JsonNode person : ndlovu.get("people")) {
            if ("Director".equals(person.get("title").asText()) && "Thabo Ndlovu".equals(person.get("fullName").asText())) {
                director = true;
            }
        }
        assertTrue(director);
        assertNull(ndlovu.get("feesNote"));
        assertNull(ndlovu.get("situations"));
        String raw = ndlovu.toString();
        assertFalse(raw.contains("hourlyRate"));
        assertFalse(raw.contains("4200"));
        assertFalse(raw.contains("totp"));
        assertFalse(raw.contains("barNumber"));
        assertFalse(raw.contains("FFC-GP"));
        assertFalse(raw.contains("approvalStatus"));
        assertFalse(raw.contains("onlineStatus"));
        assertEquals("https://wa.me/27115550180", ndlovu.get("whatsappUrl").asText());

        JsonNode legacy = data(mvc.perform(get("/api/v1/landing/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertFalse(legacy.toString().contains("hourlyRate"));
        assertFalse(legacy.toString().contains("FFC-GP"));

        JsonNode smith = data(mvc.perform(get("/api/v1/public/sites/smith-associates")).andExpect(status().isOk()).andReturn());
        assertFalse(smith.get("published").asBoolean());
        assertEquals("Smith & Associates", smith.get("firmName").asText());
        assertNull(smith.get("features"));
        assertNull(smith.get("people"));
        assertFalse(smith.toString().contains("john@smithlaw.com"));
        assertFalse(smith.toString().contains("pending"));

        mvc.perform(post("/api/v1/intake/smith-associates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"description\":\"Hello\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/public/sites/ndlovu-partners/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@example.com\",\"consent\":true}"))
                .andExpect(status().isNotFound());

        String sipho = token("ndlovu-partners", "sipho@ndlovulaw.co.za");
        mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"features\":{\"fees\":true}}"))
                .andExpect(status().isForbidden());

        String nomsa = portalToken();
        mvc.perform(get("/api/v1/public-site").header("Authorization", "Bearer " + nomsa))
                .andExpect(status().isForbidden());

        String john = token("smith-associates", "john@smithlaw.com");
        JsonNode smithAdmin = data(mvc.perform(get("/api/v1/public-site").header("Authorization", "Bearer " + john))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("smith-associates", smithAdmin.get("slug").asText());
        assertEquals("pending_approval", smithAdmin.get("publishStatus").asText());

        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        mvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"root@ndlovulaw.co.za\",\"firstName\":\"Root\",\"lastName\":\"User\",\"role\":\"superadmin\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/public-sites/" + smithAdmin.get("tenantId").asText() + "/publish")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isForbidden());

        data(mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accent\":\"copper\",\"features\":{\"fees\":true}}"))
                .andExpect(status().isOk())
                .andReturn());
        data(mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode stillOff = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertFalse(textSet(stillOff.get("features")).contains("fees"));
        assertFalse(textSet(stillOff.get("features")).contains("newsletter"));
        assertFalse(textSet(stillOff.get("features")).contains("situations"));

        String ops = token("legalsuite", "ops@legalsuite.pro");
        JsonNode queue = data(mvc.perform(get("/api/v1/platform/public-sites/queue").header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(queue.isArray());
        assertTrue(queue.toString().contains("\"featureKey\":\"fees\""));
        assertTrue(queue.toString().contains("\"featureKey\":\"newsletter\""));
        assertTrue(queue.toString().contains("\"featureKey\":\"situations\""));
        assertTrue(queue.toString().contains("\"kind\":\"publish\""));
        assertFalse(queue.toString().contains("hourlyRate"));

        String ndlovuId = data(mvc.perform(get("/api/v1/public-site").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk())
                .andReturn()).get("tenantId").asText();
        mvc.perform(post("/api/v1/platform/public-sites/" + ndlovuId + "/features/fees")
                        .header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\",\"note\":\"Fees page can go live.\"}"))
                .andExpect(status().isOk());

        JsonNode withFees = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertTrue(textSet(withFees.get("features")).contains("fees"));
        assertTrue(withFees.get("feesNote").asText().contains("written mandate"));
        assertFalse(textSet(withFees.get("features")).contains("situations"));

        mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"features\":{\"fees\":false}}"))
                .andExpect(status().isOk());
        JsonNode feesOff = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertFalse(textSet(feesOff.get("features")).contains("fees"));

        mvc.perform(get("/api/v1/public/sites/missing-firm")).andExpect(status().isNotFound());
    }

    @Test
    void darkThemeWaitsForApprovalAndLightStillPublishes() throws Exception {
        JsonNode before = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertEquals("dark", before.get("theme").asText());

        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"midnight\"}"))
                .andExpect(status().isBadRequest());

        JsonNode draft = data(mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"light\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("light", draft.get("theme").asText());
        assertEquals("dark", draft.get("liveTheme").asText());

        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk());
        JsonNode stillDark = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertEquals("dark", stillDark.get("theme").asText());

        String ops = token("legalsuite", "ops@legalsuite.pro");
        mvc.perform(post("/api/v1/platform/public-sites/" + draft.get("tenantId").asText() + "/branding")
                        .header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isOk());
        JsonNode light = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertEquals("light", light.get("theme").asText());

        mvc.perform(put("/api/v1/public-site")
                        .header("Authorization", "Bearer " + thabo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/public-sites/" + draft.get("tenantId").asText() + "/branding")
                        .header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isOk());
        JsonNode restored = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertEquals("dark", restored.get("theme").asText());
    }

    private String token(String slug, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
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

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
