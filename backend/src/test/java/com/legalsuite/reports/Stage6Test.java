package com.legalsuite.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class Stage6Test {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void dashboardAndAccountingExports() throws Exception {
        String tok = token();
        JsonNode d = data(mvc.perform(get("/api/v1/reports/practice").header("Authorization", "Bearer " + tok)).andExpect(status().isOk()));
        assertEquals(12, d.get("months").size());
        assertTrue(d.get("ageing").has("90+"));
        String xero = mvc.perform(get("/api/v1/exports/accounting.csv").param("format", "xero").param("from", "2000-01-01").param("to", "2100-01-01")
                .header("Authorization", "Bearer " + tok)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(xero.startsWith("*ContactName,"));
        assertTrue(xero.split("\r\n").length > 1, "seed invoices exported");
        String sage = mvc.perform(get("/api/v1/exports/accounting.csv").param("format", "sage").param("from", "2000-01-01").param("to", "2100-01-01")
                .header("Authorization", "Bearer " + tok)).andReturn().getResponse().getContentAsString();
        assertTrue(sage.startsWith("Customer Name,"));
    }

    @Test
    void bundleIsIndexedAndPaginated() throws Exception {
        String tok = token();
        String caseId = data(mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + tok))).get(0).get("id").asText();
        String a = upload(tok, caseId, "Summons.pdf", 2);
        String b = upload(tok, caseId, "Plea.pdf", 3);
        JsonNode cands = data(mvc.perform(get("/api/v1/cases/" + caseId + "/bundle/documents").header("Authorization", "Bearer " + tok)));
        assertTrue(cands.toString().contains("Summons.pdf"));
        byte[] pdf = mvc.perform(post("/api/v1/cases/" + caseId + "/bundle").header("Authorization", "Bearer " + tok).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Trial bundle", "sections", List.of(
                        Map.of("name", "Pleadings", "documentIds", List.of(a, b)))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(6, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("INDEX"));
            assertTrue(text.contains("A-1 to A-2"));
            assertTrue(text.contains("A-5"));
            assertTrue(text.contains("6 of 6"));
        }
    }

    private String upload(String tok, String caseId, String name, int pages) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument d = new PDDocument()) { for (int i = 0; i < pages; i++) d.addPage(new PDPage()); d.save(bos); }
        JsonNode r = data(mvc.perform(multipart("/api/v1/documents/upload").file(new MockMultipartFile("file", name, "application/pdf", bos.toByteArray()))
                .param("caseId", caseId).header("Authorization", "Bearer " + tok)).andExpect(status().isOk()));
        return r.get("id").asText();
    }

    private String token() throws Exception {
        return data(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
