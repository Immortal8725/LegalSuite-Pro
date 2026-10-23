package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class ReservedSlugTest extends PlatformTestSupport {
    private static String body(String name, String slug, String email) {
        return "{\"firmName\":\"" + name + "\"," + (slug == null ? "" : "\"firmSlug\":\"" + slug + "\",") + "\"email\":\"" + email
                + "\",\"password\":\"password\",\"firstName\":\"Test\",\"lastName\":\"Owner\",\"planSlug\":\"firm\",\"country\":\"ZA\"}";
    }

    @Test
    void typedReservedSlugIsRejected() throws Exception {
        for (String slug : new String[] {"Admin", "criminal", "divorce-and-family"}) {
            var r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body("Some Firm", slug, "owner@" + slug.toLowerCase() + "-typed-test.co.za")))
                    .andExpect(status().isBadRequest()).andReturn();
            assertTrue(r.getResponse().getContentAsString().contains("reserved by Owlaw"), slug);
        }
    }

    @Test
    void reservedFirmNameGetsLawSuffix() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Criminal", null, "owner@criminal-name-test.co.za")))
                .andExpect(status().is2xxSuccessful()).andReturn();
        assertEquals("criminal-law", data(r).get("tenant").get("slug").asText());
    }
}
