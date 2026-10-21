package com.legalsuite.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "legalsuite.backup.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "legalsuite.backup.token=test-backup-token-0123456789abcdef0123"})
@AutoConfigureMockMvc
class BackupExportTest {
    private static final String TOKEN = "test-backup-token-0123456789abcdef0123";
    @Autowired private MockMvc mvc;

    @Test
    void refusesWithoutTheToken() throws Exception {
        mvc.perform(post("/api/v1/ops/backup")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ops/backup").header("X-Backup-Token", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportIsEncryptedAndDecryptsToEveryTable() throws Exception {
        byte[] file = mvc.perform(post("/api/v1/ops/backup").header("X-Backup-Token", TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String head = new String(file, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("OWLB1", head);
        assertTrue(!new String(file, StandardCharsets.ISO_8859_1).contains("smith-associates"), "ciphertext must not leak data");
        byte[] key = Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        byte[] gz = BackupService.decrypt(file, key);
        String text = new String(new GZIPInputStream(new ByteArrayInputStream(gz)).readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"table\":\"tenants\""));
        assertTrue(text.contains("smith-associates"));
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backup.lastStatus").value("ok"))
                .andExpect(jsonPath("$.data.databaseUp").value(true));
    }
}
