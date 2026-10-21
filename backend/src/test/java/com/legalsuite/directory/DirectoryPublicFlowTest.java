package com.legalsuite.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.repo.LeadRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/** End to end through the HTTP API: search, firm page, enquiry into intake, operator controls. */
@SpringBootTest
@AutoConfigureMockMvc
class DirectoryPublicFlowTest extends DirectoryTestSupport {
    @Autowired DirectoryEnquiryRepository enquiries;
    @Autowired LeadRepository leads;
    @Autowired DirectoryPublicService publicService;

    private JsonNode search(String query) throws Exception {
        return data(mvc.perform(get("/api/v1/public/directory/search" + query)).andExpect(status().isOk()).andReturn());
    }

    private boolean has(JsonNode results, String slug) {
        for (JsonNode r : results.get("results")) if (slug.equals(r.get("slug").asText())) return true;
        return false;
    }

    @Test
    void publicSearchFiltersByAreaPlaceAndLanguageWithoutSignIn() throws Exception {
        listNdlovu();
        assertTrue(has(search(""), "ndlovu-partners"));
        assertTrue(has(search("?area=raf-claims"), "ndlovu-partners"));
        assertTrue(has(search("?area=raf-claims&province=gauteng&city=sandton&language=isizulu"), "ndlovu-partners"));
        assertFalse(has(search("?area=tax"), "ndlovu-partners"));
        assertFalse(has(search("?province=western-cape"), "ndlovu-partners"));
        assertFalse(has(search("?language=afrikaans"), "ndlovu-partners"));
        JsonNode all = search("");
        assertTrue(all.get("order").asText().contains("rotate daily"));
        for (JsonNode r : all.get("results")) {
            assertFalse(r.has("rating"));
            assertFalse(r.has("tenantId"));
        }
        mvc.perform(get("/api/v1/public/directory/search?area=space-law")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/directory/catalog")).andExpect(status().isOk());
    }

    @Test
    void firmPageShowsVerifiedLpcNumbersAndConsentWording() throws Exception {
        listNdlovu();
        JsonNode firm = data(mvc.perform(get("/api/v1/public/directory/firms/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertEquals("ndlovu-partners", firm.get("slug").asText());
        assertTrue(firm.get("practitioners").size() >= 1);
        assertTrue(firm.get("practitioners").toString().contains("LPC GP 44821"));
        assertTrue(firm.get("consentText").asText().contains("POPIA"));
        assertFalse(firm.toString().contains("\u2014"));
        mvc.perform(get("/api/v1/public/directory/firms/smith-associates-nope")).andExpect(status().isNotFound());
    }

    @Test
    void enquiryLandsInIntakeWithConflictCheckAndConsent() throws Exception {
        listNdlovu();
        String url = "/api/v1/public/directory/firms/ndlovu-partners/enquiries";
        // Consent is required and starts unticked.
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zanele Test\",\"email\":\"zanele@example.com\",\"message\":\"I was hurt in a taxi crash in May.\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zanele Test\",\"message\":\"I was hurt in a taxi crash in May.\",\"consent\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zanele Test\",\"email\":\"z@example.com\",\"practiceArea\":\"tax\",\"message\":\"I was hurt in a taxi crash in May.\",\"consent\":true}"))
                .andExpect(status().isBadRequest());

        JsonNode ok = data(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zanele Test\",\"email\":\"zanele@example.com\",\"phone\":\"082 555 0101\","
                                + "\"practiceArea\":\"raf-claims\",\"opposingParty\":\"Nomsa Khumalo\","
                                + "\"message\":\"I was hurt in a taxi crash in May.\",\"consent\":true}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("received", ok.get("status").asText());
        UUID leadId = UUID.fromString(ok.get("id").asText());

        DirectoryEnquiry e = enquiries.findByLeadId(leadId).orElseThrow();
        assertEquals(DirectoryPublicService.CONSENT_VERSION, e.getConsentVersion());
        assertNotNull(e.getConsentAt());
        assertNotNull(e.getConflictCheckId());
        assertEquals("potential_conflict", e.getConflictStatus());
        assertTrue(e.getPurgeAfter().isAfter(Instant.now().plus(89, ChronoUnit.DAYS)));

        String t = thabo();
        JsonNode intake = data(mvc.perform(get("/api/v1/intake/leads").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn());
        assertTrue(intake.toString().contains("Zanele Test"));
        JsonNode rec = data(mvc.perform(get("/api/v1/directory/enquiries/" + leadId).header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn());
        assertEquals("potential_conflict", rec.get("conflictStatus").asText());
        // Another firm cannot read it.
        mvc.perform(get("/api/v1/directory/enquiries/" + leadId).header("Authorization", "Bearer " + token("smith-associates", "john@smithlaw.com")))
                .andExpect(status().isNotFound());

        // Honeypot answers but stores nothing.
        long before = enquiries.count();
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bot\",\"email\":\"b@example.com\",\"message\":\"buy cheap things now\",\"consent\":true,\"website\":\"x\"}"))
                .andExpect(status().isOk());
        assertEquals(before, enquiries.count());
    }

    @Test
    void expiredEnquiryIsPurgedUnlessItBecameAClient() throws Exception {
        listNdlovu();
        JsonNode ok = data(mvc.perform(post("/api/v1/public/directory/firms/ndlovu-partners/enquiries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Purge Me\",\"email\":\"purge@example.com\",\"message\":\"Old enquiry that never went anywhere.\",\"consent\":true}"))
                .andExpect(status().isOk()).andReturn());
        UUID leadId = UUID.fromString(ok.get("id").asText());
        DirectoryEnquiry e = enquiries.findByLeadId(leadId).orElseThrow();
        e.setPurgeAfter(Instant.now().minusSeconds(60));
        enquiries.save(e);
        assertTrue(publicService.purgeExpired() >= 1);
        assertTrue(enquiries.findByLeadId(leadId).isEmpty());
        assertTrue(leads.findById(leadId).isEmpty());
    }

    @Test
    void operatorHidesAndRestoresListing() throws Exception {
        listNdlovu();
        String ops = ops();
        // Firms cannot reach operator screens.
        mvc.perform(get("/api/v1/platform/directory/listings").header("Authorization", "Bearer " + thabo())).andExpect(status().isForbidden());
        JsonNode list = data(mvc.perform(get("/api/v1/platform/directory/listings").header("Authorization", "Bearer " + ops)).andExpect(status().isOk()).andReturn());
        String tenantId = null;
        for (JsonNode l : list) if ("ndlovu-partners".equals(l.get("slug").asText())) {
            tenantId = l.get("tenantId").asText();
            for (JsonNode c : l.get("cityLabels")) org.junit.jupiter.api.Assertions.assertTrue(Character.isUpperCase(c.asText().charAt(0)), "city shown as code: " + c);
            for (JsonNode a : l.get("practiceAreaLabels")) org.junit.jupiter.api.Assertions.assertFalse(a.asText().contains("-"), "practice area shown as code: " + a);
        }
        assertNotNull(tenantId);
        String vis = "/api/v1/platform/directory/listings/" + tenantId + "/visibility";
        mvc.perform(post(vis).header("Authorization", "Bearer " + ops).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}"))
                .andExpect(status().isBadRequest());
        try {
            JsonNode hidden = data(mvc.perform(post(vis).header("Authorization", "Bearer " + ops).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hidden\":true,\"reason\":\"Complaint under review\"}"))
                    .andExpect(status().isOk()).andReturn());
            assertFalse(hidden.get("listed").asBoolean());
            assertFalse(has(search(""), "ndlovu-partners"));
            mvc.perform(get("/api/v1/public/directory/firms/ndlovu-partners")).andExpect(status().isNotFound());
            assertTrue(profile(thabo()).get("reasons").toString().contains("Complaint under review"));
        } finally {
            mvc.perform(post(vis).header("Authorization", "Bearer " + ops).contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":false}"))
                    .andExpect(status().isOk());
        }
        assertTrue(has(search(""), "ndlovu-partners"));
    }
}
