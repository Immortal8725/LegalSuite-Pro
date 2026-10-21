package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Support staff: compulsory 2FA, approvals only, no firm management, billing or client data. */
@SpringBootTest
@AutoConfigureMockMvc
class SupportRoleTest extends PlatformTestSupport {
    @Autowired AppUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    @Test
    void supportStaffWorkApprovalsOnlyAndOperatorManagesThem() throws Exception {
        String ops = ops();
        JsonNode invited = data(postJson("/api/v1/platform/support-staff", ops, "{\"email\":\"help.desk@owlaw.test\",\"firstName\":\"Help\",\"lastName\":\"Desk\"}")
                .andExpect(status().isOk()).andReturn());
        assertEquals("support", invited.get("role").asText());
        assertTrue(data(getAs("/api/v1/platform/support-staff", ops).andExpect(status().isOk()).andReturn()).toString().contains("help.desk@owlaw.test"));

        AppUser u = users.findByTenantIdAndEmailIgnoreCase(tenants.findBySlug("legalsuite").orElseThrow().getId(), "help.desk@owlaw.test").orElseThrow();
        u.setPasswordHash(encoder.encode("a-long-test-password"));
        u.setPasswordChangeRequired(false);
        users.save(u);

        JsonNode first = data(login("legalsuite", "help.desk@owlaw.test", "a-long-test-password"));
        assertTrue(first.get("user").get("mfaRequired").asBoolean());
        String t = first.get("accessToken").asText();
        assertEquals("mfa_required", body(getAs("/api/v1/platform/approvals", t).andExpect(status().isForbidden()).andReturn()).get("code").asText());

        String secret = data(mvc.perform(post("/api/v1/auth/totp/start").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn()).get("secret").asText();
        String t2 = data(postJson("/api/v1/auth/totp/confirm", t, "{\"code\":\"" + Totp.codeForStep(secret, System.currentTimeMillis() / 30000) + "\"}")
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();

        JsonNode inbox = data(getAs("/api/v1/platform/approvals", t2).andExpect(status().isOk()).andReturn());
        assertTrue(inbox.get("counts").has("publicSites") && inbox.get("counts").has("practitioners"));
        getAs("/api/v1/platform/public-sites/queue", t2).andExpect(status().isOk());
        getAs("/api/v1/platform/directory/practitioners", t2).andExpect(status().isOk());
        getAs("/api/v1/platform/directory/listings", t2).andExpect(status().isOk());

        // Not allowed: firm management, billing, the platform log, support look-ins, firm data, managing staff.
        getAs("/api/v1/platform/firms", t2).andExpect(status().isForbidden());
        getAs("/api/v1/platform/billing", t2).andExpect(status().isForbidden());
        getAs("/api/v1/platform/support-staff", t2).andExpect(status().isForbidden());
        assertEquals("support_scope", body(getAs("/api/v1/clients", t2).andExpect(status().isForbidden()).andReturn()).get("code").asText());
        postJson("/api/v1/auth/totp/disable", t2, "{\"password\":\"a-long-test-password\"}").andExpect(status().isForbidden());

        // A firm cannot hand out the support role.
        String owner = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        postJson("/api/v1/users/invite", owner, "{\"email\":\"x@ndlovulaw.co.za\",\"role\":\"support\"}").andExpect(status().isForbidden());

        postJson("/api/v1/platform/support-staff/" + u.getId() + "/remove", ops, "{}").andExpect(status().isOk());
        assertEquals("inactive", users.findById(u.getId()).orElseThrow().getStatus());
        String log = data(getAs("/api/v1/platform/audit?action=support_staff", ops).andExpect(status().isOk()).andReturn()).toString();
        assertTrue(log.contains("platform.support_staff.invited") && log.contains("platform.support_staff.removed"), log);
        users.delete(users.findById(u.getId()).orElseThrow());
    }
}
