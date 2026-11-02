package com.legalsuite.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.OperatorTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BlogApiTest {
    @Autowired MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();

    JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }

    String firmUser() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    @Test
    void firmUsersAndAnonymousCannotWrite() throws Exception {
        String t = firmUser();
        mvc.perform(get("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")).andExpect(status().isForbidden());
        int anon = mvc.perform(post("/api/v1/platform/blog/posts").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(anon).isIn(401, 403);
    }

    @Test
    void publicSeesPublishedOnly() throws Exception {
        String ops = OperatorTokens.ops(mvc);
        JsonNode created = data(mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test post one\",\"body\":\"## Hi\\n\\nText\",\"practiceArea\":\"raf-claims\",\"city\":\"sandton\"}"))
                .andExpect(status().isOk()).andReturn());
        String id = created.get("id").asText();
        assertThat(created.get("slug").asText()).isEqualTo("test-post-one");
        assertThat(created.get("status").asText()).isEqualTo("draft");

        mvc.perform(get("/api/v1/public/blog/posts/test-post-one")).andExpect(status().isNotFound());
        JsonNode list = data(mvc.perform(get("/api/v1/public/blog/posts")).andExpect(status().isOk()).andReturn());
        list.get("results").forEach(p -> assertThat(p.get("status").asText()).isEqualTo("published"));

        mvc.perform(post("/api/v1/platform/blog/posts/" + id + "/publish").header("Authorization", "Bearer " + ops)).andExpect(status().isOk());
        JsonNode pub = data(mvc.perform(get("/api/v1/public/blog/posts/test-post-one")).andExpect(status().isOk()).andReturn());
        assertThat(pub.get("body").asText()).contains("## Hi");
        assertThat(pub.get("practiceAreaLabel").asText()).isEqualTo("RAF claims");
        JsonNode byArea = data(mvc.perform(get("/api/v1/public/blog/posts?area=raf-claims")).andReturn());
        assertThat(byArea.get("total").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode other = data(mvc.perform(get("/api/v1/public/blog/posts?area=tax")).andReturn());
        assertThat(other.get("total").asInt()).isZero();

        // cover image: real PNG accepted, fake rejected
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        mvc.perform(multipart("/api/v1/platform/blog/posts/" + id + "/cover").file(new MockMultipartFile("file", "c.png", "image/png", png))
                .header("Authorization", "Bearer " + ops)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/blog/posts/test-post-one/cover")).andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/platform/blog/posts/" + id + "/cover").file(new MockMultipartFile("file", "c.png", "image/png", "MZ fake".getBytes()))
                .header("Authorization", "Bearer " + ops)).andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/platform/blog/posts/" + id + "/unpublish").header("Authorization", "Bearer " + ops)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/blog/posts/test-post-one")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/blog/posts/test-post-one/cover")).andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/platform/blog/posts/" + id).header("Authorization", "Bearer " + ops)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/blog/posts/" + id).header("Authorization", "Bearer " + ops)).andExpect(status().isNotFound());
    }

    @Test
    void slugRules() throws Exception {
        String ops = OperatorTokens.ops(mvc);
        mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"slug\":\"raf-claims\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"slug\":\"rss\"}")).andExpect(status().isBadRequest());
        JsonNode a = data(mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Same slug\"}")).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/v1/platform/blog/posts").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Same slug\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/platform/blog/posts/" + a.get("id").asText()).header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"practiceArea\":\"nope\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/blog/posts/" + a.get("id").asText() + "/publish").header("Authorization", "Bearer " + ops))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/platform/blog/posts/" + a.get("id").asText()).header("Authorization", "Bearer " + ops)).andExpect(status().isOk());
    }
}
