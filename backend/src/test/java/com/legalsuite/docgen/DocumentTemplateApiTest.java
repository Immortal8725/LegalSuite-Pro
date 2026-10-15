package com.legalsuite.docgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Branding, generation from a matter, firm uploads and tenant isolation through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentTemplateApiTest {
    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void brandedGenerationFromAMatterSavesPdfAndDocxAndRemembersFields() throws Exception {
        String thabo = login("ndlovu-partners", "thabo@ndlovulaw.co.za");
        ok(mvc.perform(put("/api/v1/firm/branding").header("Authorization", bearer(thabo)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"refPrefix\":\"TN\",\"footerText\":\"\",\"letterheadColour\":\"#7a4b1e\",\"trustAccountNumber\":\"62845510293\"}")));
        byte[] png = DocGenFixtures.logo("NP", new Color(0x7a, 0x4b, 0x1e), true);
        JsonNode brand = ok(mvc.perform(multipart("/api/v1/firm/branding/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png)).header("Authorization", bearer(thabo))));
        assertTrue(brand.get("hasLogo").asBoolean());
        assertEquals("Thabo Ndlovu, Director", brand.get("defaultFooterText").asText());
        byte[] served = mvc.perform(get("/api/v1/firm/branding/logo").header("Authorization", bearer(thabo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(png.length, served.length);

        JsonNode lib = ok(mvc.perform(get("/api/v1/doc-templates").header("Authorization", bearer(thabo))));
        assertTrue(lib.size() >= 9);

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        JsonNode client = ok(send(thabo, "/api/v1/clients",
                "{\"type\":\"individual\",\"firstName\":\"Lerato\",\"lastName\":\"Molefe" + suffix + "\",\"email\":\"lerato" + suffix + "@example.co.za\"}"));
        JsonNode matter = ok(send(thabo, "/api/v1/cases",
                "{\"title\":\"Estate late J Molefe " + suffix + "\",\"clientId\":\"" + client.get("id").asText() + "\",\"caseType\":\"estate\"}"));
        String caseId = matter.get("id").asText();

        JsonNode form = ok(mvc.perform(get("/api/v1/doc-templates/sys-master-deceased-estate-report/form").param("caseId", caseId)
                .header("Authorization", bearer(thabo))));
        assertEquals("Lerato Molefe" + suffix, field(form, "client_name"));
        assertEquals("TN", field(form, "firm_ref"));

        JsonNode out = ok(send(thabo, "/api/v1/doc-templates/sys-master-deceased-estate-report/generate",
                "{\"caseId\":\"" + caseId + "\",\"values\":{\"deceased_name\":\"Johannes Molefe\",\"estate_number\":\"008123/2026\",\"client_id_number\":\"8807150456081\"},"
                        + "\"sign\":{\"signerName\":\"Lerato Molefe\",\"signerEmail\":\"lerato" + suffix + "@example.co.za\"},"
                        + "\"email\":{\"to\":\"lerato" + suffix + "@example.co.za\"}}"));
        assertEquals(2, out.get("documents").size());
        assertEquals("pending", out.get("signature").get("status").asText());
        assertFalse(out.get("email").get("sent").asBoolean(), "SMTP is not configured in tests");

        String pdfId = out.get("documents").get(0).get("id").asText();
        byte[] pdf = mvc.perform(get("/api/v1/documents/" + pdfId + "/download").header("Authorization", bearer(thabo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument d = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(d);
            assertTrue(text.contains("Ndlovu & Partners"));
            assertTrue(text.contains("ESTATE LATE: Johannes Molefe"));
            assertTrue(text.contains("Thabo Ndlovu, Director"));
            assertTrue(text.contains("/ES/01/TN"));
            assertFalse(d.getPage(0).getResources().getXObjectNames().iterator().next().getName().isEmpty());
        }
        String docxId = out.get("documents").get(1).get("id").asText();
        byte[] docx = mvc.perform(get("/api/v1/documents/" + docxId + "/download").header("Authorization", bearer(thabo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor x = new XWPFWordExtractor(d)) {
            assertTrue(x.getText().contains("Johannes Molefe"));
        }

        JsonNode again = ok(mvc.perform(get("/api/v1/doc-templates/sys-general-power-of-attorney-estate/form").param("caseId", caseId)
                .header("Authorization", bearer(thabo))));
        assertEquals("Johannes Molefe", field(again, "deceased_name"), "remembered on the matter");
        assertEquals("008123/2026", field(again, "estate_number"));

        byte[] preview = mvc.perform(post("/api/v1/doc-templates/sys-raf-special-power-of-attorney/preview")
                        .header("Authorization", bearer(thabo)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":\"" + caseId + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument d = Loader.loadPDF(preview)) {
            assertEquals(15, d.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(d).contains("62845510293"));
        }
    }

    @Test
    void firmTemplatesAndLogosAreIsolatedBetweenFirms() throws Exception {
        String thabo = login("ndlovu-partners", "thabo@ndlovulaw.co.za");
        String john = login("smith-associates", "john@smithlaw.com");
        JsonNode up = ok(mvc.perform(multipart("/api/v1/doc-templates/upload")
                .file(new MockMultipartFile("file", "Letter of demand.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sampleDocx()))
                .param("name", "Letter of demand")
                .header("Authorization", bearer(thabo))));
        String id = up.get("id").asText();
        assertTrue(up.get("unknownFields").toString().contains("debtor_name"));

        JsonNode gen = ok(send(thabo, "/api/v1/doc-templates/" + id + "/generate",
                "{\"values\":{\"debtor_name\":\"Acme (Pty) Ltd\"},\"formats\":[\"docx\",\"pdf\"]}"));
        byte[] docx = mvc.perform(get("/api/v1/documents/" + gen.get("documents").get(0).get("id").asText() + "/download")
                .header("Authorization", bearer(thabo))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor x = new XWPFWordExtractor(d)) {
            String text = x.getText();
            assertTrue(text.contains("Ndlovu & Partners demands payment from Acme (Pty) Ltd"));
            assertTrue(text.contains("Amount: [AMOUNT]"), "unfilled bracket prompt stays");
        }

        JsonNode smithLib = ok(mvc.perform(get("/api/v1/doc-templates").header("Authorization", bearer(john))));
        assertFalse(smithLib.toString().contains(id));
        mvc.perform(get("/api/v1/doc-templates/" + id + "/form").header("Authorization", bearer(john))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/doc-templates/" + id + "/generate").header("Authorization", bearer(john))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/doc-templates/" + id).header("Authorization", bearer(john))).andExpect(status().isNotFound());

        JsonNode smithBrand = ok(mvc.perform(get("/api/v1/firm/branding").header("Authorization", bearer(john))));
        assertFalse(smithBrand.get("hasLogo").asBoolean());
        mvc.perform(get("/api/v1/firm/branding/logo").header("Authorization", bearer(john))).andExpect(status().isNotFound());
        JsonNode smithGen = ok(send(john, "/api/v1/doc-templates/sys-new-practice-announcement/generate", "{\"formats\":[\"pdf\"]}"));
        byte[] pdf = mvc.perform(get("/api/v1/documents/" + smithGen.get("documents").get(0).get("id").asText() + "/download")
                .header("Authorization", bearer(john))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument d = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(d);
            assertTrue(text.contains("Smith & Associates"));
            assertFalse(text.contains("Ndlovu"));
        }
        mvc.perform(get("/api/v1/documents/" + gen.get("documents").get(0).get("id").asText() + "/download")
                .header("Authorization", bearer(john))).andExpect(status().isNotFound());

        ok(mvc.perform(delete("/api/v1/doc-templates/" + id).header("Authorization", bearer(thabo))));
    }

    @Test
    void onlyAdminsChangeTheLetterheadAndBadUploadsAreRefused() throws Exception {
        String sipho = login("ndlovu-partners", "sipho@ndlovulaw.co.za");
        mvc.perform(put("/api/v1/firm/branding").header("Authorization", bearer(sipho)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"footerText\":\"x\"}")).andExpect(status().isForbidden());
        String thabo = login("ndlovu-partners", "thabo@ndlovulaw.co.za");
        mvc.perform(multipart("/api/v1/firm/branding/logo").file(new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes()))
                .header("Authorization", bearer(thabo))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/doc-templates/upload").file(new MockMultipartFile("file", "x.docx", "application/octet-stream", "nope".getBytes()))
                .header("Authorization", bearer(thabo))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/firm/branding").header("Authorization", bearer(thabo)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"letterheadColour\":\"red\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/doc-templates/sys-nope/form").header("Authorization", bearer(thabo))).andExpect(status().isNotFound());
        JsonNode fields = ok(mvc.perform(get("/api/v1/doc-templates/fields").header("Authorization", bearer(sipho))));
        assertTrue(fields.toString().contains("trust_account_number"));
    }

    private static byte[] sampleDocx() throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText("{{firm_logo}}");
            var p = d.createParagraph();
            p.createRun().setText("{{firm_name}} demands payment from ");
            p.createRun().setText("{{debtor_");
            p.createRun().setText("name}}.");
            d.createParagraph().createRun().setText("Amount: [AMOUNT]");
            d.createParagraph().createRun().setText("{{footer_text}}");
            d.write(out);
            return out.toByteArray();
        }
    }

    private static String field(JsonNode form, String key) {
        for (JsonNode f : form.get("fields")) if (key.equals(f.get("key").asText())) return f.get("value").asText();
        return null;
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode ok(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        MvcResult res = r.andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("data");
    }

    private String login(String firm, String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmSlug\":\"" + firm + "\",\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    private static String bearer(String t) {
        return "Bearer " + t;
    }
}
