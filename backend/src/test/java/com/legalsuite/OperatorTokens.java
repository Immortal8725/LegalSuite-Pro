package com.legalsuite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.service.Totp;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Signs in the demo platform operator (ops@legalsuite.pro) with two-factor sign-in, enrolling the authenticator the
 * first time. Platform screens refuse an operator token without 2FA, so every operator test goes through here.
 */
public final class OperatorTokens {
    private OperatorTokens() {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static String cachedToken;
    private static String secret;
    private static long lastStep = -1;

    public static synchronized String ops(MockMvc mvc) throws Exception {
        if (cachedToken != null && works(mvc, cachedToken)) return cachedToken;
        JsonNode first = login(mvc, null);
        if (first.has("accessToken")) {
            // Not enrolled yet in this database: enrol now.
            String t = first.get("accessToken").asText();
            JsonNode started = data(mvc.perform(post("/api/v1/auth/totp/start").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString());
            secret = started.get("secret").asText();
            JsonNode confirmed = data(mvc.perform(post("/api/v1/auth/totp/confirm").header("Authorization", "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + nextCode() + "\"}"))
                    .andReturn().getResponse().getContentAsString());
            cachedToken = confirmed.get("accessToken").asText();
            return cachedToken;
        }
        if (secret == null) throw new IllegalStateException("Operator has 2FA but the test lost its secret");
        cachedToken = login(mvc, nextCode()).get("accessToken").asText();
        return cachedToken;
    }

    /** A code for a time step not used yet (the server accepts one step either side and each step once). */
    private static String nextCode() throws InterruptedException {
        long now = System.currentTimeMillis() / 1000 / 30;
        long step = Math.max(now - 1, lastStep + 1);
        while (step > now + 1) {
            Thread.sleep(1000);
            now = System.currentTimeMillis() / 1000 / 30;
        }
        lastStep = step;
        return Totp.codeForStep(secret, step);
    }

    private static boolean works(MockMvc mvc, String token) throws Exception {
        return mvc.perform(get("/api/v1/platform/firms").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus() == 200;
    }

    private static JsonNode login(MockMvc mvc, String code) throws Exception {
        String body = "{\"firmSlug\":\"legalsuite\",\"email\":\"ops@legalsuite.pro\",\"password\":\"password\""
                + (code == null ? "" : ",\"totpCode\":\"" + code + "\"") + "}";
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode data(String res) throws Exception {
        return JSON.readTree(res).get("data");
    }
}
