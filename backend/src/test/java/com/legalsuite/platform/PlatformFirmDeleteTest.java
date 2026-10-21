package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformFirmDeleteTest extends PlatformTestSupport {
    @Autowired TenantRepository tenants;
    @Autowired AppUserRepository users;

    @Test
    void deleteNeedsNameAndReasonBlocksEverythingAndPurgeWaits30Days() throws Exception {
        JsonNode reg = registerFirm("Delete Me Attorneys");
        String id = reg.get("tenant").get("id").asText();
        String slug = reg.get("tenant").get("slug").asText();
        String name = reg.get("tenant").get("firmName").asText();
        String owner = reg.get("accessToken").asText();
        String ownerEmail = reg.get("user").get("email").asText();
        String ops = ops();

        postJson("/api/v1/platform/firms/" + id + "/delete", ops, "{\"reason\":\"Closed the practice\",\"confirmName\":\"wrong\"}").andExpect(status().isBadRequest());
        postJson("/api/v1/platform/firms/" + id + "/delete", ops, "{\"reason\":\"\",\"confirmName\":\"" + name + "\"}").andExpect(status().isBadRequest());
        postJson("/api/v1/platform/firms/" + id + "/delete", token("ndlovu-partners", "thabo@ndlovulaw.co.za"),
                "{\"reason\":\"Closed the practice\",\"confirmName\":\"" + name + "\"}").andExpect(status().isForbidden());
        JsonNode d = data(postJson("/api/v1/platform/firms/" + id + "/delete", ops, "{\"reason\":\"Closed the practice\",\"confirmName\":\"" + name + "\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("deleted", d.get("status").asText());

        MvcResult blocked = getAs("/api/v1/auth/me", owner).andExpect(status().isForbidden()).andReturn();
        assertEquals("firm_deleted", body(blocked).get("code").asText());
        MvcResult signIn = login(slug, ownerEmail, "password");
        assertEquals(403, signIn.getResponse().getStatus());
        assertTrue(body(signIn).get("message").asText().contains("deleted"));
        mvc.perform(get("/api/v1/public/sites/" + slug)).andExpect(status().isNotFound());

        // Purge is refused inside 30 days.
        postJson("/api/v1/platform/firms/" + id + "/purge", ops, "{\"reason\":\"POPIA erasure\",\"confirmName\":\"" + name + "\"}").andExpect(status().isBadRequest());
        Tenant t = tenants.findById(UUID.fromString(id)).orElseThrow();
        t.setDeletedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        tenants.save(t);
        JsonNode p = data(postJson("/api/v1/platform/firms/" + id + "/purge", ops, "{\"reason\":\"POPIA erasure\",\"confirmName\":\"" + name + "\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("purged", p.get("status").asText());
        assertTrue(users.findByTenantIdOrderByLastNameAsc(UUID.fromString(id)).isEmpty());
        Tenant after = tenants.findById(UUID.fromString(id)).orElseThrow();
        assertTrue(after.getFirmName().startsWith("Purged firm"));
        // The rest of the platform still works.
        getAs("/api/v1/platform/firms", ops).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk());
    }

    @Test
    void restoreBringsAFirmBackAndPlatformCannotBeDeleted() throws Exception {
        JsonNode reg = registerFirm("Restore Me Attorneys");
        String id = reg.get("tenant").get("id").asText();
        String name = reg.get("tenant").get("firmName").asText();
        String ops = ops();
        postJson("/api/v1/platform/firms/" + id + "/delete", ops, "{\"reason\":\"Asked to close\",\"confirmName\":\"" + name + "\"}").andExpect(status().isOk());
        JsonNode r = data(postJson("/api/v1/platform/firms/" + id + "/restore", ops, "{\"reason\":\"Changed their mind\"}").andExpect(status().isOk()).andReturn());
        assertEquals("trial", r.get("status").asText());
        Tenant platform = tenants.findBySlug(PlatformGuard.PLATFORM_SLUG).orElseThrow();
        postJson("/api/v1/platform/firms/" + platform.getId() + "/delete", ops,
                "{\"reason\":\"Should never work\",\"confirmName\":\"" + platform.getFirmName() + "\"}").andExpect(status().isBadRequest());
    }
}
