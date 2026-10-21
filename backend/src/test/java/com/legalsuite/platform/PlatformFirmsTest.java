package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.domain.Client;
import com.legalsuite.repo.ClientRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformFirmsTest extends PlatformTestSupport {
    @Autowired ClientRepository clients;
    @Autowired PasswordEncoder encoder;

    private JsonNode find(String ops, String id) throws Exception {
        for (JsonNode f : data(getAs("/api/v1/platform/firms", ops).andExpect(status().isOk()).andReturn())) {
            if (id.equals(f.get("id").asText())) return f;
        }
        return null;
    }

    @Test
    void firmsListIsOperatorOnlyAndHasNoClientData() throws Exception {
        String ops = ops();
        JsonNode list = data(getAs("/api/v1/platform/firms", ops).andExpect(status().isOk()).andReturn());
        assertTrue(list.size() >= 2);
        assertTrue(list.toString().contains("ndlovu-partners"));
        assertTrue(!list.toString().contains("\"slug\":\"legalsuite\""));
        JsonNode one = list.get(0);
        for (String k : new String[] {"firmName", "planName", "status", "seats", "createdAt", "trialEndsAt", "lastActivityAt"}) assertTrue(one.has(k), k);
        assertTrue(!list.toString().contains("Nomsa"));
        getAs("/api/v1/platform/firms", token("ndlovu-partners", "thabo@ndlovulaw.co.za")).andExpect(status().isForbidden());
    }

    @Test
    void suspendBlocksStaffAndPortalThenReactivateRestores() throws Exception {
        JsonNode reg = registerFirm("Suspend Me Attorneys");
        String id = reg.get("tenant").get("id").asText();
        String slug = reg.get("tenant").get("slug").asText();
        String owner = reg.get("accessToken").asText();
        String ownerEmail = reg.get("user").get("email").asText();
        Client c = new Client();
        c.setTenantId(UUID.fromString(id));
        c.setFirstName("Portal");
        c.setLastName("Client");
        c.setEmail("portal@" + slug + ".co.za");
        c.setPortalEnabled(true);
        c.setPortalPasswordHash(encoder.encode("password"));
        clients.save(c);
        String ops = ops();

        postJson("/api/v1/platform/firms/" + id + "/suspend", ops, "{\"reason\":\"\"}").andExpect(status().isBadRequest());
        JsonNode s = data(postJson("/api/v1/platform/firms/" + id + "/suspend", ops, "{\"reason\":\"Unpaid for three months\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("suspended", s.get("status").asText());

        // Live token stops working with a clear message, sign-in is refused, portal is refused.
        MvcResult blocked = getAs("/api/v1/auth/me", owner).andExpect(status().isForbidden()).andReturn();
        assertEquals("firm_suspended", body(blocked).get("code").asText());
        MvcResult signIn = login(slug, ownerEmail, "password");
        assertEquals(403, signIn.getResponse().getStatus());
        assertTrue(body(signIn).get("message").asText().contains("suspended"));
        mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"portal@" + slug + ".co.za\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());

        postJson("/api/v1/platform/firms/" + id + "/reactivate", ops, "{\"reason\":\"Paid in full\"}").andExpect(status().isOk());
        assertEquals("trial", find(ops, id).get("status").asText());
        assertEquals(200, login(slug, ownerEmail, "password").getResponse().getStatus());
        mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"portal@" + slug + ".co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk());

        // Audited in the firm's own log.
        String fresh = data(login(slug, ownerEmail, "password")).get("accessToken").asText();
        String log = data(getAs("/api/v1/audit", fresh).andExpect(status().isOk()).andReturn()).toString();
        assertTrue(log.contains("platform.firm.suspended") && log.contains("platform.firm.reactivated"), log);
    }

    @Test
    void extendTrialAndChangePlan() throws Exception {
        JsonNode reg = registerFirm("Trial Extension Inc");
        String id = reg.get("tenant").get("id").asText();
        String ops = ops();
        String before = find(ops, id).get("trialEndsAt").asText();
        postJson("/api/v1/platform/firms/" + id + "/extend-trial", ops, "{\"days\":200,\"reason\":\"Too long\"}").andExpect(status().isBadRequest());
        JsonNode ext = data(postJson("/api/v1/platform/firms/" + id + "/extend-trial", ops, "{\"days\":7,\"reason\":\"Asked for more time\"}")
                .andExpect(status().isOk()).andReturn());
        assertTrue(java.time.Instant.parse(ext.get("trialEndsAt").asText()).isAfter(java.time.Instant.parse(before)));
        postJson("/api/v1/platform/firms/" + id + "/plan", ops, "{\"planSlug\":\"nope\",\"reason\":\"Testing\"}").andExpect(status().isBadRequest());
        JsonNode plan = data(postJson("/api/v1/platform/firms/" + id + "/plan", ops, "{\"planSlug\":\"practice\",\"reason\":\"Upgrade agreed\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("practice", plan.get("planSlug").asText());
        assertNotNull(plan.get("lastPlatformAction").asText());
    }
}
