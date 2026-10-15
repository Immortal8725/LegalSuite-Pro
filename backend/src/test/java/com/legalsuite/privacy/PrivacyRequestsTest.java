package com.legalsuite.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Invoice;
import com.legalsuite.repo.AuditLogRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.InvoiceRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POPIA access and deletion: self-service export, admin-approved anonymisation, retention kept, audit logged. */
@SpringBootTest
@AutoConfigureMockMvc
class PrivacyRequestsTest {
    @Autowired MockMvc mvc;
    @Autowired ClientRepository clients;
    @Autowired InvoiceRepository invoices;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PasswordEncoder encoder;
    final ObjectMapper json = new ObjectMapper();

    record Firm(String slug, String owner, UUID tenantId) {}

    @Test
    void portalClientExportsAndAsksForDeletionAndTheAdminAnonymises() throws Exception {
        Firm f = firm();
        Client c = portalClient(f, "Nandi", "Khoza");
        Invoice inv = new Invoice();
        inv.setTenantId(f.tenantId());
        inv.setClientId(c.getId());
        inv.setInvoiceNumber("INV-P-" + UUID.randomUUID().toString().substring(0, 5));
        inv.setTotal(new BigDecimal("1500.00"));
        invoices.save(inv);
        String portal = portalLogin(f, c.getEmail());

        JsonNode exp = data(mvc.perform(get("/api/v1/portal/privacy/export").header("Authorization", "Bearer " + portal)));
        assertEquals(c.getEmail(), exp.get("client").get("email").asText());
        assertEquals(1, exp.get("invoices").size());
        assertFalse(exp.has("notes"), "firm notes are not in the portal export");
        byte[] zip = mvc.perform(get("/api/v1/portal/privacy/export?format=zip").header("Authorization", "Bearer " + portal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertEquals("personal-information.json", z.getNextEntry().getName());
        }

        JsonNode req = data(send(portal, "/api/v1/portal/privacy/erasure", "{\"reason\":\"I moved firms\"}"));
        assertEquals("pending", req.get("status").asText());
        send(portal, "/api/v1/portal/privacy/erasure", "{}").andExpect(status().isConflict());
        // Only a firm admin sees and decides.
        mvc.perform(get("/api/v1/privacy/requests").header("Authorization", "Bearer " + portal)).andExpect(status().isForbidden());
        JsonNode list = data(mvc.perform(get("/api/v1/privacy/requests").header("Authorization", "Bearer " + f.owner())));
        assertEquals(1, list.size());
        String id = req.get("id").asText();
        send(f.owner(), "/api/v1/privacy/requests/" + id + "/reject", "{}").andExpect(status().isBadRequest());
        JsonNode done = data(send(f.owner(), "/api/v1/privacy/requests/" + id + "/approve", "{\"note\":\"Identity checked\"}"));
        assertEquals("completed", done.get("status").asText());
        assertTrue(done.get("outcome").asText().contains("1 invoices"));
        send(f.owner(), "/api/v1/privacy/requests/" + id + "/approve", "{}").andExpect(status().isBadRequest());

        Client after = clients.findById(c.getId()).orElseThrow();
        assertEquals("anonymised", after.getStatus());
        assertNull(after.getEmail());
        assertNull(after.getPhone());
        assertFalse(after.isPortalEnabled());
        assertEquals(1, invoices.findByTenantIdAndClientId(f.tenantId(), c.getId()).size(), "invoices are kept");
        mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + f.slug() + "\",\"email\":\"" + c.getEmail() + "\",\"password\":\"Portal-Pass-1\"}"))
                .andExpect(status().isUnauthorized());
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> f.tenantId().equals(a.getTenantId()) && "privacy.erasure.completed".equals(a.getAction())));
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> f.tenantId().equals(a.getTenantId()) && "privacy.export".equals(a.getAction())));
    }

    @Test
    void firmUserExportsAndAnAdminAnonymisesThemButNotTheOnlyOwner() throws Exception {
        Firm f = firm();
        JsonNode me = data(mvc.perform(get("/api/v1/privacy/me/export").header("Authorization", "Bearer " + f.owner())));
        assertTrue(me.get("user").get("email").asText().contains("@"));
        JsonNode r = data(send(f.owner(), "/api/v1/privacy/me/erasure", "{}"));
        send(f.owner(), "/api/v1/privacy/requests/" + r.get("id").asText() + "/approve", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void otherFirmsAndNonAdminsCannotReachAClient() throws Exception {
        Firm a = firm();
        Firm b = firm();
        Client c = portalClient(a, "Pieter", "Botha");
        mvc.perform(get("/api/v1/privacy/clients/" + c.getId() + "/export").header("Authorization", "Bearer " + b.owner()))
                .andExpect(status().isNotFound());
        send(b.owner(), "/api/v1/privacy/clients/" + c.getId() + "/erasure", "{}").andExpect(status().isNotFound());
        JsonNode ok = data(mvc.perform(get("/api/v1/privacy/clients/" + c.getId() + "/export").header("Authorization", "Bearer " + a.owner())));
        assertEquals("Botha", ok.get("client").get("lastName").asText());
        assertTrue(ok.has("notes"));
    }

    // ---------- fixtures: a fresh firm per test, nothing from the demo seed ----------

    Firm firm() throws Exception {
        String slug = "pop-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"Privacy " + slug + "\",\"email\":\"o@" + slug + ".co.za\",\"password\":\"Sup3r-Secret-99\",\"firstName\":\"Ayanda\",\"lastName\":\"Nkosi\",\"firmSlug\":\"" + slug + "\",\"planSlug\":\"firm\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode d = json.readTree(body).get("data");
        String token = d.get("accessToken").asText();
        JsonNode meNode = data(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)));
        UUID tenantId = UUID.fromString(meNode.has("tenantId") ? meNode.get("tenantId").asText() : meNode.get("tenant").get("id").asText());
        return new Firm(slug, token, tenantId);
    }

    Client portalClient(Firm f, String first, String last) {
        Client c = new Client();
        c.setTenantId(f.tenantId());
        c.setFirstName(first);
        c.setLastName(last);
        c.setEmail(first.toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 6) + "@example.co.za");
        c.setPhone("082 555 0101");
        c.setPortalEnabled(true);
        c.setPortalPasswordHash(encoder.encode("Portal-Pass-1"));
        return clients.save(c);
    }

    String portalLogin(Firm f, String email) throws Exception {
        String body = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + f.slug() + "\",\"email\":\"" + email + "\",\"password\":\"Portal-Pass-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    ResultActions send(String token, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
}
