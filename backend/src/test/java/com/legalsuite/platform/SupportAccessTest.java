package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.repo.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class SupportAccessTest extends PlatformTestSupport {
    @Autowired TenantRepository tenants;

    @Test
    void operatorHasNoStandingAccessAndViewsOnlyUnderALiveGrant() throws Exception {
        String ndlovu = tenants.findBySlug("ndlovu-partners").orElseThrow().getId().toString();
        String ops = ops();
        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");

        // No grant: nothing. Tenant endpoints only ever show the operator's own (empty) platform account.
        getAs("/api/v1/platform/support/" + ndlovu + "/matters", ops).andExpect(status().isForbidden());
        String opsCases = getAs("/api/v1/cases", ops).andReturn().getResponse().getContentAsString();
        assertFalse(opsCases.contains("Khumalo"));
        String opsClients = getAs("/api/v1/clients", ops).andReturn().getResponse().getContentAsString();
        assertFalse(opsClients.contains("Nomsa"));

        // Grant rules.
        postJson("/api/v1/support-access", thabo, "{\"days\":15,\"reason\":\"Help with invoices\"}").andExpect(status().isBadRequest());
        postJson("/api/v1/support-access", thabo, "{\"days\":3}").andExpect(status().isBadRequest());
        postJson("/api/v1/support-access", token("ndlovu-partners", "sipho@ndlovulaw.co.za"), "{\"days\":3,\"reason\":\"Help me please\"}")
                .andExpect(status().isForbidden());
        JsonNode granted = data(postJson("/api/v1/support-access", thabo, "{\"days\":3,\"reason\":\"Invoice totals look wrong\"}")
                .andExpect(status().isOk()).andReturn());
        assertTrue(granted.get("active").asBoolean());
        String grantId = granted.get("grants").get(0).get("id").asText();
        postJson("/api/v1/support-access", thabo, "{\"days\":3,\"reason\":\"Second grant\"}").andExpect(status().isBadRequest());

        try {
            assertTrue(data(getAs("/api/v1/platform/support", ops).andExpect(status().isOk()).andReturn()).toString().contains("Ndlovu"));
            JsonNode matters = data(getAs("/api/v1/platform/support/" + ndlovu + "/matters", ops).andExpect(status().isOk()).andReturn());
            assertTrue(matters.get("readOnly").asBoolean());
            assertTrue(matters.get("rows").size() > 0);
            data(getAs("/api/v1/platform/support/" + ndlovu + "/clients", ops).andExpect(status().isOk()).andReturn());
            data(getAs("/api/v1/platform/support/" + ndlovu + "/overview", ops).andExpect(status().isOk()).andReturn());

            // Every view is visible to the firm.
            JsonNode mine = data(getAs("/api/v1/support-access", thabo).andExpect(status().isOk()).andReturn());
            assertEquals(3, mine.get("grants").get(0).get("views").size());
            assertTrue(data(getAs("/api/v1/staff-activity", thabo).andReturn()).toString().contains("Viewed matters"));
        } finally {
            postJson("/api/v1/support-access/" + grantId + "/revoke", thabo, "{}").andExpect(status().isOk());
        }
        getAs("/api/v1/platform/support/" + ndlovu + "/matters", ops).andExpect(status().isForbidden());
        assertFalse(data(getAs("/api/v1/support-access", thabo).andReturn()).get("active").asBoolean());
    }
}
