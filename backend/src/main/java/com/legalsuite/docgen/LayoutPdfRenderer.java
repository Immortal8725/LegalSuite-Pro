package com.legalsuite.docgen;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Renders a positioned (PDF-derived) template: every span, line, box and image goes back where it was
 * in the firm's original, with fields merged in. Merged text that grows pushes the rest of its line
 * along and is condensed slightly if it would run past the margin.
 */
public final class LayoutPdfRenderer {
    private LayoutPdfRenderer() {}

    public static byte[] render(JsonNode tpl, Map<String, String> values, Branding brand) {
        try (PDDocument doc = new PDDocument()) {
            PdfFonts fonts = new PdfFonts(doc);
            String all = tpl.toString();
            boolean ownFooter = all.contains("{{footer_text");
            boolean ownLogo = all.contains("{{firm_logo");
            boolean firstPage = true;
            PDImageXObject logo = brand.hasLogo() ? PDImageXObject.createFromByteArray(doc, brand.logo(), "logo") : null;
            for (JsonNode p : tpl.get("pages")) {
                float w = (float) p.get("w").asDouble();
                float h = (float) p.get("h").asDouble();
                PDPage page = new PDPage(new PDRectangle(w, h));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawShapes(cs, p, h);
                    if (!ownLogo && firstPage) {
                        // Annexures split from a longer document had the logo on the parent's first page only.
                        drawLogo(cs, fonts, brand, logo, 40, 12 + 36, 36, h, 36, 160);
                    }
                    firstPage = false;
                    for (JsonNode img : p.path("images")) {
                        byte[] png = Base64.getDecoder().decode(img.get("png").asText());
                        PDImageXObject x = PDImageXObject.createFromByteArray(doc, png, "img");
                        float iw = (float) img.get("w").asDouble();
                        float ih = (float) img.get("h").asDouble();
                        cs.drawImage(x, (float) img.get("x").asDouble(), h - (float) img.get("y").asDouble() - ih, iw, ih);
                    }
                    for (JsonNode line : p.get("lines")) {
                        drawLine(cs, fonts, line, values, brand, logo, w, h);
                    }
                    if (!ownFooter && !brand.footer().isBlank()) {
                        cs.setNonStrokingColor(0.25f, 0.25f, 0.25f);
                        PDFont small = fonts.get("sans", false, false);
                        fonts.show(cs, brand.footer(), small, 8, (w - fonts.width(brand.footer(), small, 8)) / 2, 18, 1f);
                        cs.setNonStrokingColor(0f, 0f, 0f);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void drawShapes(PDPageContentStream cs, JsonNode p, float h) throws IOException {
        for (JsonNode d : p.path("drawings")) {
            String stroke = d.path("stroke").isTextual() ? d.get("stroke").asText() : null;
            String fill = d.path("fill").isTextual() ? d.get("fill").asText() : null;
            if (stroke == null && fill == null) continue;
            cs.setLineWidth((float) d.path("lw").asDouble(0.5));
            float cx = Float.NaN;
            float cy = Float.NaN;
            for (JsonNode it : d.get("items")) {
                String t = it.get(0).asText();
                switch (t) {
                    case "l" -> {
                        float x1 = f(it, 1), y1 = h - f(it, 2), x2 = f(it, 3), y2 = h - f(it, 4);
                        if (Float.isNaN(cx) || Math.abs(cx - x1) > 0.01 || Math.abs(cy - y1) > 0.01) cs.moveTo(x1, y1);
                        cs.lineTo(x2, y2);
                        cx = x2;
                        cy = y2;
                    }
                    case "c" -> {
                        float x1 = f(it, 1), y1 = h - f(it, 2);
                        if (Float.isNaN(cx) || Math.abs(cx - x1) > 0.01 || Math.abs(cy - y1) > 0.01) cs.moveTo(x1, y1);
                        cs.curveTo(f(it, 3), h - f(it, 4), f(it, 5), h - f(it, 6), f(it, 7), h - f(it, 8));
                        cx = f(it, 7);
                        cy = h - f(it, 8);
                    }
                    case "re" -> {
                        cs.addRect(f(it, 1), h - f(it, 2) - f(it, 4), f(it, 3), f(it, 4));
                        cx = Float.NaN;
                    }
                    case "qu" -> {
                        cs.moveTo(f(it, 1), h - f(it, 2));
                        cs.lineTo(f(it, 3), h - f(it, 4));
                        cs.lineTo(f(it, 5), h - f(it, 6));
                        cs.lineTo(f(it, 7), h - f(it, 8));
                        cs.closePath();
                        cx = Float.NaN;
                    }
                    default -> { }
                }
            }
            if (d.path("close").asBoolean()) cs.closePath();
            if (fill != null) {
                float[] c = rgb(fill);
                cs.setNonStrokingColor(c[0], c[1], c[2]);
            }
            if (stroke != null) {
                float[] c = rgb(stroke);
                cs.setStrokingColor(c[0], c[1], c[2]);
            }
            boolean eo = d.path("eo").asBoolean();
            if (fill != null && stroke != null) {
                if (eo) cs.fillAndStrokeEvenOdd();
                else cs.fillAndStroke();
            } else if (fill != null) {
                if (eo) cs.fillEvenOdd();
                else cs.fill();
            } else {
                cs.stroke();
            }
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
        cs.setStrokingColor(0f, 0f, 0f);
    }

    private static float f(JsonNode it, int i) {
        return (float) it.get(i).asDouble();
    }

    private static void drawLine(PDPageContentStream cs, PdfFonts fonts, JsonNode line, Map<String, String> values,
                                 Branding brand, PDImageXObject logo, float pageW, float h) throws IOException {
        float prevOrigEnd = -1;
        float prevDrawEnd = -1;
        float maxRight = pageW - 28;
        int visible = 0;
        for (JsonNode s : line.get("spans")) if (!s.get("text").asText().isBlank()) visible++;
        for (JsonNode s : line.get("spans")) {
            float mid = (float) (s.get("x").asDouble() + s.get("w").asDouble() / 2);
            boolean centred = visible == 1 && Math.abs(mid - pageW / 2) < 12 && s.get("x").asDouble() > 90;
            String raw = s.get("text").asText();
            float x = (float) s.get("x").asDouble();
            float y = (float) s.get("y").asDouble();
            float ow = (float) s.get("w").asDouble();
            float size = (float) s.get("size").asDouble();
            PDFont font = fonts.get(s.path("font").asText("sans"), s.path("bold").asBoolean(), s.path("italic").asBoolean());
            float drawX = x;
            if (prevDrawEnd >= 0) {
                float gap = x - prevOrigEnd;
                drawX = gap < 20 ? Math.max(x, prevDrawEnd + Math.max(gap, 0)) : Math.max(x, prevDrawEnd + 4);
            }
            if (TokenMerge.hasLogo(raw)) {
                if (centred && TokenMerge.stripLogo(raw).isBlank()) {
                    float lw = logo != null ? logoSize(logo, y, size, 54, 190)[0]
                            : fonts.width(brand.firmName(), fonts.get("sans", true, false), 16);
                    drawX = (pageW - lw) / 2;
                }
                float used = drawLogo(cs, fonts, brand, logo, drawX, y, size, h, 54, 190);
                raw = TokenMerge.stripLogo(raw);
                drawX += used;
                if (raw.isBlank()) {
                    prevOrigEnd = x + ow;
                    prevDrawEnd = drawX;
                    continue;
                }
            }
            String original = TokenMerge.original(raw);
            String merged = TokenMerge.merge(raw, values);
            float natural = fonts.width(original, font, size);
            float ratio = natural > 0.5 && ow > 0.5 ? clamp(ow / natural, 0.8f, 1.2f) : 1f;
            float nw = fonts.width(merged, font, size) * ratio;
            float hs = ratio;
            if (drawX + nw > maxRight && nw > 0) {
                float avail = Math.max(maxRight - drawX, nw * 0.55f);
                hs = ratio * avail / nw;
                nw = avail;
            }
            if (centred && nw < maxRight - 28) {
                drawX = (pageW - nw) / 2;
            }
            if (!merged.isBlank()) {
                float[] c = rgb(s.path("color").asText("#000000"));
                cs.setNonStrokingColor(c[0], c[1], c[2]);
                fonts.show(cs, merged, font, size, drawX, h - y, hs);
            }
            prevOrigEnd = x + ow;
            prevDrawEnd = drawX + nw;
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

    /** Draws the firm logo (or a wordmark if none is uploaded) with its top where the field text sat. */
    static float drawLogo(PDPageContentStream cs, PdfFonts fonts, Branding brand, PDImageXObject logo,
                          float x, float baseline, float size, float h, float maxH, float maxW) throws IOException {
        float top = baseline - size;
        if (logo != null) {
            float[] sz = logoSize(logo, baseline, size, maxH, maxW);
            float lw = sz[0];
            float lh = sz[1];
            cs.drawImage(logo, x, h - (baseline + 3), lw, lh);
            return lw + 8;
        }
        float[] c = brand.rgb();
        cs.setNonStrokingColor(c[0], c[1], c[2]);
        PDFont bold = fonts.get("sans", true, false);
        String name = brand.firmName().isBlank() ? "" : brand.firmName();
        fonts.show(cs, name, bold, 16, x, h - top - 16, 1f);
        cs.setNonStrokingColor(0f, 0f, 0f);
        return fonts.width(name, bold, 16) + 8;
    }

    static float[] logoSize(PDImageXObject logo, float baseline, float size, float maxH, float maxW) {
        float room = Math.max(size * 1.6f, Math.min(maxH, baseline + 3 - 10));
        float scale = Math.min(room / logo.getHeight(), maxW / logo.getWidth());
        return new float[] {logo.getWidth() * scale, logo.getHeight() * scale};
    }

    static float[] rgb(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return new float[] {0, 0, 0};
        return new float[] {
            Integer.parseInt(hex.substring(1, 3), 16) / 255f,
            Integer.parseInt(hex.substring(3, 5), 16) / 255f,
            Integer.parseInt(hex.substring(5, 7), 16) / 255f
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
