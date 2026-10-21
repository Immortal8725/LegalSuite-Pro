package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformAuditTest extends PlatformTestSupport {

    @Test
    void platformActionsShowInPlatformLogAndOnlyInThatFirmsStaffActivity() throws Exception {
        JsonNode reg = registerFirm("Audit Trail Attorneys");
        String id = reg.get("tenant").get("id").asText();
        String owner = reg.get("accessToken").asText();
        String ops = ops();
        postJson("/api/v1/platform/firms/" + id + "/extend-trial", ops, "{\"days\":5,\"reason\":\"Audit test extension\"}").andExpect(status().isOk());

        JsonNode log = data(getAs("/api/v1/platform/audit?tenantId=" + id, ops).andExpect(status().isOk()).andReturn());
        assertTrue(log.toString().contains("platform.firm.trial_extended"));
        assertTrue(log.toString().contains("Audit Trail Attorneys"));
        assertTrue(log.toString().contains("ops@legalsuite.pro"));

        JsonNode mine = data(getAs("/api/v1/staff-activity", owner).andExpect(status().isOk()).andReturn());
        assertTrue(mine.toString().contains("Extended the trial"));
        assertTrue(mine.toString().contains("Audit test extension"));

        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        assertFalse(data(getAs("/api/v1/staff-activity", thabo).andExpect(status().isOk()).andReturn()).toString().contains("Audit test extension"));
        getAs("/api/v1/platform/audit", thabo).andExpect(status().isForbidden());
        getAs("/api/v1/staff-activity", token("ndlovu-partners", "sipho@ndlovulaw.co.za")).andExpect(status().isForbidden());
    }
}
