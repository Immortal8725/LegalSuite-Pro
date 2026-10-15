package com.legalsuite.docgen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/** Word templates: the firm's own DOCX keeps its formatting; only the {{fields}} change. */
public final class DocxTemplateEngine {
    /** One paragraph of a merged Word document, for the PDF version. */
    public record Para(String text, boolean bold, boolean italic, double size, String align, String font, boolean logo, int indent) {}

    private static final Pattern BRACKET = Pattern.compile("\\[([A-Za-z][^\\]\\[{}]{1,78})\\]");

    private DocxTemplateEngine() {}

    public static XWPFDocument open(byte[] bytes) {
        try {
            return new XWPFDocument(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("That file is not a readable Word (.docx) document.");
        }
    }

    static List<XWPFParagraph> allParagraphs(XWPFDocument doc) {
        List<XWPFParagraph> out = new ArrayList<>(doc.getParagraphs());
        for (XWPFTable t : doc.getTables()) tableParas(t, out);
        for (XWPFHeader h : doc.getHeaderList()) {
            out.addAll(h.getParagraphs());
            for (XWPFTable t : h.getTables()) tableParas(t, out);
        }
        for (XWPFFooter f : doc.getFooterList()) {
            out.addAll(f.getParagraphs());
            for (XWPFTable t : f.getTables()) tableParas(t, out);
        }
        return out;
    }

    private static void tableParas(XWPFTable t, List<XWPFParagraph> out) {
        for (XWPFTableRow row : t.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                out.addAll(cell.getParagraphs());
                for (XWPFTable inner : cell.getTables()) tableParas(inner, out);
            }
        }
    }

    public static Set<String> fieldKeys(byte[] bytes) {
        try (XWPFDocument doc = open(bytes)) {
            Set<String> keys = new LinkedHashSet<>();
            for (XWPFParagraph p : allParagraphs(doc)) keys.addAll(TokenMerge.keys(p.getText()));
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uploaded templates: [BRACKET PROMPTS] become fill-in fields that print the prompt until filled. */
    public static byte[] normalise(byte[] bytes) {
        try (XWPFDocument doc = open(bytes)) {
            for (XWPFParagraph p : allParagraphs(doc)) {
                String text = p.getText();
                if (!BRACKET.matcher(text).find()) continue;
                Matcher m = BRACKET.matcher(text);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    String key = m.group(1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                    if (key.length() > 40) key = key.substring(0, 40).replaceAll("_$", "");
                    m.appendReplacement(sb, Matcher.quoteReplacement("{{" + key + ":[" + m.group(1) + "]}}"));
                }
                m.appendTail(sb);
                setText(p, sb.toString());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setText(XWPFParagraph p, String text) {
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            p.createRun().setText(text, 0);
            return;
        }
        runs.get(0).setText(text, 0);
        for (int i = runs.size() - 1; i >= 1; i--) p.removeRun(i);
    }

    public static byte[] merge(byte[] template, Map<String, String> values, Branding brand) {
        try (XWPFDocument doc = open(template)) {
            boolean placedLogo = false;
            for (XWPFParagraph p : allParagraphs(doc)) {
                String text = p.getText();
                if (!text.contains("{{")) continue;
                String merged = TokenMerge.merge(text, values);
                if (TokenMerge.hasLogo(merged)) {
                    placedLogo = true;
                    String[] parts = merged.split("\\{\\{\\s*firm_logo\\s*\\}\\}", -1);
                    setText(p, parts[0]);
                    for (int i = 1; i < parts.length; i++) {
                        XWPFRun lr = p.createRun();
                        if (brand.hasLogo()) DocxSupport.picture(lr, brand.logo(), 190, 54);
                        else {
                            lr.setText(brand.firmName());
                            lr.setBold(true);
                            lr.setColor(DocxSupport.hex(brand.rgb()));
                        }
                        if (!parts[i].isEmpty()) p.createRun().setText(parts[i]);
                    }
                } else {
                    setText(p, merged);
                }
            }
            boolean blankHeaders = doc.getHeaderList().stream().allMatch(x -> x.getText().isBlank() && x.getAllPictures().isEmpty());
            if (!placedLogo && blankHeaders) letterhead(doc, values, brand);
            boolean blankFooters = doc.getFooterList().stream().allMatch(x -> x.getText().isBlank());
            if (blankFooters && !brand.footer().isBlank()) {
                XWPFFooter f = doc.getFooterList().isEmpty() ? doc.createFooter(HeaderFooterType.DEFAULT) : doc.getFooterList().get(0);
                XWPFParagraph fp = f.createParagraph();
                fp.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = fp.createRun();
                r.setText(brand.footer());
                r.setFontSize(8);
                r.setColor(DocxSupport.hex(brand.rgb()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void letterhead(XWPFDocument doc, Map<String, String> values, Branding brand) {
        XWPFHeader h = doc.getHeaderList().isEmpty() ? doc.createHeader(HeaderFooterType.DEFAULT) : doc.getHeaderList().get(0);
        XWPFParagraph p = h.getParagraphs().isEmpty() ? h.createParagraph() : h.getParagraphs().get(0);
        XWPFRun r = p.createRun();
        if (brand.hasLogo()) {
            DocxSupport.picture(r, brand.logo(), 170, 48);
            r.addBreak();
        }
        XWPFRun name = p.createRun();
        name.setText(brand.firmName());
        name.setBold(true);
        name.setFontSize(12);
        name.setColor(DocxSupport.hex(brand.rgb()));
        String contact = contactLine(values);
        if (!contact.isBlank()) {
            name.addBreak();
            XWPFRun c = p.createRun();
            c.setText(contact);
            c.setFontSize(8);
        }
        p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
    }

    static String contactLine(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        if (!values.getOrDefault("firm_address", "").isBlank()) parts.add(values.get("firm_address"));
        if (!values.getOrDefault("firm_phone", "").isBlank()) parts.add("Tel: " + values.get("firm_phone"));
        if (!values.getOrDefault("firm_email", "").isBlank()) parts.add("Email: " + values.get("firm_email"));
        return String.join("  |  ", parts);
    }

    /** Body paragraphs (and table rows) of a merged document in reading order, with list numbering applied. */
    public static List<Para> paragraphs(byte[] merged) {
        try (XWPFDocument doc = open(merged)) {
            List<Para> out = new ArrayList<>();
            Map<String, Integer> counters = new HashMap<>();
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) out.add(para(p, counters));
                else if (el instanceof XWPFTable t) {
                    for (XWPFTableRow row : t.getRows()) {
                        List<String> cells = new ArrayList<>();
                        boolean bold = false;
                        for (XWPFTableCell c : row.getTableCells()) {
                            cells.add(c.getText().trim());
                            if (!c.getParagraphs().isEmpty() && !c.getParagraphs().get(0).getRuns().isEmpty()) {
                                bold |= c.getParagraphs().get(0).getRuns().get(0).isBold();
                            }
                        }
                        out.add(new Para(String.join("    ", cells), bold, false, 11, "left", "serif", false, 0));
                    }
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Para para(XWPFParagraph p, Map<String, Integer> counters) {
        String text = p.getText();
        boolean logo = false;
        if (!p.getRuns().isEmpty() && !p.getRuns().get(0).getEmbeddedPictures().isEmpty() && text.isBlank()) logo = true;
        String prefix = "";
        if (p.getNumID() != null) {
            String fmt = p.getNumFmt();
            String key = p.getNumID() + ":" + (p.getNumIlvl() == null ? "0" : p.getNumIlvl().toString());
            int n = counters.merge(key, 1, Integer::sum);
            if ("bullet".equals(fmt)) prefix = "\u2022  ";
            else {
                String lvl = p.getNumLevelText();
                prefix = (lvl == null ? n + "." : lvl.replaceAll("%\\d", String.valueOf(n))) + "  ";
            }
        }
        boolean bold = false;
        boolean italic = false;
        double size = 11;
        String font = "serif";
        if (!p.getRuns().isEmpty()) {
            XWPFRun r = p.getRuns().get(0);
            bold = r.isBold();
            italic = r.isItalic();
            Double fs = r.getFontSizeAsDouble();
            if (fs != null) size = fs;
            String fam = r.getFontFamily();
            if (fam != null) {
                String f = fam.toLowerCase(Locale.ROOT);
                font = f.contains("calibri") ? "carlito" : f.contains("arial") || f.contains("helvetica") ? "sans" : "serif";
            }
        }
        if (p.getStyle() != null && p.getStyle().toLowerCase(Locale.ROOT).startsWith("heading")) bold = true;
        String align = switch (p.getAlignment()) {
            case CENTER -> "center";
            case RIGHT, END -> "right";
            default -> "left";
        };
        int indent = p.getIndentationLeft() > 0 ? p.getIndentationLeft() / 20 : 0;
        return new Para(prefix + text, bold, italic, size, align, font, logo, indent + (prefix.isEmpty() ? 0 : 0));
    }
}
