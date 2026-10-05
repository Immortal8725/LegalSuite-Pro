package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.config.ProdSecretsGuard;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PilotPricingTest {
    @Autowired
    MockMvc mvc;

    @Test
    void capsUnlimitedVoiceAndDoesNotInventARetainer() {
        assertEquals(0, Pricing.capIncludedMinutes(null));
        assertEquals(0, Pricing.capIncludedMinutes(-1));
        assertEquals(0, Pricing.INCLUDED_PSTN_MINUTES);
        assertEquals(new BigDecimal("1199"), Pricing.LIGHT_MONTHLY_ZAR);
        assertEquals(new BigDecimal("79"), Pricing.DID_MONTHLY_ZAR);
        assertEquals(new BigDecimal("4200"), RetainService.pledgedRetainer(null, new BigDecimal("4200")));
        assertEquals(new BigDecimal("5000"), RetainService.pledgedRetainer("5000", new BigDecimal("4200")));
        assertEquals(BigDecimal.ZERO, RetainService.pledgedRetainer(null, null));
        assertEquals(BigDecimal.ZERO, RetainService.pledgedRetainer("2500abc", null));
        assertEquals(BigDecimal.ZERO, VoiceService.rateOrZero(""));
        assertEquals(BigDecimal.ZERO, VoiceService.rateOrZero("-1"));
        assertFalse(OperatorCredentials.present(""));
        assertFalse(OperatorCredentials.present("your-stripe-secret"));
        assertTrue(OperatorCredentials.present("sk_live_example_not_a_real_key"));
    }

    @Test
    void healthIsPublicAndOmitsSecrets() throws Exception {
        String body = mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("up"))
                .andExpect(jsonPath("$.data.database").value("h2"))
                .andExpect(jsonPath("$.data.databaseUp").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .toLowerCase();
        assertFalse(body.contains("password"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("jdbc:"));
    }

    @Test
    void lightIsTheOnlySeatAndHasNoMinuteBundle() throws Exception {
        mvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("light"))
                .andExpect(jsonPath("$.data[0].name").value("Light"))
                .andExpect(jsonPath("$.data[0].priceMonthly").value(1199))
                .andExpect(jsonPath("$.data[0].maxUsers").value(1))
                .andExpect(jsonPath("$.data[0].includedVoiceMinutes").value(0))
                .andExpect(jsonPath("$.data[0].currency").value("ZAR"))
                .andExpect(jsonPath("$.data[0].phoneBilling").value("pay-what-you-use"))
                .andExpect(jsonPath("$.data[0].didMonthly").value(79))
                .andExpect(jsonPath("$.data[0].trustIncluded").value(true));
    }

    @Test
    void postgresProfileRejectsTheDevJwtSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("postgres");
        ProdSecretsGuard guard = new ProdSecretsGuard(
                env, "legalsuite-pro-dev-secret-change-in-production-must-be-64-chars-longXX");
        assertThrows(IllegalStateException.class, () -> guard.run(null));

        MockEnvironment demo = new MockEnvironment();
        new ProdSecretsGuard(demo, "change-in-production").run(null);
    }
}
