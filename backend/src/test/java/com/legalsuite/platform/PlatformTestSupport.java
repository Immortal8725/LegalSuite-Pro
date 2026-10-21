package com.legalsuite.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.OperatorTokens;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

abstract class PlatformTestSupport {
    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper json = new ObjectMapper();

    protected JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected MvcResult login(String slug, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    protected String token(String slug, String email) throws Exception {
        MvcResult r = login(slug, email, "password");
        return data(r).get("accessToken").asText();
    }

    protected String ops() throws Exception {
        return OperatorTokens.ops(mvc);
    }

    /** A fresh firm so suspension tests do not touch shared demo firms. */
    protected JsonNode registerFirm(String name) throws Exception {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + UUID.randomUUID().toString().substring(0, 6);
        String email = "owner@" + slug + ".co.za";
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmName\":\"" + name + "\",\"firmSlug\":\"" + slug + "\",\"email\":\"" + email
                                + "\",\"password\":\"password\",\"firstName\":\"Test\",\"lastName\":\"Owner\",\"planSlug\":\"firm\",\"country\":\"ZA\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return data(r);
    }

    protected ResultActions postJson(String url, String token, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getAs(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }
}
