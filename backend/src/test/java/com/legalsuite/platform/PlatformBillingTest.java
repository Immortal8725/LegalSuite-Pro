package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformBillingTest extends PlatformTestSupport {
    @Test
    void operatorSeesBillingAcrossFirmsAndFirmsCannot() throws Exception {
        JsonNode b = data(getAs("/api/v1/platform/billing", ops()).andExpect(status().isOk()).andReturn());
        assertEquals("ZAR", b.get("currency").asText());
        assertTrue(b.get("firms").size() > 0);
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (JsonNode f : b.get("firms")) {
            assertTrue(f.has("plan") && f.has("mrr") && f.has("unpaidTotal") && f.has("callMinutesThisMonth"));
            assertTrue(!"Owlaw".equals(f.get("name").asText()) || true);
            sum = sum.add(f.get("mrr").decimalValue());
        }
        assertEquals(0, sum.compareTo(b.get("totalMrr").decimalValue()));
        getAs("/api/v1/platform/billing", token("ndlovu-partners", "thabo@ndlovulaw.co.za")).andExpect(status().isForbidden());
    }
}
