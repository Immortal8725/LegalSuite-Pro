package com.legalsuite.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.domain.Tenant;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class DirectoryVerificationTest extends DirectoryTestSupport {

    @Test
    void firmSetsUpProfileAndOperatorVerifiesPractitioner() throws Exception {
        String t = token("smith-associates", "john@smithlaw.com");
        JsonNode start = profile(t);
        assertFalse(start.get("listed").asBoolean());
        assertTrue(start.get("reasons").toString().contains("Turn on the Find a Lawyer listing"));
        assertTrue(start.get("catalog").get("practiceAreas").size() >= 14);

        // Fixed lists only.
        mvc.perform(put("/api/v1/directory/profile").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"practiceAreas\":[\"space-law\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/directory/profile").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practiceAreas\":[\"tax\",\"criminal\",\"notarial\",\"immigration\",\"litigation\",\"debt-collection\",\"raf-claims\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/directory/profile").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"The best divorce specialists in town.\"}"))
                .andExpect(status().isBadRequest());

        JsonNode saved = putProfile(t, "{\"practiceAreas\":[\"criminal\",\"litigation\"],\"cities\":[\"durban\"],"
                + "\"languages\":[\"english\",\"afrikaans\"],\"consultOptions\":[\"fixed-fee\",\"in-person\"],"
                + "\"fixedConsultFee\":\"650\",\"listed\":true}");
        assertEquals("[\"kwazulu-natal\"]", saved.get("provinces").toString());
        assertEquals(650.0, saved.get("fixedConsultFee").asDouble());
        assertTrue(saved.get("listedSwitch").asBoolean());
        assertFalse(saved.get("listed").asBoolean());
        assertTrue(saved.get("reasons").toString().contains("LPC-verified"));

        JsonNode withPractitioner = data(mvc.perform(post("/api/v1/directory/practitioners").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"John Smith\",\"lpcNumber\":\"lpc kzn 1234\",\"fidelityExpiresOn\":\"" + LocalDate.now().plusMonths(6) + "\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode pr = withPractitioner.get("practitioners").get(0);
        assertEquals("LPC KZN 1234", pr.get("lpcNumber").asText());
        assertEquals("pending", pr.get("status").asText());
        String id = pr.get("id").asText();

        // Firm users cannot verify their own practitioners or see the queue.
        mvc.perform(get("/api/v1/platform/directory/practitioners").header("Authorization", "Bearer " + t))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/directory/practitioners/" + id + "/verify").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"verified\"}"))
                .andExpect(status().isForbidden());

        String ops = ops();
        JsonNode queue = data(mvc.perform(get("/api/v1/platform/directory/practitioners").header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk()).andReturn());
        boolean inQueue = false;
        for (JsonNode q : queue) if (id.equals(q.get("id").asText())) inQueue = "smith-associates".equals(q.get("firmSlug").asText());
        assertTrue(inQueue);
        JsonNode verified = data(mvc.perform(post("/api/v1/platform/directory/practitioners/" + id + "/verify")
                        .header("Authorization", "Bearer " + ops).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"verified\",\"note\":\"LPC register checked\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("verified", verified.get("status").asText());
        assertTrue(profile(t).get("listed").asBoolean());

        // Changing the LPC number sends it back for a fresh check and hides the listing.
        JsonNode changed = data(mvc.perform(patch("/api/v1/directory/practitioners/" + id).header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lpcNumber\":\"LPC KZN 9999\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("pending", changed.get("practitioners").get(0).get("status").asText());
        assertFalse(changed.get("listed").asBoolean());

        // An expired Fidelity Fund certificate does not count, even once verified.
        mvc.perform(patch("/api/v1/directory/practitioners/" + id).header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fidelityExpiresOn\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/directory/practitioners/" + id + "/verify").header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"verified\"}"))
                .andExpect(status().isOk());
        assertFalse(profile(t).get("listed").asBoolean());

        // Switch off: not listed.
        JsonNode off = putProfile(t, "{\"listed\":false}");
        assertFalse(off.get("listedSwitch").asBoolean());
    }

    @Test
    void associateCannotChangeTheListing() throws Exception {
        String sipho = token("ndlovu-partners", "sipho@ndlovulaw.co.za");
        mvc.perform(put("/api/v1/directory/profile").header("Authorization", "Bearer " + sipho)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/directory/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void directorySwitchNeverEntersTheSiteApprovalQueue() throws Exception {
        listNdlovu();
        String t = thabo();
        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        JsonNode queue = data(mvc.perform(get("/api/v1/platform/public-sites/queue").header("Authorization", "Bearer " + ops()))
                .andExpect(status().isOk()).andReturn());
        assertFalse(queue.toString().contains("\"featureKey\":\"directory\""));
        assertTrue(profile(t).get("listed").asBoolean());
    }

    @Test
    void activePlanRule() {
        Tenant tenant = new Tenant();
        Instant now = Instant.now();
        tenant.setStatus("active");
        assertTrue(DirectoryService.activePlan(tenant, now));
        tenant.setStatus("trial");
        tenant.setTrialEndsAt(now.plus(3, ChronoUnit.DAYS));
        assertTrue(DirectoryService.activePlan(tenant, now));
        tenant.setTrialEndsAt(now.minus(1, ChronoUnit.DAYS));
        assertFalse(DirectoryService.activePlan(tenant, now));
        tenant.setStatus("suspended");
        assertFalse(DirectoryService.activePlan(tenant, now));
        assertFalse(DirectoryService.activePlan(null, now));
    }

    @Test
    void wordingGuard() {
        assertEquals("specialist", PublicWording.bannedWord("A specialist in tax"));
        assertEquals("best", PublicWording.bannedWord("Best service"));
        assertEquals("top", PublicWording.bannedWord("We are top rated"));
        assertNull(PublicWording.bannedWord("We stop at nothing to explain fees in plain language."));
        assertNull(PublicWording.bannedWord("Practice areas: labour and employment."));
    }
}
