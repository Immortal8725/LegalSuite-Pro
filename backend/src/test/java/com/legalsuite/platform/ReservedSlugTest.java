package com.legalsuite.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class ReservedSlugTest extends PlatformTestSupport {
    @Test
    void reservedSubdomainIsNeverAFirmSlug() throws Exception {
        String body = "{\"firmName\":\"Admin\",\"firmSlug\":\"Admin\",\"email\":\"owner@admin-law-test.co.za\",\"password\":\"password\","
                + "\"firstName\":\"Test\",\"lastName\":\"Owner\",\"planSlug\":\"firm\",\"country\":\"ZA\"}";
        var r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn();
        assertEquals("admin-law", data(r).get("tenant").get("slug").asText());
    }
}
