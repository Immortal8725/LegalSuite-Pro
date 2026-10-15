package com.legalsuite.docgen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** PDF of a Word template: firm letterhead on page one, the footer line and page numbers on every page. */
public final class FlowPdfRenderer {
    private static final float M = 56;

    private FlowPdfRenderer() {}

    public static byte[] render(List<DocxTemplateEngine.Para> paras, Map<String, String> values, Branding brand, boolean letterhead) {
        try (PDDocument doc = new PDDocument()) {
            PdfFonts fonts = new PdfFonts(doc);
            PDRectangle a4 = PDRectangle.A4;
            float w = a4.getWidth();
            float h = a4.getHeight();
            float[] accent = brand.rgb();
            List<PDPage> pages = new ArrayList<>();
            PDPage page = new PDPage(a4);
            doc.addPage(page);
            pages.add(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = h - M;
            if (letterhead) y = drawLetterhead(doc, cs, fonts, brand, values, w, y, accent);
            for (DocxTemplateEngine.Para p : paras) {
                if (p.logo()) continue;
                PDFont font = fonts.get(p.font(), p.bold(), p.italic());
                float size = (float) Math.max(7, Math.min(p.size(), 20));
                float lead = size * 1.3f;
                float left = M + Math.min(p.indent(), 120);
                float maxW = w - M - left;
                List<String> lines = wrap(fonts, p.text(), font, size, maxW);
                if (lines.isEmpty()) lines = List.of("");
                for (String line : lines) {
                    if (y - lead < M + 24) {
                        cs.close();
                        page = new PDPage(a4);
                        doc.addPage(page);
                        pages.add(page);
                        cs = new PDPageContentStream(doc, page);
                        y = h - M;
                    }
                    y -= lead;
                    float lw = fonts.width(line, font, size);
                    float x = switch (p.align()) {
                        case "center" -> (w - lw) / 2;
                        case "right" -> w - M - lw;
                        default -> left;
                    };
                    if (!line.isBlank()) fonts.show(cs, line, font, size, x, y, 1f);
                }
                y -= size * 0.45f;
            }
            cs.close();
            int total = pages.size();
            PDFont small = fonts.get("sans", false, false);
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream fc = new PDPageContentStream(doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    fc.setStrokingColor(accent[0], accent[1], accent[2]);
                    fc.setLineWidth(0.6f);
                    fc.moveTo(M, M);
                    fc.lineTo(w - M, M);
                    fc.stroke();
                    fc.setNonStrokingColor(0.25f, 0.25f, 0.25f);
                    String foot = brand.footer();
                    if (!foot.isBlank()) fonts.show(fc, foot, small, 8, M, M - 12, 1f);
                    String pg = "Page " + (i + 1) + " of " + total;
                    fonts.show(fc, pg, small, 8, w - M - fonts.width(pg, small, 8), M - 12, 1f);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float drawLetterhead(PDDocument doc, PDPageContentStream cs, PdfFonts fonts, Branding brand,
                                        Map<String, String> values, float w, float top, float[] accent) throws IOException {
        float bottom = top;
        if (brand.hasLogo()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, brand.logo(), "logo");
            float scale = Math.min(54f / logo.getHeight(), 180f / logo.getWidth());
            float lw = logo.getWidth() * scale;
            float lh = logo.getHeight() * scale;
            cs.drawImage(logo, M, top - lh, lw, lh);
            bottom = top - lh;
        }
        PDFont bold = fonts.get("sans", true, false);
        PDFont reg = fonts.get("sans", false, false);
        float ty = top - 12;
        cs.setNonStrokingColor(accent[0], accent[1], accent[2]);
        String name = brand.firmName();
        fonts.show(cs, name, bold, 13, w - M - fonts.width(name, bold, 13), ty, 1f);
        cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
        for (String line : new String[] {values.getOrDefault("firm_address", ""),
                values.getOrDefault("firm_phone", "").isBlank() ? "" : "Tel: " + values.get("firm_phone"),
                values.getOrDefault("firm_email", "").isBlank() ? "" : "Email: " + values.get("firm_email")}) {
            if (line.isBlank()) continue;
            ty -= 11;
            fonts.show(cs, line, reg, 8.5f, w - M - fonts.width(line, reg, 8.5f), ty, 1f);
        }
        bottom = Math.min(bottom, ty - 6);
        cs.setStrokingColor(accent[0], accent[1], accent[2]);
        cs.setLineWidth(1.2f);
        cs.moveTo(M, bottom - 4);
        cs.lineTo(w - M, bottom - 4);
        cs.stroke();
        cs.setNonStrokingColor(0f, 0f, 0f);
        return bottom - 14;
    }

    static List<String> wrap(PdfFonts fonts, String text, PDFont font, float size, float maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fonts.width(candidate, font, size) <= maxW || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }
}
