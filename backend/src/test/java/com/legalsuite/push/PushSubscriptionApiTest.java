package com.legalsuite.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.domain.AppUser;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.TenantRepository;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "legalsuite.push.vapid-public-key=BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
        "legalsuite.push.vapid-private-key=UUxI4O8-FbRouAevSmBQ6o18hgE4nSG3qwvJTfKc-ls"})
@AutoConfigureMockMvc
class PushSubscriptionApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private PushService push;
    @Autowired private PushSubscriptionRepository subs;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void staffAndClientSubscribeAndDeadPushesArePruned() throws Exception {
        push.setDeliverForTests(false);
        JsonNode key = data(mvc.perform(get("/api/v1/public/push/key")).andExpect(status().isOk()).andReturn());
        assertTrue(key.get("enabled").asBoolean());

        String thabo = token("ndlovu-partners", "thabo@ndlovulaw.co.za");
        String sub = subscription("https://push.example.test/staff-1");
        call(post("/api/v1/push/subscriptions"), thabo, sub).andExpect(status().isOk());
        call(post("/api/v1/push/subscriptions"), thabo, sub).andExpect(status().isOk());
        assertEquals(1, data(call(get("/api/v1/push/subscriptions"), thabo, null).andReturn()).get("devices").asInt());
        call(post("/api/v1/push/subscriptions"), thabo, "{\"endpoint\":\"http://insecure\",\"keys\":{}}").andExpect(status().isBadRequest());

        String client = portalToken();
        call(post("/api/v1/portal/push/subscriptions"), client, subscription("https://push.example.test/client-1")).andExpect(status().isOk());
        // A portal token cannot use the staff route and the other way round.
        call(post("/api/v1/push/subscriptions"), client, sub).andExpect(status().isForbidden());
        call(post("/api/v1/portal/push/subscriptions"), thabo, sub).andExpect(status().isForbidden());

        UUID firm = tenants.findBySlug("ndlovu-partners").orElseThrow().getId();
        AppUser u = users.findAll().stream().filter(x -> firm.equals(x.getTenantId()) && "thabo@ndlovulaw.co.za".equals(x.getEmail())).findFirst().orElseThrow();
        int before = push.queuedForTests();
        push.toUser(firm, u.getId(), "Deadline in 3 days", "Plea due: CASE-1", "/cases/1");
        for (int i = 0; i < 50 && push.queuedForTests() == before; i++) Thread.sleep(20);
        assertEquals(before + 1, push.queuedForTests());
        assertTrue(push.lastPayloadForTests().contains("Deadline in 3 days"));

        PushSubscription s = subs.findByEndpoint("https://push.example.test/staff-1").orElseThrow();
        push.record(s.getId(), 500);
        assertEquals(1, subs.findById(s.getId()).orElseThrow().getFailures());
        push.record(s.getId(), 410);
        assertTrue(subs.findById(s.getId()).isEmpty());

        call(post("/api/v1/portal/push/subscriptions/remove"), client, "{\"endpoint\":\"https://push.example.test/client-1\"}").andExpect(status().isOk());
        assertTrue(subs.findByEndpoint("https://push.example.test/client-1").isEmpty());
    }

    private String subscription(String endpoint) throws Exception {
        KeyPair kp = WebPushCrypto.newKeyPair();
        String p = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) kp.getPublic()));
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + p + "\",\"auth\":\"" + WebPushCrypto.b64(new byte[16]) + "\"}}";
    }

    private org.springframework.test.web.servlet.ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private String token(String slug, String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"password\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }
}
