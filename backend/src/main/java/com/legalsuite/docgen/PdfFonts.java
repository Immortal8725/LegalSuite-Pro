package com.legalsuite.docgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Embedded fonts for generated PDFs, with a DejaVu fallback for symbols such as ✓ and ➢. */
final class PdfFonts {
    record Run(String text, PDFont font) {}

    private final PDDocument doc;
    private final Map<String, PDFont> cache = new HashMap<>();
    private final Map<PDFont, Map<Integer, Boolean>> glyphs = new HashMap<>();

    PdfFonts(PDDocument doc) {
        this.doc = doc;
    }

    PDFont get(String kind, boolean bold, boolean italic) {
        String file;
        String style = bold && italic ? "BoldItalic" : bold ? "Bold" : italic ? "Italic" : "Regular";
        switch (kind == null ? "sans" : kind) {
            case "serif" -> file = "Tinos-" + style;
            case "carlito" -> file = "Carlito-" + style;
            default -> file = bold ? "DejaVuSans-Bold" : "DejaVuSans";
        }
        return load(file);
    }

    PDFont fallback() {
        return load("DejaVuSans");
    }

    private PDFont load(String file) {
        return cache.computeIfAbsent(file, f -> {
            try (InputStream in = PdfFonts.class.getResourceAsStream("/doc-templates/fonts/" + f + ".ttf")) {
                if (in == null) throw new IllegalStateException("Missing font " + f);
                return PDType0Font.load(doc, in, true);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private boolean supports(PDFont font, int cp) {
        return glyphs.computeIfAbsent(font, k -> new HashMap<>()).computeIfAbsent(cp, c -> {
            try {
                font.encode(new String(Character.toChars(c)));
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /** Splits text into runs the fonts can draw; characters no font has (emoji) are dropped. */
    List<Run> runs(String text, PDFont primary) {
        List<Run> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        PDFont curFont = primary;
        PDFont fb = fallback();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\t' || cp == '\n' || cp == '\r' || cp == 0xA0) cp = ' ';
            PDFont f = supports(primary, cp) ? primary : supports(fb, cp) ? fb : null;
            if (f == null) continue;
            if (f != curFont && cur.length() > 0) {
                out.add(new Run(cur.toString(), curFont));
                cur.setLength(0);
            }
            curFont = f;
            cur.appendCodePoint(cp);
        }
        if (cur.length() > 0) out.add(new Run(cur.toString(), curFont));
        return out;
    }

    float width(String text, PDFont primary, float size) {
        float w = 0;
        for (Run r : runs(text, primary)) {
            try {
                w += r.font().getStringWidth(r.text()) / 1000f * size;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return w;
    }

    /** Draws text at a baseline with horizontal scaling (1 = natural width). */
    void show(PDPageContentStream cs, String text, PDFont primary, float size, float x, float y, float hscale) throws IOException {
        List<Run> runs = runs(text, primary);
        if (runs.isEmpty()) return;
        cs.beginText();
        cs.setHorizontalScaling(hscale * 100f);
        cs.newLineAtOffset(x, y);
        for (Run r : runs) {
            cs.setFont(r.font(), size);
            cs.showText(r.text());
        }
        cs.endText();
    }
}
