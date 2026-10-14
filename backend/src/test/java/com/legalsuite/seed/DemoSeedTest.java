package com.legalsuite.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The SA demo firm has documents that open, e-sign in both states, expenses and team messages. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoSeedTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void southAfricanDemoFirmHasWorkOnEveryScreen() throws Exception {
        String thabo = token();
        JsonNode docs = list("/api/v1/documents", thabo);
        assertTrue(docs.size() >= 7, "documents seeded: " + docs.size());
        for (JsonNode d : docs) {
            byte[] body = mvc.perform(get("/api/v1/documents/" + d.get("id").asText() + "/download")
                            .header("Authorization", "Bearer " + thabo))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertEquals("%PDF", new String(body, 0, 4, StandardCharsets.ISO_8859_1), "document " + d.get("name"));
        }
        JsonNode sigs = list("/api/v1/signatures", thabo);
        boolean signed = false;
        boolean pending = false;
        for (JsonNode s : sigs) {
            signed |= "signed".equals(s.path("status").asText());
            pending |= "pending".equals(s.path("status").asText());
        }
        assertTrue(signed && pending, "a signed and a pending mandate are seeded");
        assertTrue(list("/api/v1/expenses", thabo).size() >= 5);
        JsonNode convs = list("/api/v1/conversations", thabo);
        assertTrue(convs.size() >= 2);
        int messages = 0;
        for (JsonNode c : convs) messages += c.path("messages").size();
        assertTrue(messages >= 6);
    }

    private JsonNode list(String path, String token) throws Exception {
        return data(mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
