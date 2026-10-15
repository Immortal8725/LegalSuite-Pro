package com.legalsuite.docgen;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;

final class DocxSupport {
    private DocxSupport() {}

    static String wordFont(String kind) {
        return switch (kind == null ? "sans" : kind) {
            case "serif" -> "Times New Roman";
            case "carlito" -> "Calibri";
            default -> "Arial";
        };
    }

    static void tabStop(XWPFParagraph p, long twips) {
        CTPPr ppr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTTabs tabs = ppr.isSetTabs() ? ppr.getTabs() : ppr.addNewTabs();
        CTTabStop ts = tabs.addNewTab();
        ts.setVal(STTabJc.LEFT);
        ts.setPos(BigInteger.valueOf(Math.max(0, twips)));
    }

    /** Adds the logo picture to a run, at most maxW by maxH points. */
    static void picture(XWPFRun run, byte[] logo, float maxW, float maxH) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(logo));
            if (img == null) return;
            double scale = Math.min(maxH / img.getHeight(), maxW / img.getWidth());
            boolean png = logo.length > 3 && (logo[0] & 0xff) == 0x89 && logo[1] == 'P';
            run.addPicture(new ByteArrayInputStream(logo), png ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG,
                    png ? "logo.png" : "logo.jpg", Units.toEMU(img.getWidth() * scale), Units.toEMU(img.getHeight() * scale));
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            // A logo that cannot be read is left out; the firm name still prints.
        }
    }

    static String hex(float[] rgb) {
        return String.format("%02X%02X%02X", Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
    }
}
