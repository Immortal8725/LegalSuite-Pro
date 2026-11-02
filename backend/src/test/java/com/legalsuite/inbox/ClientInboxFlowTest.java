package com.legalsuite.inbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.litigation.ClientSummaries;
import com.legalsuite.litigation.LitigationCatalog;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Client inbox, stage summaries and the stage-change message, with tenant and client isolation. */
@SpringBootTest
@AutoConfigureMockMvc
class ClientInboxFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private TenantRepository tenants;
    @Autowired private ClientRepository clients;
    @Autowired private LegalCaseRepository cases;
    @Autowired private DocumentFileRepository documents;
    @Autowired private LitigationCatalog catalog;
    @Autowired private ClientSummaries summaries;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void everyStageOnEveryTrackHasAPlainSummary() {
        int n = 0;
        for (var t : catalog.tracks()) {
            for (var s : t.stages()) {
                var text = summaries.stage("en", t.id(), s.id());
                assertNotNull(text, t.id() + ":" + s.id());
                assertFalse(text.get("means").isBlank());
                assertFalse(text.get("next").isBlank());
                assertFalse((text.get("means") + text.get("next")).contains("\u2014"));
                n++;
            }
        }
        assertTrue(n > 50);
        assertTrue(summaries.disclaimer("en").contains("not legal advice"));
    }

    @Test
    void staffAndClientTalkOnAMatterWithFilesUnreadAndStageUpdates() throws Exception {
        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        Client nomsa = clients.findAll().stream().filter(c -> firm.equals(c.getTenantId()) && "nomsa@example.com".equals(c.getEmail())).findFirst().orElseThrow();
        Client other = clients.findAll().stream().filter(c -> firm.equals(c.getTenantId()) && !nomsa.getId().equals(c.getId())).findFirst().orElseThrow();
        LegalCase mine = matter(firm, nomsa, "INBOX-1");
        LegalCase theirs = matter(firm, other, "INBOX-2");

        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        String john = token("smith-associates", "john@smithlaw.com");
        String client = portalToken();

        // Summary on the staff side, with a firm note.
        call(put("/api/v1/cases/" + mine.getId() + "/client-note"), thabo, "{\"note\":\"We will call you on Friday.\"}").andExpect(status().isOk());
        JsonNode sum = data(call(get("/api/v1/cases/" + mine.getId() + "/client-summary"), thabo, null).andExpect(status().isOk()).andReturn());
        assertEquals("letter_of_demand", sum.get("stage").asText());
        assertFalse(sum.get("means").asText().isBlank());
        assertEquals("We will call you on Friday.", sum.get("firmNote").asText());

        // Staff sends a message with a file.
        MockMultipartFile pdf = new MockMultipartFile("file", "letter.pdf", "application/pdf", "%PDF-1.4\n%test\n".getBytes());
        JsonNode sent = data(mvc.perform(multipart("/api/v1/cases/" + mine.getId() + "/inbox").file(pdf).param("body", "Please see the letter.")
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isOk()).andReturn());
        UUID docId = UUID.fromString(sent.get("attachment").get("documentId").asText());
        assertEquals(mine.getId(), documents.findById(docId).orElseThrow().getCaseId());

        // Client sees one unread, opens the matter, downloads the file.
        JsonNode portal = data(call(get("/api/v1/portal/cases"), client, null).andExpect(status().isOk()).andReturn());
        assertEquals(1, unreadFor(portal, mine.getId()));
        JsonNode view = data(call(get("/api/v1/portal/cases/" + mine.getId()), client, null).andExpect(status().isOk()).andReturn());
        assertTrue(view.get("summary").get("disclaimer").asText().contains("not legal advice"));
        JsonNode msg = view.get("messages").get(0);
        assertFalse(msg.get("attachment").has("documentId"));
        call(get("/api/v1/portal/cases/" + mine.getId() + "/messages/" + msg.get("id").asText() + "/attachment"), client, null).andExpect(status().isOk());
        portal = data(call(get("/api/v1/portal/cases"), client, null).andExpect(status().isOk()).andReturn());
        assertEquals(0, unreadFor(portal, mine.getId()));

        // Client replies; staff unread goes up, then clears on open. Read receipt shows on staff side.
        mvc.perform(multipart("/api/v1/portal/cases/" + mine.getId() + "/messages").param("body", "Thank you, received.")
                .header("Authorization", "Bearer " + client)).andExpect(status().isOk());
        JsonNode unread = data(call(get("/api/v1/inbox/unread"), thabo, null).andExpect(status().isOk()).andReturn());
        assertTrue(unread.get("total").asLong() >= 1);
        JsonNode thread = data(call(get("/api/v1/cases/" + mine.getId() + "/inbox"), thabo, null).andExpect(status().isOk()).andReturn());
        assertEquals(2, thread.get("messages").size());
        assertFalse(thread.get("messages").get(0).get("readByClientAt").isNull());
        unread = data(call(get("/api/v1/inbox/unread"), thabo, null).andExpect(status().isOk()).andReturn());
        for (JsonNode r : unread.get("matters")) assertFalse(mine.getId().toString().equals(r.get("caseId").asText()));

        // Stage change with the client message: preview, edit, send.
        JsonNode prev = data(call(get("/api/v1/cases/" + mine.getId() + "/stage-message?stage=summons_issued"), thabo, null).andExpect(status().isOk()).andReturn());
        assertTrue(prev.get("body").asText().contains("not legal advice"));
        call(post("/api/v1/cases/" + mine.getId() + "/stages"), thabo,
                "{\"stage\":\"summons_issued\",\"notifyClient\":true,\"clientMessage\":\"Summons is out. Edited by staff.\"}").andExpect(status().isOk());
        // Without the flag, no message.
        call(post("/api/v1/cases/" + mine.getId() + "/stages"), thabo, "{\"stage\":\"summons_served\"}").andExpect(status().isOk());
        view = data(call(get("/api/v1/portal/cases/" + mine.getId()), client, null).andExpect(status().isOk()).andReturn());
        assertEquals(3, view.get("messages").size());
        assertEquals("stage_update", view.get("messages").get(2).get("kind").asText());
        assertEquals("Summons is out. Edited by staff.", view.get("messages").get(2).get("body").asText());

        // Isolation: another firm, another client, and client tokens on staff routes.
        call(get("/api/v1/cases/" + mine.getId() + "/inbox"), john, null).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/cases/" + mine.getId() + "/inbox").param("body", "x").header("Authorization", "Bearer " + john)).andExpect(status().isNotFound());
        call(get("/api/v1/cases/" + mine.getId() + "/client-summary"), john, null).andExpect(status().isNotFound());
        call(get("/api/v1/portal/cases/" + theirs.getId()), client, null).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/portal/cases/" + theirs.getId() + "/messages").param("body", "x").header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
        call(get("/api/v1/portal/cases/" + theirs.getId() + "/messages/" + msg.get("id").asText() + "/attachment"), client, null).andExpect(status().isNotFound());
        int code = call(get("/api/v1/cases/" + mine.getId() + "/inbox"), client, null).andReturn().getResponse().getStatus();
        assertTrue(code == 401 || code == 403, "client token on staff route gave " + code);
        code = call(get("/api/v1/portal/cases/" + mine.getId()), thabo, null).andReturn().getResponse().getStatus();
        assertTrue(code == 401 || code == 403, "staff token on portal route gave " + code);

        // Empty message is refused; audit has the entries.
        mvc.perform(multipart("/api/v1/portal/cases/" + mine.getId() + "/messages").param("body", " ")
                .header("Authorization", "Bearer " + client)).andExpect(status().isBadRequest());
        String audit = call(get("/api/v1/audit"), thabo, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(audit.contains("inbox.send") && audit.contains("inbox.client_send") && audit.contains("inbox.stage_update"));
    }

    @Test
    void portalAccessNeedsAnAttorneyAndALongPassword() throws Exception {
        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        Client pieter = clients.findAll().stream().filter(c -> firm.equals(c.getTenantId()) && c.getEmail() != null && !"nomsa@example.com".equals(c.getEmail())).findFirst().orElseThrow();
        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        String john = token("smith-associates", "john@smithlaw.com");
        call(post("/api/v1/clients/" + pieter.getId() + "/portal-access"), thabo, "{\"password\":\"short\"}").andExpect(status().isBadRequest());
        call(post("/api/v1/clients/" + pieter.getId() + "/portal-access"), john, "{\"password\":\"long-enough-pass-1\"}").andExpect(status().isNotFound());
        call(post("/api/v1/clients/" + pieter.getId() + "/portal-access"), thabo, "{\"password\":\"long-enough-pass-1\"}").andExpect(status().isOk());
        mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + pieter.getEmail() + "\",\"password\":\"long-enough-pass-1\"}")).andExpect(status().isOk());
        call(post("/api/v1/clients/" + pieter.getId() + "/portal-access"), thabo, "{\"enabled\":false}").andExpect(status().isOk());
    }

    private LegalCase matter(UUID firm, Client c, String number) {
        LegalCase m = new LegalCase();
        m.setTenantId(firm);
        m.setClientId(c.getId());
        m.setCaseNumber(number);
        m.setTitle("Inbox test " + number);
        m.setStatus("open");
        m.setLitigationTrack("high_court_action");
        m.setLitigationStage("letter_of_demand");
        m = cases.save(m);
        return m;
    }

    private long unreadFor(JsonNode portal, UUID caseId) {
        for (JsonNode c : portal.get("cases")) if (caseId.toString().equals(c.get("id").asText())) return c.get("unreadMessages").asLong();
        throw new AssertionError("case not in portal list");
    }

    private org.springframework.test.web.servlet.ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private String token(String slug, String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"password\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }
}
