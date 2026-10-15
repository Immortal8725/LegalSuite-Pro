package com.legalsuite.docgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** Every system template renders to PDF and DOCX in each firm's own branding, with the wording intact. */
class SystemTemplateRenderTest {
    private final TemplateLibrary library = new TemplateLibrary();

    @Test
    void libraryHoldsTheSuppliedTemplates() {
        List<String> keys = library.all().stream().map(TemplateLibrary.SystemTemplate::key).toList();
        for (String k : List.of("raf-special-power-of-attorney", "new-practice-announcement", "master-deceased-estate-report",
                "termination-of-mandate-deceased-estate", "general-power-of-attorney-estate", "client-mandate-fee-agreement",
                "privacy-policy", "client-information-individual", "client-information-entity")) {
            assertTrue(keys.contains(k), k + " missing from " + keys);
        }
        assertEquals(9, keys.size());
    }

    @Test
    void everyTemplateRendersInBothFirmsBranding() throws Exception {
        Branding a = new Branding(firmAValues(), DocGenFixtures.logo("KM", new Color(0x1f, 0x2a, 0x44), true), "#1f2a44");
        Branding b = new Branding(DocGenFixtures.firmB(), DocGenFixtures.logo("VD", new Color(0x1b, 0x6b, 0x3a), false), "#1b6b3a");
        String samples = System.getProperty("owlaw.samples", System.getenv("OWLAW_SAMPLES"));
        for (TemplateLibrary.SystemTemplate t : library.all()) {
            for (Branding brand : List.of(a, b)) {
                DocumentGenerationService.Source src = new DocumentGenerationService.Source(t.id(), t.name(), t.category(),
                        t.description(), true, t.layout(), t.docx(), t.fields());
                byte[] pdf = render(src, brand, true);
                if (samples != null) {
                    Path dir = Path.of(samples);
                    Files.createDirectories(dir);
                    String firm = brand == a ? "firm-a-mokoena" : "firm-b-vdm-dlamini";
                    Files.write(dir.resolve(firm + "--" + t.key() + ".pdf"), pdf);
                    Files.write(dir.resolve(firm + "--" + t.key() + ".docx"), render(src, brand, false));
                }
                String text;
                try (PDDocument d = Loader.loadPDF(pdf)) {
                    if (t.layout() != null) assertEquals(t.layout().get("pages").size(), d.getNumberOfPages(), t.key());
                    text = new PDFTextStripper().getText(d);
                }
                String flat = text.replaceAll("\\s+", " ");
                assertFalse(text.contains("{{"), t.key() + " left a placeholder");
                assertTrue(flat.contains(brand.firmName()) || flat.contains(brand.firmName().replace("&", "& ")), t.key() + " lacks the firm name");
                assertTrue(flat.contains(brand.footer()), t.key() + " lacks the footer");
                byte[] docx = render(src, brand, false);
                try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx));
                     XWPFWordExtractor x = new XWPFWordExtractor(d)) {
                    String dt = x.getText();
                    assertFalse(dt.contains("{{"), t.key() + " DOCX left a placeholder");
                    if (t.fields().contains("firm_name")) assertTrue(dt.contains(brand.firmName()), t.key() + " DOCX lacks the firm name");
                    assertTrue(dt.contains(brand.footer()) || t.layout() != null, t.key() + " DOCX lacks the footer");
                    int pictures = d.getAllPictures().size()
                            + d.getHeaderList().stream().mapToInt(hd -> hd.getAllPictures().size()).sum();
                    assertTrue(pictures > 0, t.key() + " DOCX lacks the logo");
                }
            }
        }
    }

    @Test
    void wordingIsKeptAndBlanksStayWhenUnfilled() throws Exception {
        TemplateLibrary.SystemTemplate raf = library.find("sys-raf-special-power-of-attorney").orElseThrow();
        Branding b = new Branding(DocGenFixtures.firmB(), null, "#1b6b3a");
        byte[] pdf = LayoutPdfRenderer.render(raf.layout(), b.values(), b);
        String text;
        try (PDDocument d = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(d).replaceAll("\\s+", " ");
        }
        assertTrue(text.contains("do hereby nominate, constitute and appoint Nomsa Dlamini of Van der Merwe & Dlamini Attorneys"));
        assertTrue(text.contains("IN TERMS OF THE CONTINGENCY FEES ACT, 1997 (ACT 66 OF 1997)"));
        assertTrue(text.contains("(Name & Surname) ______"), "unfilled client name keeps its blank");
        assertTrue(text.contains("62 0099 1187"), "trust account number merged");

        Map<String, String> filled = new HashMap<>(b.values());
        filled.put("client_name", "Lerato Molefe");
        try (PDDocument d = Loader.loadPDF(LayoutPdfRenderer.render(raf.layout(), filled, b))) {
            assertTrue(new PDFTextStripper().getText(d).contains("Lerato Molefe"));
        }
    }

    @Test
    void paragraphsReflowInOneFontWithoutSqueezing() throws Exception {
        TemplateLibrary.SystemTemplate raf = library.find("sys-raf-special-power-of-attorney").orElseThrow();
        Map<String, String> v = DocGenFixtures.firmA();
        DocGenFixtures.clientA(v);
        Branding a = new Branding(v, null, "#1f2a44");
        byte[] pdf = LayoutPdfRenderer.render(raf.layout(), v, a);
        java.util.Set<Float> sizes = new java.util.TreeSet<>();
        java.util.Set<Float> scales = new java.util.TreeSet<>();
        String text;
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFTextStripper st = new PDFTextStripper() {
                @Override
                protected void writeString(String s, List<org.apache.pdfbox.text.TextPosition> tps) throws java.io.IOException {
                    for (org.apache.pdfbox.text.TextPosition tp : tps) {
                        if (tp.getY() < 230 || tp.getY() > 800) continue;
                        sizes.add(tp.getFontSizeInPt());
                        scales.add(Math.round(tp.getTextMatrix().getScalingFactorX() / tp.getFontSizeInPt() * 100) / 100f);
                    }
                    super.writeString(s, tps);
                }
            };
            st.setStartPage(1);
            st.setEndPage(1);
            text = st.getText(d);
        }
        // the filled names flow on in the same paragraph; no line starts with the comma that followed a field
        String flat = text.replaceAll("\\s+", " ");
        assertTrue(flat.contains("do hereby nominate, constitute and appoint Kagiso Mokoena of Mokoena Inc. Attorneys, or any associate"), flat);
        assertFalse(text.contains("\n,"), "a line starts with a comma");
        assertFalse(flat.contains("do  hereby"));
        assertTrue(flat.contains("occurred on 3 March 2026."));
        assertEquals(java.util.Set.of(11f), sizes, "one body size on page 1");
        assertEquals(java.util.Set.of(1f), scales, "no condensed text");
    }

    @Test
    void longValuesWrapInsteadOfOverflowing() throws Exception {
        TemplateLibrary.SystemTemplate raf = library.find("sys-raf-special-power-of-attorney").orElseThrow();
        Map<String, String> v = new HashMap<>(DocGenFixtures.firmA());
        v.put("firm_name", "Mokoena, Mahlangu, Venter, Pretorius and Associates Incorporated Attorneys, Notaries and Conveyancers");
        Branding a = new Branding(v, null, "#1f2a44");
        try (PDDocument d = Loader.loadPDF(LayoutPdfRenderer.render(raf.layout(), v, a))) {
            assertEquals(raf.layout().get("pages").size(), d.getNumberOfPages());
            String flat = new PDFTextStripper().getText(d).replaceAll("\\s+", " ");
            assertTrue(flat.contains("Notaries and Conveyancers, or any associate"), flat.substring(0, 900));
        }
    }

    @Test
    void docxTemplatesPrintBracketPromptsUntilFilled() throws Exception {
        TemplateLibrary.SystemTemplate t = library.find("sys-termination-of-mandate-deceased-estate").orElseThrow();
        assertTrue(t.fields().contains("deceased_name"));
        assertTrue(t.fields().contains("estate_number"));
        Map<String, String> v = new HashMap<>(DocGenFixtures.firmA());
        v.put("deceased_name", "Johannes Tumelo Molefe");
        byte[] merged = DocxTemplateEngine.merge(t.docx(), v, new Branding(v, null, null));
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(merged)); XWPFWordExtractor x = new XWPFWordExtractor(d)) {
            String text = x.getText();
            assertTrue(text.contains("IN THE ESTATE OF THE LATE Johannes Tumelo Molefe"));
            assertTrue(text.contains("Estate Number: [If available]"));
            assertTrue(text.contains("We reserve all our rights in respect of any fees, disbursements, documents or other matters arising from the terminated mandate."));
        }
    }

    @Test
    void tokenMergeHandlesDefaultsAndSystemFields() {
        Map<String, String> v = Map.of("firm_name", "ABC Attorneys", "client_name", "");
        assertEquals("ABC Attorneys ____", TokenMerge.merge("{{firm_name}} {{client_name:____}}", v));
        assertEquals("[X]", TokenMerge.merge("{{x:[X]}}", v));
        assertEquals("", TokenMerge.merge("{{bank_name}}", v));
        assertEquals("{{firm_logo}}", TokenMerge.merge("{{firm_logo}}", v));
        assertEquals("{{firm_name}} ____", TokenMerge.original("{{firm_name}} {{client_name:____}}"));
        assertEquals("SIGNED AT Rustenburg ON", TokenMerge.merge("SIGNED AT{{signed_at:____}} ON", Map.of("signed_at", "Rustenburg")));
        assertEquals("Thabo Ndlovu, Director", BrandingService.defaultFooter("Thabo Ndlovu", "Director"));
        assertEquals("Thabo Ndlovu", BrandingService.defaultFooter("Thabo Ndlovu", " "));
    }

    private static Map<String, String> firmAValues() {
        Map<String, String> v = DocGenFixtures.firmA();
        DocGenFixtures.clientA(v);
        return v;
    }

    private static byte[] render(DocumentGenerationService.Source src, Branding brand, boolean pdf) {
        if (src.layout() != null) {
            return pdf ? LayoutPdfRenderer.render(src.layout(), brand.values(), brand)
                    : LayoutDocxRenderer.render(src.layout(), brand.values(), brand);
        }
        byte[] merged = DocxTemplateEngine.merge(src.docx(), brand.values(), brand);
        return pdf ? FlowPdfRenderer.render(DocxTemplateEngine.paragraphs(merged), brand.values(), brand, true) : merged;
    }
}
