package com.legalsuite.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Minimal A4 report writer: headings, paragraphs and tables, page numbers in the footer. */
public class SimplePdf implements AutoCloseable {
    static final float MARGIN = 40, W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight();
    final PDDocument doc = new PDDocument();
    final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream cs;
    private float y;
    private final String footer;
    private final List<PDPage> pages = new ArrayList<>();

    public SimplePdf(String footer) throws IOException {
        this.footer = clean(footer);
        newPage();
    }

    public PDDocument document() { return doc; }

    public void newPage() throws IOException {
        if (cs != null) cs.close();
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        pages.add(p);
        cs = new PDPageContentStream(doc, p);
        y = H - MARGIN;
    }

    private void need(float h) throws IOException {
        if (y - h < MARGIN + 20) newPage();
    }

    public void title(String t) throws IOException { text(t, bold, 16, 22); }
    public void heading(String t) throws IOException { need(40); y -= 6; text(t, bold, 12, 17); }

    public void para(String t) throws IOException {
        for (String line : wrap(clean(t), regular, 9.5f, W - 2 * MARGIN)) text(line, regular, 9.5f, 13);
        y -= 4;
    }

    private void text(String t, PDType1Font f, float size, float lead) throws IOException {
        need(lead);
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(MARGIN, y - size);
        cs.showText(clean(t));
        cs.endText();
        y -= lead;
    }

    /** widths are fractions of the usable width; numeric columns (rightAlign) align right. */
    public void table(List<String> head, List<List<String>> rows, float[] widths, boolean[] rightAlign) throws IOException {
        float usable = W - 2 * MARGIN;
        float size = 8f;
        row(head, widths, rightAlign, usable, bold, size, true);
        for (List<String> r : rows) row(r, widths, rightAlign, usable, regular, size, false);
        y -= 6;
    }

    private void row(List<String> cells, float[] widths, boolean[] right, float usable, PDType1Font f, float size, boolean header) throws IOException {
        List<List<String>> wrapped = new ArrayList<>();
        int lines = 1;
        for (int i = 0; i < cells.size(); i++) {
            List<String> w = wrap(clean(cells.get(i)), f, size, widths[i] * usable - 4);
            wrapped.add(w);
            lines = Math.max(lines, w.size());
        }
        float h = lines * (size + 2.5f) + 4;
        need(h);
        if (header) {
            cs.setNonStrokingColor(0.06f, 0.24f, 0.37f);
            cs.addRect(MARGIN, y - h, usable, h);
            cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);
        } else {
            cs.setStrokingColor(0.85f, 0.88f, 0.92f);
            cs.moveTo(MARGIN, y - h);
            cs.lineTo(MARGIN + usable, y - h);
            cs.stroke();
            cs.setNonStrokingColor(0.1f, 0.12f, 0.16f);
        }
        float x = MARGIN;
        for (int i = 0; i < cells.size(); i++) {
            float colW = widths[i] * usable;
            float ly = y - size - 2;
            for (String line : wrapped.get(i)) {
                float tw = f.getStringWidth(line) / 1000 * size;
                float tx = right != null && right[i] ? x + colW - tw - 2 : x + 2;
                cs.beginText();
                cs.setFont(f, size);
                cs.newLineAtOffset(tx, ly);
                cs.showText(line);
                cs.endText();
                ly -= size + 2.5f;
            }
            x += colW;
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
        y -= h;
    }

    static List<String> wrap(String t, PDType1Font f, float size, float width) throws IOException {
        List<String> out = new ArrayList<>();
        for (String para : t.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.isEmpty() ? word : line + " " + word;
                if (f.getStringWidth(trial) / 1000 * size <= width || line.isEmpty()) {
                    line.setLength(0);
                    line.append(trial);
                    while (f.getStringWidth(line.toString()) / 1000 * size > width && line.length() > 1) {
                        // Hard-break very long tokens.
                        int cut = line.length() - 1;
                        while (cut > 1 && f.getStringWidth(line.substring(0, cut)) / 1000 * size > width) cut--;
                        out.add(line.substring(0, cut));
                        line.delete(0, cut);
                    }
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

    /** Standard 14 fonts only cover WinAnsi; replace anything else so a name never breaks the PDF. */
    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\t' || c == '\r') b.append(' ');
            else if (c == '\u2013' || c == '\u2014') b.append('-');
            else if (c == '\n' || (c >= 32 && c < 127) || (c >= 160 && c <= 255)) b.append(c);
            else b.append('?');
        }
        return b.toString();
    }

    public byte[] finish() throws IOException {
        cs.close();
        cs = null;
        int n = pages.size();
        for (int i = 0; i < n; i++) {
            try (PDPageContentStream f = new PDPageContentStream(doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true)) {
                f.beginText();
                f.setFont(regular, 7.5f);
                f.newLineAtOffset(MARGIN, 22);
                f.showText(footer + "   Page " + (i + 1) + " of " + n);
                f.endText();
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (cs != null) cs.close();
        doc.close();
    }
}
