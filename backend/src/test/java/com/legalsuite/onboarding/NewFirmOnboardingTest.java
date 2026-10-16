package com.legalsuite.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A freshly registered firm: opening a trust account, fitness text from its own data, and sign-in edge cases. */
@SpringBootTest
@AutoConfigureMockMvc
class NewFirmOnboardingTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ownerOpensTrustAccountAndItAppearsInRecon() throws Exception {
        String owner = register();
        assertEquals(0, data(auth(get("/api/v1/trust/accounts"), owner)).size());

        JsonNode acct = data(auth(post("/api/v1/trust/accounts"), owner,
                "{\"accountName\":\"Firm Trust Account\",\"bankName\":\"FNB\"}").andExpect(status().isOk()));
        assertEquals("Firm Trust Account", acct.get("accountName").asText());
        assertEquals(0, acct.get("balance").decimalValue().signum());
        assertEquals("trust", acct.get("accountType").asText());

        assertEquals(1, data(auth(get("/api/v1/trust/accounts"), owner)).size());
        assertEquals("balanced", data(auth(get("/api/v1/trust/recon"), owner)).get("worstStatus").asText());

        // Same name at the same bank is refused; blanks are refused.
        auth(post("/api/v1/trust/accounts"), owner, "{\"accountName\":\"firm trust account\",\"bankName\":\"fnb\"}")
                .andExpect(status().isBadRequest());
        auth(post("/api/v1/trust/accounts"), owner, "{\"accountName\":\"\",\"bankName\":\"FNB\"}")
                .andExpect(status().isBadRequest());
        auth(post("/api/v1/trust/accounts"), owner, "{\"accountName\":\"X\",\"bankName\":\"FNB\",\"accountType\":\"business\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotOpenTrustAccount() throws Exception {
        String maria = login("smith-associates", "maria@smithlaw.com"); // attorney, not a firm admin
        String alex = login("smith-associates", "alex@smithlaw.com"); // associate
        auth(post("/api/v1/trust/accounts"), alex, "{\"accountName\":\"Side\",\"bankName\":\"ABSA\"}")
                .andExpect(status().isForbidden());
        auth(post("/api/v1/trust/accounts"), maria, "{\"accountName\":\"Side\",\"bankName\":\"ABSA\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void newFirmFitnessHasNoDemoText() throws Exception {
        String owner = register();
        JsonNode fit = data(auth(get("/api/v1/fitness"), owner));
        String next = fit.get("next").asText();
        assertFalse(next.contains("11,750"), next);
        assertFalse(next.contains("C-2002"), next);
        assertTrue(next.contains("Create your firm's trust account"), next);

        auth(post("/api/v1/trust/accounts"), owner, "{\"accountName\":\"Trust\",\"bankName\":\"Nedbank\"}")
                .andExpect(status().isOk());
        String after = data(auth(get("/api/v1/fitness"), owner)).get("next").asText();
        assertFalse(after.contains("Create your firm's trust account"), after);
    }

    @Test
    void unknownFirmSignInIsACleanError() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"no-such-firm-" + UUID.randomUUID() + "\",\"email\":\"a@b.co.za\",\"password\":\"x\"}"))
                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Exception"), body);
        assertTrue(body.toLowerCase().contains("firm"), body);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co.za\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    private String register() throws Exception {
        String slug = "onb-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"Onboard " + slug + "\",\"email\":\"o@" + slug + ".co.za\",\"password\":\"Sup3r-Secret-99\",\"firstName\":\"Zanele\",\"lastName\":\"Dube\",\"firmSlug\":\"" + slug + "\",\"planSlug\":\"firm\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private String login(String firm, String email) throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"" + firm + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private ResultActions auth(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }

    private ResultActions auth(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body)
            throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
