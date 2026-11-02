package com.legalsuite.esign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.service.SignatureService;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentSigningFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private SignatureService signatures;
    @MockitoBean MailGateway mail;
    private final ObjectMapper json = new ObjectMapper();

    // Tiny 1x1 PNG.
    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    void staffRequestsSignatureClientSignsWithOtpAndGetsSignedPdf() throws Exception {
        when(mail.configured()).thenReturn(true);
        String staff = token();
        String client = portalToken();
        String caseId = data(mvc.perform(get("/api/v1/portal/cases").header("Authorization", "Bearer " + client)).andReturn()).get("cases").get(0).get("id").asText();

        byte[] pdf = twoPagePdf();
        MvcResult up = mvc.perform(multipart("/api/v1/documents/upload")
                .file(new MockMultipartFile("file", "Mandate.pdf", "application/pdf", pdf))
                .param("caseId", caseId).param("category", "correspondence")
                .header("Authorization", "Bearer " + staff)).andExpect(status().isOk()).andReturn();
        String docId = data(up).get("id").asText();

        // Portal tokens cannot request signatures.
        mvc.perform(post("/api/v1/documents/" + docId + "/signature-request").header("Authorization", "Bearer " + client)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        JsonNode req = data(mvc.perform(post("/api/v1/documents/" + docId + "/signature-request").header("Authorization", "Bearer " + staff)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn());
        String id = req.get("id").asText();
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), req.get("documentHash").asText());
        assertEquals("nomsa@example.com", req.get("signerEmail").asText());

        // The client sees it in the portal.
        JsonNode mine = data(mvc.perform(get("/api/v1/portal/signatures").header("Authorization", "Bearer " + client)).andExpect(status().isOk()).andReturn());
        boolean found = false;
        for (JsonNode n : mine) found |= id.equals(n.get("id").asText());
        assertTrue(found);

        JsonNode view = data(mvc.perform(get("/api/v1/sign/" + id)).andExpect(status().isOk()).andReturn());
        assertTrue(view.get("otpRequired").asBoolean());
        mvc.perform(get("/api/v1/sign/" + id + "/document")).andExpect(status().isOk());

        String body = "{\"signatureDataUrl\":\"" + PNG + "\",\"typedName\":\"Nomsa Dlamini\",\"signerIdentityNumber\":\"8001015009087\",\"otp\":\"000000\"}";
        // No code asked for yet.
        mvc.perform(post("/api/v1/sign/" + id).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sign/" + id + "/otp")).andExpect(status().isOk());
        verify(mail).send(anyString(), org.mockito.ArgumentMatchers.eq("nomsa@example.com"), anyString(), anyString());
        String code = signatures.lastOtpForTests();
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/v1/sign/" + id).contentType(MediaType.APPLICATION_JSON).content(body.replace("000000", wrong))).andExpect(status().isBadRequest());
        // Typed name is required.
        mvc.perform(post("/api/v1/sign/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("Nomsa Dlamini", "").replace("000000", code))).andExpect(status().isBadRequest());

        JsonNode signed = data(mvc.perform(post("/api/v1/sign/" + id).contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "JUnit phone").header("X-Forwarded-For", "196.25.1.1, 10.0.0.1")
                .content(body.replace("000000", code))).andExpect(status().isOk()).andReturn());
        assertEquals("signed", signed.get("status").asText());
        assertTrue(signed.get("emailConfirmed").asBoolean());
        assertFalse(signed.get("signedDocumentId").isNull());

        byte[] out = mvc.perform(get("/api/v1/sign/" + id + "/pdf")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument d = Loader.loadPDF(out)) {
            assertEquals(4, d.getNumberOfPages()); // 2 original + signature page + audit certificate
            String text = new PDFTextStripper().getText(d);
            assertTrue(text.contains("audit certificate"));
            assertTrue(text.contains("196.25.1.1"));
            assertTrue(text.contains("JUnit phone"));
            assertTrue(text.contains(req.get("documentHash").asText()));
            assertTrue(text.contains("Nomsa Dlamini"));
            assertTrue(text.contains("advanced electronic signature"));
            assertFalse(text.contains("8001015009087")); // ID number is masked
        }
        // The signed copy is on the matter's documents.
        JsonNode docs = data(mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + staff)).andReturn());
        boolean stored = false;
        for (JsonNode n : docs) stored |= signed.get("signedDocumentId").asText().equals(n.get("id").asText());
        assertTrue(stored);
        // Cannot sign twice.
        mvc.perform(post("/api/v1/sign/" + id).contentType(MediaType.APPLICATION_JSON).content(body.replace("000000", code))).andExpect(status().isBadRequest());
    }

    @Test
    void signedPdfBuilderTypesetsLetterTextWhenThereIsNoFile() throws Exception {
        var ev = new SignedPdfBuilder.Evidence("Engagement letter", "Firm", "A Client", "A Client", "", "1234567",
                java.time.Instant.parse("2026-11-02T10:00:00Z"), "1.2.3.4", "UA", "abc", "def", "id-1", false, null);
        byte[] out = SignedPdfBuilder.build(null, "We act for you.\nFees are R1 500 an hour \u2014 payable monthly.", PNG, ev);
        try (PDDocument d = Loader.loadPDF(out)) {
            assertEquals(3, d.getNumberOfPages());
            String text = new PDFTextStripper().getText(d);
            assertTrue(text.contains("We act for you."));
            assertTrue(text.contains("12:00:00 SAST"));
            assertTrue(text.contains("not used"));
        }
        assertNotNull(out);
    }

    private static byte[] twoPagePdf() throws Exception {
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            d.addPage(new PDPage());
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            d.save(o);
            return o.toByteArray();
        }
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"thabo@ndlovulaw.co.za\",\"password\":\"password\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private String portalToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/portal/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmSlug\":\"ndlovu-partners\",\"email\":\"nomsa@example.com\",\"password\":\"portal123\"}")).andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data");
    }
}
