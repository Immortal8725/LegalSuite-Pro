package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.legalsuite.domain.AppUser;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.Totp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** A second, local test operator goes through first sign-in enrolment. */
@SpringBootTest
@AutoConfigureMockMvc
class SuperadminMfaTest extends PlatformTestSupport {
    @Autowired AppUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired PasswordEncoder encoder;

    private MvcResult loginCode(String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"legalsuite\",\"email\":\"second.ops@owlaw.test\",\"password\":\"a-long-test-password\",\"totpCode\":\"" + code + "\"}"))
                .andReturn();
    }

    @Test
    void operatorMustEnrolBeforePlatformScreensAndRecoveryCodesWorkOnce() throws Exception {
        AppUser u = users.findByTenantIdAndEmailIgnoreCase(tenants.findBySlug("legalsuite").orElseThrow().getId(), "second.ops@owlaw.test").orElseGet(() -> {
            AppUser x = new AppUser();
            x.setTenantId(tenants.findBySlug("legalsuite").orElseThrow().getId());
            x.setEmail("second.ops@owlaw.test");
            x.setFirstName("Second");
            x.setLastName("Operator");
            x.setRole("superadmin");
            x.setStatus("active");
            x.setPasswordHash(encoder.encode("a-long-test-password"));
            return users.save(x);
        });
        MvcResult first = login("legalsuite", "second.ops@owlaw.test", "a-long-test-password");
        JsonNode d = data(first);
        assertTrue(d.get("user").get("mfaRequired").asBoolean());
        assertFalse(d.get("user").get("totpEnabled").asBoolean());
        String t = d.get("accessToken").asText();

        MvcResult denied = getAs("/api/v1/platform/firms", t).andExpect(status().isForbidden()).andReturn();
        assertEquals("mfa_required", body(denied).get("code").asText());

        JsonNode started = data(mvc.perform(post("/api/v1/auth/totp/start").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn());
        String secret = started.get("secret").asText();
        assertTrue(started.get("otpauthUrl").asText().startsWith("otpauth://totp/Owlaw:"));
        long step = System.currentTimeMillis() / 1000 / 30;
        postJson("/api/v1/auth/totp/confirm", t, "{\"code\":\"000000\"}").andExpect(status().isBadRequest());
        JsonNode confirmed = data(postJson("/api/v1/auth/totp/confirm", t, "{\"code\":\"" + Totp.codeForStep(secret, step - 1) + "\"}")
                .andExpect(status().isOk()).andReturn());
        JsonNode codes = confirmed.get("recoveryCodes");
        assertEquals(10, codes.size());
        String t2 = confirmed.get("accessToken").asText();
        getAs("/api/v1/platform/firms", t2).andExpect(status().isOk());

        // Sign-in now asks for the second factor; a recovery code works once.
        assertTrue(data(login("legalsuite", "second.ops@owlaw.test", "a-long-test-password")).get("requiresTotp").asBoolean());
        assertEquals(401, loginCode("123456").getResponse().getStatus());
        String rc = codes.get(0).asText();
        assertEquals(200, loginCode(rc.toLowerCase()).getResponse().getStatus());
        assertEquals(401, loginCode(rc).getResponse().getStatus());
        MvcResult viaTotp = loginCode(Totp.codeForStep(secret, step + 1));
        assertEquals(200, viaTotp.getResponse().getStatus());
        String t3 = data(viaTotp).get("accessToken").asText();
        assertEquals(9, data(getAs("/api/v1/auth/me", t3).andExpect(status().isOk()).andReturn()).get("user").get("recoveryCodesLeft").asInt());

        // Cannot be switched off for an operator.
        postJson("/api/v1/auth/totp/disable", t3, "{\"password\":\"a-long-test-password\"}").andExpect(status().isForbidden());

        String log = data(getAs("/api/v1/platform/audit?action=mfa", OperatorTokensBridge.ops(mvc)).andExpect(status().isOk()).andReturn()).toString();
        assertTrue(log.contains("platform.mfa.enrolled") && log.contains("second.ops@owlaw.test"), log);
        users.delete(users.findById(u.getId()).orElseThrow());
    }
}
