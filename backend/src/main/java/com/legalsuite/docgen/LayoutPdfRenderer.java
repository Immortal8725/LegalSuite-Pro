package com.legalsuite.docgen;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

/**
 * Renders a PDF-derived template. The page keeps the original's margins, headings, boxes and images,
 * but body text is reflowed as real paragraphs: wrapped lines are joined, fields are merged inline in
 * the paragraph's own font and size, and the text is wrapped (and justified where the original was) at
 * the original margins. When a paragraph needs more or fewer lines everything below it moves with it.
 */
public final class LayoutPdfRenderer {
    private LayoutPdfRenderer() {}

    private static final Pattern LABEL = Pattern.compile(
            "^\\s*(\\d+(\\.\\d+)*\\.?|\\(?[a-zA-Z]{1,2}\\)|\\(?[ivxIVX]{1,5}[.)]|[a-zA-Z][.)]|\\(?[ivxIVX]{1,5}\\)|\\(\\d+\\)|[•➢\\-–\\u2022\\u25aa\\u25cf\\uf0b7\\uf0d8])\\s*$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\d+(\\.\\d+)*\\.?|\\(?[a-zA-Z]\\)|\\(?[ivx]{1,4}\\))\\s+\\S");
    private static final Pattern LEADING_PUNCT = Pattern.compile("^[,.;:)\\]]");

    // ---------- model ----------
    private static final class Seg {
        final List<JsonNode> spans = new ArrayList<>();
        float x;
        float end;
        String raw() {
            StringBuilder b = new StringBuilder();
            for (JsonNode s : spans) b.append(s.get("text").asText());
            return b.toString();
        }
    }

    private static final class Row {
        float y;
        float size;
        Seg label;
        final List<Seg> segs = new ArrayList<>();
        boolean logo;
        boolean footer;
        float x() { return segs.isEmpty() ? (label == null ? 0 : label.x) : segs.get(0).x; }
        float end() { return segs.isEmpty() ? (label == null ? 0 : label.end) : segs.get(segs.size() - 1).end; }
    }

    private record Piece(String text, PDFont font, float size, float[] colour) {}

    private static final class Word {
        final List<Piece> pieces = new ArrayList<>();
        boolean spaceBefore;
        float width;
    }

    private record Style(PDFont font, float size, float[] colour) {}

    private static final class Ctx {
        PdfFonts fonts;
        Map<String, String> values;
        Branding brand;
        PDImageXObject logo;
        float bodySize;
        String bodyKind;
        float pitch;
        float right;
    }

    // ---------- entry ----------
    public static byte[] render(JsonNode tpl, Map<String, String> values, Branding brand) {
        try (PDDocument doc = new PDDocument()) {
            Ctx c = new Ctx();
            c.fonts = new PdfFonts(doc);
            c.values = values;
            c.brand = brand;
            c.logo = brand.hasLogo() ? PDImageXObject.createFromByteArray(doc, brand.logo(), "logo") : null;
            measureTemplate(tpl, c);
            String all = tpl.toString();
            boolean ownFooter = all.contains("{{footer_text");
            boolean ownLogo = all.contains("{{firm_logo");
            boolean firstPage = true;
            int pageNo = 0;
            int pageTotal = tpl.get("pages").size();
            for (JsonNode p : tpl.get("pages")) {
                PAGE_NO.set(new int[] {++pageNo, pageTotal});
                float w = (float) p.get("w").asDouble();
                float h = (float) p.get("h").asDouble();
                PDPage page = new PDPage(new PDRectangle(w, h));
                doc.addPage(page);
                PAGE_W.set(w);
                RULES.set(verticalRules(p));
                List<Row> rows = rows(p, h, c);
                float right = pageRight(rows, w, c);
                float left = w;
                for (Row r : rows) if (!r.footer) left = Math.min(left, r.label != null ? r.label.x : r.x());
                LEFT.set(Math.min(left, w - right));
                List<Op> ops = new ArrayList<>();
                TreeMap<Float, Float> shifts = layout(rows, w, h, right, c, ops);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawShapes(cs, p, h, shifts);
                    if (!ownLogo && firstPage) {
                        drawLogo(cs, c.fonts, brand, c.logo, 40, 12 + 36, 36, h, 36, 160);
                    }
                    firstPage = false;
                    for (JsonNode img : p.path("images")) {
                        byte[] png = Base64.getDecoder().decode(img.get("png").asText());
                        PDImageXObject x = PDImageXObject.createFromByteArray(doc, png, "img");
                        float iw = (float) img.get("w").asDouble();
                        float ih = (float) img.get("h").asDouble();
                        float iy = (float) img.get("y").asDouble();
                        cs.drawImage(x, (float) img.get("x").asDouble(), h - (iy + shift(shifts, iy)) - ih, iw, ih);
                    }
                    for (Op op : ops) op.draw(cs);
                    if (!ownFooter && !brand.footer().isBlank()) {
                        cs.setNonStrokingColor(0.25f, 0.25f, 0.25f);
                        PDFont small = c.fonts.get(c.bodyKind, false, false);
                        c.fonts.show(cs, brand.footer(), small, 8, (w - c.fonts.width(brand.footer(), small, 8)) / 2, 18, 1f);
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

    private interface Op {
        void draw(PDPageContentStream cs) throws IOException;
    }

    // ---------- template-wide measurements ----------
    private static void measureTemplate(JsonNode tpl, Ctx c) {
        Map<Float, Integer> sizes = new HashMap<>();
        Map<String, Integer> kinds = new HashMap<>();
        Map<Float, Integer> gaps = new HashMap<>();
        for (JsonNode p : tpl.get("pages")) {
            float prevY = -1;
            float prevX = -1;
            for (JsonNode line : p.get("lines")) {
                for (JsonNode s : line.get("spans")) {
                    int n = s.get("text").asText().trim().length();
                    if (n == 0) continue;
                    float sz = Math.round(s.get("size").asDouble() * 2) / 2f;
                    sizes.merge(sz, n, Integer::sum);
                    kinds.merge(s.path("font").asText("sans"), n, Integer::sum);
                }
                JsonNode first = line.get("spans").get(0);
                float y = (float) line.get("y").asDouble();
                float x = first == null ? -1 : (float) first.get("x").asDouble();
                if (prevY >= 0 && Math.abs(x - prevX) < 3) gaps.merge((float) Math.round(y - prevY), 1, Integer::sum);
                prevY = y;
                prevX = x;
            }
        }
        c.bodySize = sizes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(11f);
        c.bodyKind = kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("sans");
        float lo = c.bodySize * 1.1f;
        float hi = c.bodySize * 2.6f;
        c.pitch = gaps.entrySet().stream().filter(e -> e.getKey() >= lo && e.getKey() <= hi)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(c.bodySize * 1.45f);
    }

    /** Text close to the paragraph's own size is set in that size and font, so a paragraph reads as one. */
    private static Style style(JsonNode s, float paraSize, String paraKind, Ctx c) {
        float size = (float) s.get("size").asDouble();
        String kind = s.path("font").asText("sans");
        if (Math.abs(size - paraSize) <= 1.6f) {
            size = paraSize;
            kind = paraKind;
        }
        PDFont font = c.fonts.get(kind, s.path("bold").asBoolean(), s.path("italic").asBoolean());
        return new Style(font, size, rgb(s.path("color").asText("#000000")));
    }

    /** The size and font most of the text in these cells is set in (bold names in a slightly larger size do not count). */
    private static Object[] dominant(List<Seg> segs) {
        Map<Float, Integer> sizes = new HashMap<>();
        Map<String, Integer> kinds = new HashMap<>();
        for (Seg seg : segs) {
            for (JsonNode s : seg.spans) {
                String t = TokenMerge.original(s.get("text").asText()).trim();
                if (t.isEmpty()) continue;
                int n = t.replace("_", "").length() + 1;
                if (s.path("bold").asBoolean()) n = Math.max(1, n / 4);
                sizes.merge(Math.round(s.get("size").asDouble() * 2) / 2f, n, Integer::sum);
                kinds.merge(s.path("font").asText("sans"), n, Integer::sum);
            }
        }
        float size = sizes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(11f);
        String kind = kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("sans");
        return new Object[] {size, kind};
    }

    // ---------- rows ----------
    private static List<Row> rows(JsonNode p, float h, Ctx c) {
        List<Row> out = new ArrayList<>();
        for (JsonNode line : p.get("lines")) {
            Row r = new Row();
            r.y = (float) line.get("y").asDouble();
            List<Seg> segs = new ArrayList<>();
            Seg cur = null;
            float maxSize = 0;
            JsonNode labelEnd = null;
            List<JsonNode> spansNb = new ArrayList<>();
            for (JsonNode s : line.get("spans")) if (!s.get("text").asText().isBlank()) spansNb.add(s);
            if (spansNb.size() >= 2 && LABEL.matcher(spansNb.get(0).get("text").asText()).matches()
                    && spansNb.get(1).get("x").asDouble() - (spansNb.get(0).get("x").asDouble() + spansNb.get(0).get("w").asDouble()) > 4) {
                labelEnd = spansNb.get(0);
            }
            for (JsonNode s : line.get("spans")) {
                String t = s.get("text").asText();
                if (t.isEmpty()) continue;
                if (TokenMerge.hasLogo(t)) r.logo = true;
                if (t.contains("{{footer_text")) r.footer = true;
                float x = (float) s.get("x").asDouble();
                float e = x + (float) s.get("w").asDouble();
                if (!t.isBlank()) maxSize = Math.max(maxSize, (float) s.get("size").asDouble());
                if (cur == null || x - cur.end > 14 || (labelEnd != null && cur.spans.contains(labelEnd)) || ruleBetween(cur.end, x, r.y)) {
                    if (cur != null && cur.raw().isBlank()) {
                        cur.spans.clear();
                    } else if (cur != null) {
                        segs.add(cur);
                    }
                    cur = new Seg();
                    cur.x = x;
                    int lead = t.length() - t.stripLeading().length();
                    if (lead > 0 && !t.isBlank()) {
                        PDFont lf = c.fonts.get(s.path("font").asText("sans"), s.path("bold").asBoolean(), false);
                        cur.x = x + c.fonts.width(t.substring(0, lead), lf, (float) s.get("size").asDouble());
                    }
                }
                cur.spans.add(s);
                cur.end = Math.max(cur.end, e);
            }
            if (cur != null && !cur.raw().isBlank()) segs.add(cur);
            if (segs.isEmpty()) continue;
            // a leading spaced-out number or bullet hangs in the margin; the text after it is the paragraph
            if (segs.size() >= 2 && LABEL.matcher(segs.get(0).raw()).matches() && !TokenMerge.TOKEN.matcher(segs.get(0).raw()).find()) {
                r.label = segs.remove(0);
            }
            r.segs.addAll(segs);
            r.size = maxSize == 0 ? c.bodySize : (Float) dominant(r.label == null ? r.segs : List.of(r.label, r.segs.get(0)))[0];
            if (r.y > h - 40) r.footer = true;
            out.add(r);
        }
        // the block at the foot of the page (footer, firm line) stays put
        boolean foot = false;
        for (int k = 0; k < out.size(); k++) {
            Row r = out.get(k);
            if (!foot && r.y > h * 0.85f && (k == 0 || r.y - out.get(k - 1).y > 60)) foot = true;
            if (foot) r.footer = true;
        }
        return out;
    }

    private static float pageRight(List<Row> rows, float w, Ctx c) {
        float right = 0;
        for (Row r : rows) {
            if (r.footer || r.segs.size() != 1) continue;
            right = Math.max(right, r.end());
        }
        float left = w;
        for (Row r : rows) if (!r.footer) left = Math.min(left, r.label != null ? r.label.x : r.x());
        if (left < w / 3) right = Math.max(right, w - left);
        if (right < w * 0.6f) right = w - 45;
        return Math.min(right, w - 20);
    }

    // ---------- layout ----------
    private static TreeMap<Float, Float> layout(List<Row> rows, float w, float h, float right, Ctx c, List<Op> ops) throws IOException {
        TreeMap<Float, Float> shifts = new TreeMap<>();
        shifts.put(-1000f, 0f);
        float dy = 0;
        int i = 0;
        java.util.Set<Row> done = new java.util.HashSet<>();
        while (i < rows.size()) {
            Row r = rows.get(i);
            if (done.contains(r)) {
                i++;
                continue;
            }
            if (r.footer) {
                shifts.put(r.y - r.size - 14, 0f);
                ops.add(positioned(r, 0, w, right, c, new float[1]));
                i++;
                continue;
            }
            if (r.logo) {
                float d = dy;
                ops.add(cs -> drawLogoRow(cs, r, d, w, h, c));
                i++;
                continue;
            }
            if (r.segs.size() != 1) {
                float[] extra = new float[1];
                ops.add(positioned(r, dy, w, right, c, extra));
                dy += extra[0];
                shifts.put(r.y + 0.5f, dy);
                i++;
                continue;
            }
            // gather a paragraph: following rows that are the wrapped continuation of this one
            List<Row> para = new ArrayList<>();
            para.add(r);
            int j = i + 1;
            float cr = cellRight(r.x(), r.y, r.size, right);
            boolean inCell = cr < right - 0.5f || cr > right + 0.5f;
            float pr = inCell ? cr : right;
            List<Row> side = new ArrayList<>();
            while (j < rows.size()) {
                Row nx = rows.get(j);
                // notes set beside the paragraph ("Initial here" boxes) do not interrupt it
                if (!nx.footer && !nx.logo && nx.x() >= pr - 2 && nx.y - para.get(para.size() - 1).y < r.size * 2) {
                    side.add(nx);
                    j++;
                    continue;
                }
                if (!continues(para, nx, pr, c)) break;
                para.add(nx);
                j++;
            }
            // side notes after the paragraph's last line are not part of it
            while (!side.isEmpty() && side.get(side.size() - 1).y > para.get(para.size() - 1).y) {
                side.remove(side.size() - 1);
                j--;
            }
            for (Row sr : side) {
                ops.add(positioned(sr, dy, w, right, c, new float[1]));
                done.add(sr);
            }
            float pitch = para.size() > 1 ? (para.get(para.size() - 1).y - r.y) / (para.size() - 1) : Math.max(c.pitch, r.size * 1.2f);
            float[] lines = new float[1];
            ops.add(paragraph(para, dy, w, pr, inCell, pitch, c, lines));
            dy += (lines[0] - para.size()) * pitch;
            shifts.put(para.get(para.size() - 1).y + 0.5f, dy);
            i = j;
        }
        return shifts;
    }

    private static boolean continues(List<Row> para, Row next, float right, Ctx c) {
        Row first = para.get(0);
        Row last = para.get(para.size() - 1);
        if (next.footer || next.logo || next.label != null || next.segs.size() != 1) return false;
        if (Math.abs(next.size - last.size) > 0.6f) return false;
        float gap = next.y - last.y;
        if (gap <= 0 || gap > Math.max(last.size * 2.7f, c.pitch * 1.15f)) return false;
        if (para.size() > 1 && Math.abs(gap - (last.y - para.get(para.size() - 2).y)) > 4) return false;
        float contX = para.size() > 1 ? para.get(1).x() : -1;
        float nx = next.x();
        boolean xOk = contX >= 0 ? Math.abs(nx - contX) < 3
                : (Math.abs(nx - first.x()) < 3 || (nx < first.x() && first.label == null) || (nx > first.x() && nx - first.x() < 40 && NUMBERED.matcher(first.segs.get(0).raw()).find()));
        if (!xOk) return false;
        if (centred(first, right)) return false;
        // a field on a line of its own, or a block set to the right (letter references, addresses), keeps its lines
        if (next.segs.get(0).raw().strip().matches("\\{\\{[^}]*\\}\\}[.,]?")) return false;
        if (first.x() > PAGE_W.get() * 0.45f) return false;
        String txt = TokenMerge.original(next.segs.get(0).raw()).stripLeading();
        if (LEADING_PUNCT.matcher(txt).find()) return true;
        // a hard line break inside an indented paragraph: same indent and pitch, and the sentence had not ended
        String prevTxt = TokenMerge.original(last.segs.get(0).raw()).strip();
        if (para.size() >= 2 && contX > first.x() + 3 && !prevTxt.matches(".*[.:;!?]$")) return true;
        String firstWord = txt.split("\\s+", 2)[0];
        JsonNode s0 = next.segs.get(0).spans.get(0);
        float fw = c.fonts.width(firstWord, c.fonts.get(s0.path("font").asText("sans"), s0.path("bold").asBoolean(), false), last.size);
        float lineRight = Math.max(right, last.end());
        // the previous line ended because the next word did not fit (or it is a placeholder that was long)
        return last.end() + fw + last.size * 0.3f > lineRight - 2;
    }

    private static boolean centred(Row r, float right) {
        float mid = (r.x() + r.end()) / 2;
        float centre = (40 + right) / 2;
        return r.label == null && r.x() > 90 && (Math.abs(mid - centre) < 14 || Math.abs(mid - PAGE_W.get() / 2) < 14);
    }

    // ---------- words ----------
    private record Chunk(String text, Style style, boolean gapBefore, boolean filledBlank) {}

    private static List<Word> words(List<Seg> segs, Ctx c) {
        Object[] dom = dominant(segs);
        float paraSize = (Float) dom[0];
        String paraKind = (String) dom[1];
        // 1. split every span into text and field chunks, merging the fields
        List<Chunk> chunks = new ArrayList<>();
        float prevEnd = -1;
        for (Seg seg : segs) {
            boolean newRow = true;
            for (JsonNode s : seg.spans) {
                String raw = s.get("text").asText();
                float x = (float) s.get("x").asDouble();
                boolean gap = newRow && !chunks.isEmpty() || prevEnd >= 0 && x - prevEnd > paraSize * 0.2f;
                newRow = false;
                prevEnd = x + (float) s.get("w").asDouble();
                Style st = style(s, paraSize, paraKind, c);
                boolean spanHasText = !TokenMerge.TOKEN.matcher(raw).replaceAll("").isBlank();
                java.util.regex.Matcher m = TokenMerge.TOKEN.matcher(raw);
                int last = 0;
                while (m.find()) {
                    if (m.start() > last) {
                        chunks.add(new Chunk(raw.substring(last, m.start()), st, gap, false));
                        gap = false;
                    }
                    String key = m.group(1).toLowerCase();
                    String def = m.group(2);
                    String v = c.values.get(key);
                    boolean fillIn = def != null && !FieldCatalog.SYSTEM.contains(key);
                    boolean filled = v != null && !v.isBlank();
                    Style fs = st;
                    if (fillIn && filled && !spanHasText && st.font() != c.fonts.get(kindOf(st, c), false, false)) {
                        // a filled-in blank reads in the paragraph's normal weight
                        fs = new Style(c.fonts.get(kindOf(st, c), false, isItalic(s)), st.size(), st.colour());
                    }
                    String text = TokenMerge.merge(m.group(0), c.values);
                    chunks.add(new Chunk(text, fs, gap, fillIn && filled && def.startsWith("_")));
                    gap = false;
                    last = m.end();
                }
                if (last < raw.length()) chunks.add(new Chunk(raw.substring(last), st, gap, false));
            }
        }
        // 2. a filled blank takes the place of the whole line it sat on, including stray underscores either side
        for (int k = 0; k < chunks.size(); k++) {
            if (!chunks.get(k).filledBlank()) continue;
            if (k > 0) {
                Chunk p = chunks.get(k - 1);
                String t = p.text().replaceAll("_+$", "");
                if (!t.isEmpty() && Character.isLetterOrDigit(t.charAt(t.length() - 1))) t = t + " ";
                chunks.set(k - 1, new Chunk(t, p.style(), p.gapBefore(), p.filledBlank()));
            }
            if (k + 1 < chunks.size() && !chunks.get(k + 1).filledBlank()) {
                Chunk n = chunks.get(k + 1);
                String t = n.text().replaceAll("^_+", "");
                if (!t.isEmpty() && Character.isLetterOrDigit(t.charAt(0))) t = " " + t;
                chunks.set(k + 1, new Chunk(t, n.style(), n.gapBefore(), false));
            }
        }
        // 3. words
        List<Word> out = new ArrayList<>();
        Word cur = null;
        boolean pendingSpace = false;
        for (Chunk ch0 : chunks) {
            String text = ch0.text().replace('\u00a0', ' ').replace('\t', ' ').replace('\n', ' ');
            if (ch0.gapBefore()) {
                cur = null;
                pendingSpace = true;
            }
            StringBuilder buf = new StringBuilder();
            for (int k = 0; k < text.length(); k++) {
                char ch = text.charAt(k);
                if (ch == ' ') {
                    if (buf.length() > 0) {
                        cur = append(out, cur, buf.toString(), ch0.style(), pendingSpace);
                        pendingSpace = false;
                        buf.setLength(0);
                    }
                    cur = null;
                    pendingSpace = true;
                } else {
                    buf.append(ch);
                }
            }
            if (buf.length() > 0) {
                cur = append(out, cur, buf.toString(), ch0.style(), pendingSpace);
                pendingSpace = false;
            }
        }
        for (Word wd : out) {
            wd.width = 0;
            for (Piece pc : wd.pieces) wd.width += c.fonts.width(pc.text(), pc.font(), pc.size());
        }
        return out;
    }

    private static String kindOf(Style st, Ctx c) {
        for (String k : List.of("sans", "serif", "carlito")) {
            for (int b = 0; b < 4; b++) {
                if (c.fonts.get(k, (b & 1) == 1, (b & 2) == 2) == st.font()) return k;
            }
        }
        return "sans";
    }

    private static boolean isItalic(JsonNode s) {
        return s.path("italic").asBoolean();
    }

    private static Word append(List<Word> out, Word cur, String text, Style st, boolean space) {
        if (cur == null && !out.isEmpty() && space && LEADING_PUNCT.matcher(text).find() && !text.startsWith("(")) {
            // ", or" that began the next row in the original belongs to the word before it
            cur = out.get(out.size() - 1);
            space = false;
        }
        if (cur == null || space) {
            cur = new Word();
            cur.spaceBefore = space && !out.isEmpty();
            out.add(cur);
        }
        cur.pieces.add(new Piece(text, st.font(), st.size(), st.colour()));
        return cur;
    }

    /** Lays words into lines no wider than the given widths; a word longer than a line is broken. */
    private static List<List<Word>> wrap(List<Word> ws, float firstW, float restW, Ctx c) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        float used = 0;
        for (Word wd : ws) {
            float avail = lines.isEmpty() ? firstW : restW;
            float sp = line.isEmpty() ? 0 : spaceW(wd, c);
            if (!line.isEmpty() && used + sp + wd.width > avail + 0.5f) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
                sp = 0;
                avail = restW;
            }
            if (line.isEmpty() && wd.width > avail) {
                for (Word part : split(wd, avail, c)) {
                    if (!line.isEmpty()) {
                        lines.add(line);
                        line = new ArrayList<>();
                    }
                    line.add(part);
                    used = part.width;
                }
                continue;
            }
            line.add(wd);
            used += sp + wd.width;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private static List<Word> split(Word wd, float avail, Ctx c) {
        List<Word> parts = new ArrayList<>();
        Word cur = new Word();
        for (Piece pc : wd.pieces) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < pc.text().length(); k++) {
                String next = b.toString() + pc.text().charAt(k);
                float wNow = cur.width + c.fonts.width(next, pc.font(), pc.size());
                if (wNow > avail && (b.length() > 0 || !cur.pieces.isEmpty())) {
                    if (b.length() > 0) cur.pieces.add(new Piece(b.toString(), pc.font(), pc.size(), pc.colour()));
                    cur.width = 0;
                    for (Piece q : cur.pieces) cur.width += c.fonts.width(q.text(), q.font(), q.size());
                    parts.add(cur);
                    cur = new Word();
                    b.setLength(0);
                }
                b.append(pc.text().charAt(k));
            }
            if (b.length() > 0) {
                cur.pieces.add(new Piece(b.toString(), pc.font(), pc.size(), pc.colour()));
                cur.width += c.fonts.width(b.toString(), pc.font(), pc.size());
            }
        }
        if (!cur.pieces.isEmpty()) parts.add(cur);
        return parts;
    }

    private static float spaceW(Word wd, Ctx c) {
        Piece p = wd.pieces.get(0);
        return c.fonts.width(" ", p.font(), p.size());
    }

    private static float lineWidth(List<Word> line, Ctx c) {
        float w = 0;
        for (int k = 0; k < line.size(); k++) w += (k == 0 ? 0 : spaceW(line.get(k), c)) + line.get(k).width;
        return w;
    }

    private static void drawLine(PDPageContentStream cs, List<Word> line, float x, float baseline, float extraPerGap, Ctx c) throws IOException {
        float cx = x;
        for (int k = 0; k < line.size(); k++) {
            Word wd = line.get(k);
            if (k > 0) cx += spaceW(wd, c) + extraPerGap;
            for (Piece pc : wd.pieces) {
                cs.setNonStrokingColor(pc.colour()[0], pc.colour()[1], pc.colour()[2]);
                c.fonts.show(cs, pc.text(), pc.font(), pc.size(), cx, baseline, 1f);
                cx += c.fonts.width(pc.text(), pc.font(), pc.size());
            }
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

    // ---------- paragraph ----------
    private static Op paragraph(List<Row> para, float dy, float pageW, float right, boolean inCell, float pitch, Ctx c, float[] linesOut) throws IOException {
        Row first = para.get(0);
        float firstX = first.x();
        float contX = para.size() > 1 ? para.get(1).x() : firstX;
        float paraRight = right;
        for (Row r : para) paraRight = Math.max(paraRight, Math.min(r.end(), inCell ? right + 1.5f : pageW - 20));
        boolean centre = para.size() == 1 && centred(first, right);
        boolean rightAlign = para.size() == 1 && !centre && first.x() > pageW / 2 && Math.abs(first.end() - right) < 6;
        boolean justified = false;
        for (int k = 0; k < para.size() - 1; k++) {
            if (para.get(k).end() >= right - 4) justified = true;
        }
        List<Seg> segs = new ArrayList<>();
        for (Row r : para) segs.add(r.segs.get(0));
        List<Word> ws = words(segs, c);
        float firstW = paraRight - firstX;
        float restW = paraRight - contX;
        if (centre) {
            firstW = restW = Math.max(right - LEFT.get(), first.end() - firstX);
        }
        List<List<Word>> lines = wrap(ws, firstW, restW, c);
        if (lines.isEmpty()) lines.add(new ArrayList<>());
        linesOut[0] = lines.size();
        final float fFirstW = firstW;
        final float fRestW = restW;
        final boolean fJust = justified;
        return cs -> {
            if (first.label != null) drawLabel(cs, first, dy, pageW, c);
            for (int k = 0; k < lines.size(); k++) {
                List<Word> line = lines.get(k);
                if (line.isEmpty()) continue;
                float base = first.y + dy + k * pitch;
                float lw = lineWidth(line, c);
                float avail = k == 0 ? fFirstW : fRestW;
                float x = k == 0 ? firstX : contX;
                float extra = 0;
                if (centre) {
                    x = (pageW - lw) / 2;
                } else if (rightAlign) {
                    x = right - lw;
                } else if (fJust && k < lines.size() - 1 && line.size() > 1) {
                    extra = (avail - lw) / (line.size() - 1);
                    if (extra > first.size * 1.5f || extra < 0) extra = 0;
                }
                drawLine(cs, line, x, CUR_H.get() - base, extra, c);
            }
        };
    }

    private static final ThreadLocal<Float> PAGE_W = ThreadLocal.withInitial(() -> 595f);
    private static final ThreadLocal<int[]> PAGE_NO = ThreadLocal.withInitial(() -> new int[] {1, 1});
    private static final ThreadLocal<Float> LEFT = ThreadLocal.withInitial(() -> 43f);
    private static final ThreadLocal<Float> CUR_H = ThreadLocal.withInitial(() -> 842f);

    private static void drawLabel(PDPageContentStream cs, Row r, float dy, float pageW, Ctx c) throws IOException {
        List<Seg> one = List.of(r.label);
        List<Word> ws = words(one, c);
        float x = r.label.x;
        drawLine(cs, ws, x, CUR_H.get() - (r.y + dy), 0, c);
    }

    /** A row of separate cells (form labels, table columns, signature blocks): cells stay where they were. */
    private static Op positioned(Row r, float dy, float pageW, float right, Ctx c, float[] extraOut) throws IOException {
        List<Seg> cells = new ArrayList<>();
        if (r.label != null) cells.add(r.label);
        cells.addAll(r.segs);
        List<List<List<Word>>> laid = new ArrayList<>();
        List<Float> xs = new ArrayList<>();
        float pitch = Math.max(r.size * 1.3f, Math.min(c.pitch, r.size * 1.6f));
        int maxLines = 1;
        for (int k = 0; k < cells.size(); k++) {
            Seg cell = cells.get(k);
            float limit = k + 1 < cells.size() ? cells.get(k + 1).x - 6 : Math.max(right, Math.min(cell.end, pageW - 20));
            limit = Math.min(Math.max(limit, cell.end), Math.max(cell.end, cellRight(cell.x, r.y, r.size, pageW)));
            List<Word> ws = words(List.of(cell), c);
            renumber(ws);
            float x = cell.x;
            float natural = lineWidth(ws, c);
            float avail = limit - x;
            boolean single = cells.size() == 1;
            if (single && centred(r, right) && natural <= pageW - 80) {
                x = (pageW - natural) / 2;
                avail = natural + 1;
            }
            float longest = 0;
            for (Word wd : ws) longest = Math.max(longest, wd.width);
            avail = Math.max(avail, Math.min(longest, pageW - 12 - x));
            List<List<Word>> lines = wrap(ws, avail, avail, c);
            maxLines = Math.max(maxLines, lines.size());
            laid.add(lines);
            xs.add(x);
        }
        extraOut[0] = (maxLines - 1) * pitch;
        final float fPitch = pitch;
        return cs -> {
            for (int k = 0; k < laid.size(); k++) {
                List<List<Word>> lines = laid.get(k);
                for (int n = 0; n < lines.size(); n++) {
                    drawLine(cs, lines.get(n), xs.get(k), CUR_H.get() - (r.y + dy + n * fPitch), 0, c);
                }
            }
        };
    }

    /** "Page 2 of 20" in a template split from a longer document counts this document's pages. */
    private static void renumber(List<Word> ws) {
        for (int k = 0; k + 3 < ws.size(); k++) {
            if (text(ws.get(k)).equals("Page") && text(ws.get(k + 1)).matches("\\d+") && text(ws.get(k + 2)).equals("of")
                    && text(ws.get(k + 3)).matches("\\d+")) {
                setText(ws.get(k + 1), String.valueOf(PAGE_NO.get()[0]));
                setText(ws.get(k + 3), String.valueOf(PAGE_NO.get()[1]));
            }
        }
    }

    private static String text(Word w) {
        StringBuilder b = new StringBuilder();
        for (Piece p : w.pieces) b.append(p.text());
        return b.toString();
    }

    private static void setText(Word w, String t) {
        Piece p = w.pieces.get(0);
        w.pieces.clear();
        w.pieces.add(new Piece(t, p.font(), p.size(), p.colour()));
    }

    private static void drawLogoRow(PDPageContentStream cs, Row r, float dy, float pageW, float h, Ctx c) throws IOException {
        float y = r.y + dy;
        float x = r.x();
        String rest = "";
        for (Seg s : r.segs) rest += TokenMerge.stripLogo(s.raw());
        boolean centre = rest.isBlank() && Math.abs((r.x() + r.end()) / 2 - pageW / 2) < 40 && r.x() > 90;
        if (centre) {
            float lw = c.logo != null ? logoSize(c.logo, y, r.size, 54, 190)[0]
                    : c.fonts.width(c.brand.firmName(), c.fonts.get("sans", true, false), 16);
            x = (pageW - lw) / 2;
        }
        float used = drawLogo(cs, c.fonts, c.brand, c.logo, x, y, r.size, h, 54, 190);
        if (!rest.isBlank()) {
            Object[] dom = dominant(r.segs);
            Style st = style(r.segs.get(0).spans.get(0), (Float) dom[0], (String) dom[1], c);
            c.fonts.show(cs, TokenMerge.merge(rest.trim(), c.values), st.font(), st.size(), x + used, h - y, 1f);
        }
    }

    // ---------- shapes ----------
    private static float shift(TreeMap<Float, Float> shifts, float y) {
        Map.Entry<Float, Float> e = shifts.floorEntry(y);
        return e == null ? 0 : e.getValue();
    }

    private static void drawShapes(PDPageContentStream cs, JsonNode p, float h, TreeMap<Float, Float> shifts) throws IOException {
        CUR_H.set(h);
        // every point moves with the text above it, so a table border below a grown cell moves down and the
        // column rules either side of it stretch
        java.util.function.Function<Float, Float> Y = y -> h - (y + shift(shifts, y));
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
                        float x1 = f(it, 1), y1 = Y.apply(f(it, 2)), x2 = f(it, 3), y2 = Y.apply(f(it, 4));
                        if (Float.isNaN(cx) || Math.abs(cx - x1) > 0.01 || Math.abs(cy - y1) > 0.01) cs.moveTo(x1, y1);
                        cs.lineTo(x2, y2);
                        cx = x2;
                        cy = y2;
                    }
                    case "c" -> {
                        float x1 = f(it, 1), y1 = Y.apply(f(it, 2));
                        if (Float.isNaN(cx) || Math.abs(cx - x1) > 0.01 || Math.abs(cy - y1) > 0.01) cs.moveTo(x1, y1);
                        cs.curveTo(f(it, 3), Y.apply(f(it, 4)), f(it, 5), Y.apply(f(it, 6)), f(it, 7), Y.apply(f(it, 8)));
                        cx = f(it, 7);
                        cy = Y.apply(f(it, 8));
                    }
                    case "re" -> {
                        float top = Y.apply(f(it, 2));
                        float bottom = Y.apply(f(it, 2) + f(it, 4));
                        cs.addRect(f(it, 1), bottom, f(it, 3), top - bottom);
                        cx = Float.NaN;
                    }
                    case "qu" -> {
                        cs.moveTo(f(it, 1), Y.apply(f(it, 2)));
                        cs.lineTo(f(it, 3), Y.apply(f(it, 4)));
                        cs.lineTo(f(it, 5), Y.apply(f(it, 6)));
                        cs.lineTo(f(it, 7), Y.apply(f(it, 8)));
                        cs.closePath();
                        cx = Float.NaN;
                    }
                    default -> { }
                }
            }
            if (d.path("close").asBoolean()) cs.closePath();
            if (fill != null) {
                float[] col = rgb(fill);
                cs.setNonStrokingColor(col[0], col[1], col[2]);
            }
            if (stroke != null) {
                float[] col = rgb(stroke);
                cs.setStrokingColor(col[0], col[1], col[2]);
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

    /** Vertical rules on the page (table columns, boxes): text in a cell wraps before the next one. */
    private static List<float[]> verticalRules(JsonNode p) {
        List<float[]> out = new ArrayList<>();
        for (JsonNode d : p.path("drawings")) {
            for (JsonNode it : d.get("items")) {
                String t = it.get(0).asText();
                if (t.equals("l") && Math.abs(f(it, 1) - f(it, 3)) < 1 && Math.abs(f(it, 2) - f(it, 4)) > 4) {
                    out.add(new float[] {f(it, 1), Math.min(f(it, 2), f(it, 4)), Math.max(f(it, 2), f(it, 4))});
                } else if (t.equals("re") && f(it, 4) > 4) {
                    out.add(new float[] {f(it, 1), f(it, 2), f(it, 2) + f(it, 4)});
                    if (f(it, 3) > 2.5f) out.add(new float[] {f(it, 1) + f(it, 3), f(it, 2), f(it, 2) + f(it, 4)});
                }
            }
        }
        return out;
    }

    private static boolean ruleBetween(float a, float b, float baseline) {
        for (float[] r : RULES.get()) {
            if (r[0] >= a - 1 && r[0] <= b + 1 && r[1] <= baseline && r[2] >= baseline - 4) return true;
        }
        return false;
    }

    private static final ThreadLocal<List<float[]>> RULES = ThreadLocal.withInitial(ArrayList::new);

    /** The right edge text starting at x on this baseline may use: the next column rule, or the margin. */
    private static float cellRight(float x, float baseline, float size, float right) {
        float best = Float.MAX_VALUE;
        for (float[] r : RULES.get()) {
            if (r[0] > x + 6 && r[1] <= baseline && r[2] >= baseline - size * 0.6f) best = Math.min(best, r[0]);
        }
        return best == Float.MAX_VALUE ? right : best - 4;
    }

    private static float f(JsonNode it, int i) {
        return (float) it.get(i).asDouble();
    }

    /** Draws the firm logo (or a wordmark if none is uploaded) with its top where the field text sat. */
    static float drawLogo(PDPageContentStream cs, PdfFonts fonts, Branding brand, PDImageXObject logo,
                          float x, float baseline, float size, float h, float maxH, float maxW) throws IOException {
        float top = baseline - size;
        if (logo != null) {
            float[] sz = logoSize(logo, baseline, size, maxH, maxW);
            cs.drawImage(logo, x, h - (baseline + 3), sz[0], sz[1]);
            return sz[0] + 8;
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
}
