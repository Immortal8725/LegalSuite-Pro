package com.legalsuite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Signs in the demo platform operator (ops@legalsuite.pro) for tests. */
public final class OperatorTokens {
    private OperatorTokens() {}

    private static final ObjectMapper JSON = new ObjectMapper();

    public static String ops(MockMvc mvc) throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"legalsuite\",\"email\":\"ops@legalsuite.pro\",\"password\":\"password\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = JSON.readTree(res).get("data");
        return data.get("accessToken").asText();
    }
}
