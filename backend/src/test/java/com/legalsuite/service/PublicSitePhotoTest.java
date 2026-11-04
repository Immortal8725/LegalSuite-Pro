package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
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
class PublicSitePhotoTest {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sniffAcceptsOnlyImages() {
        assertEquals("image/png", PublicSitePhotoService.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0}));
        assertEquals("image/webp", PublicSitePhotoService.sniff("RIFF\0\0\0\0WEBPVP8 ".getBytes()));
        assertEquals("image/jpeg", PublicSitePhotoService.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertNull(PublicSitePhotoService.sniff("<svg onload=alert(1)>".getBytes()));
    }

    @Test
    void photoStaysDraftUntilBrandingIsApproved() throws Exception {
        String thabo = token("thabo@ndlovulaw.co.za");
        JsonNode admin = data(mvc.perform(get("/api/v1/public-site").header("Authorization", "Bearer " + thabo)).andExpect(status().isOk()).andReturn());
        String tenantId = admin.get("tenantId").asText();
        byte[] png = png();

        // Not an image, and a slot from another firm or made up, are refused.
        mvc.perform(multipart("/api/v1/public-site/photos/hero").file(new MockMultipartFile("file", "x.png", "image/png", "<svg></svg>   ".getBytes()))
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/public-site/photos/banner").file(new MockMultipartFile("file", "x.png", "image/png", png))
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isBadRequest());
        String sipho = token("sipho@ndlovulaw.co.za");
        mvc.perform(multipart("/api/v1/public-site/photos/hero").file(new MockMultipartFile("file", "x.png", "image/png", png))
                .header("Authorization", "Bearer " + sipho)).andExpect(status().isForbidden());

        // High-resolution files up to 15 MB are accepted; anything larger is refused.
        byte[] big = java.util.Arrays.copyOf(png, 12 * 1024 * 1024);
        mvc.perform(multipart("/api/v1/public-site/photos/hero").file(new MockMultipartFile("file", "big.png", "image/png", big))
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        byte[] tooBig = java.util.Arrays.copyOf(png, 15 * 1024 * 1024 + 1);
        mvc.perform(multipart("/api/v1/public-site/photos/hero").file(new MockMultipartFile("file", "huge.png", "image/png", tooBig))
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isBadRequest());

        mvc.perform(multipart("/api/v1/public-site/photos/hero").file(new MockMultipartFile("file", "hero.png", "image/png", png))
                .header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        byte[] draft = mvc.perform(get("/api/v1/public-site/photos/hero").header("Authorization", "Bearer " + thabo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, draft);
        JsonNode site = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        assertTrue(site.path("heroPhoto").isNull() || site.path("heroPhoto").isMissingNode());
        mvc.perform(get("/api/v1/public/sites/ndlovu-partners/photos/hero")).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        String ops = com.legalsuite.OperatorTokens.ops(mvc);
        mvc.perform(post("/api/v1/platform/public-sites/" + tenantId + "/branding").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approved\"}")).andExpect(status().isOk());

        site = data(mvc.perform(get("/api/v1/public/sites/ndlovu-partners")).andExpect(status().isOk()).andReturn());
        String url = site.get("heroPhoto").asText();
        assertTrue(url.startsWith("/api/v1/public/sites/ndlovu-partners/photos/hero?v="));
        byte[] live = mvc.perform(get("/api/v1/public/sites/ndlovu-partners/photos/hero")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, live);

        // Clean up so the shared demo site keeps its default pictures.
        mvc.perform(delete("/api/v1/public-site/photos/hero").header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/public-site/submit").header("Authorization", "Bearer " + thabo)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/public-sites/" + tenantId + "/branding").header("Authorization", "Bearer " + ops)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approved\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/sites/ndlovu-partners/photos/hero")).andExpect(status().isNotFound());
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(1, 1, 0xff112233);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
