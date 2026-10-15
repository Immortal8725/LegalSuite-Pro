package com.legalsuite.docgen;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

/** Editable Word version of a positioned template: one paragraph per printed line, columns on tab stops. */
public final class LayoutDocxRenderer {
    private static final int LEFT = 40;
    private static final int TOP = 24;

    private LayoutDocxRenderer() {}

    public static byte[] render(JsonNode tpl, Map<String, String> values, Branding brand) {
        try (XWPFDocument doc = new XWPFDocument()) {
            JsonNode first = tpl.get("pages").get(0);
            CTSectPr sect = doc.getDocument().getBody().addNewSectPr();
            CTPageSz sz = sect.addNewPgSz();
            sz.setW(BigInteger.valueOf(Math.round(first.get("w").asDouble() * 20)));
            sz.setH(BigInteger.valueOf(Math.round(first.get("h").asDouble() * 20)));
            CTPageMar mar = sect.addNewPgMar();
            mar.setLeft(BigInteger.valueOf(LEFT * 20));
            mar.setRight(BigInteger.valueOf(LEFT * 20));
            mar.setTop(BigInteger.valueOf(TOP * 20));
            mar.setBottom(BigInteger.valueOf(TOP * 20));
            boolean firstPage = true;
            if (!tpl.toString().contains("{{firm_logo")) {
                XWPFParagraph lp = doc.createParagraph();
                XWPFRun lr = lp.createRun();
                if (brand.hasLogo()) DocxSupport.picture(lr, brand.logo(), 160, 36);
                else {
                    lr.setText(brand.firmName());
                    lr.setBold(true);
                    lr.setColor(DocxSupport.hex(brand.rgb()));
                }
            }
            for (JsonNode p : tpl.get("pages")) {
                double lastBottom = TOP;
                boolean pageStart = true;
                for (JsonNode line : p.get("lines")) {
                    JsonNode spans = line.get("spans");
                    if (spans.isEmpty()) continue;
                    String rowText = "";
                    for (JsonNode s : spans) rowText += s.get("text").asText();
                    if (rowText.isBlank()) continue;
                    double size = spans.get(0).get("size").asDouble();
                    double y = line.get("y").asDouble();
                    XWPFParagraph para = doc.createParagraph();
                    if (pageStart && !firstPage) para.setPageBreak(true);
                    pageStart = false;
                    double before = Math.max(0, (y - size) - lastBottom);
                    para.setSpacingBefore((int) Math.round(before * 20));
                    para.setSpacingAfter(0);
                    para.setSpacingBetween(size * 1.2, LineSpacingRule.EXACT);
                    double x0 = spans.get(0).get("x").asDouble();
                    para.setIndentationLeft((int) Math.round(Math.max(0, x0 - LEFT) * 20));
                    boolean firstSpan = true;
                    for (JsonNode s : spans) {
                        String raw = s.get("text").asText();
                        if (!firstSpan) {
                            DocxSupport.tabStop(para, Math.round((s.get("x").asDouble() - LEFT) * 20));
                            para.createRun().addTab();
                        }
                        firstSpan = false;
                        if (TokenMerge.hasLogo(raw)) {
                            XWPFRun lr = para.createRun();
                            if (brand.hasLogo()) {
                                DocxSupport.picture(lr, brand.logo(), 190, 54);
                                para.setSpacingBetween(1.0, LineSpacingRule.AUTO);
                            } else {
                                lr.setText(brand.firmName());
                                lr.setBold(true);
                                lr.setFontSize(16);
                                lr.setColor(DocxSupport.hex(brand.rgb()));
                            }
                            raw = TokenMerge.stripLogo(raw);
                        }
                        String text = TokenMerge.merge(raw, values);
                        if (text.isEmpty()) continue;
                        XWPFRun r = para.createRun();
                        r.setText(text.replace('\t', ' '));
                        r.setFontFamily(DocxSupport.wordFont(s.path("font").asText()));
                        r.setFontSize(s.get("size").asDouble());
                        r.setBold(s.path("bold").asBoolean());
                        r.setItalic(s.path("italic").asBoolean());
                        String c = s.path("color").asText("#000000");
                        if (c.matches("#[0-9a-fA-F]{6}") && !"#000000".equals(c)) r.setColor(c.substring(1));
                    }
                    lastBottom = y + size * 0.25;
                }
                firstPage = false;
            }
            if (!tpl.toString().contains("{{footer_text") && !brand.footer().isBlank()) {
                XWPFParagraph fp = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph();
                fp.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                XWPFRun fr = fp.createRun();
                fr.setText(brand.footer());
                fr.setFontSize(8);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
