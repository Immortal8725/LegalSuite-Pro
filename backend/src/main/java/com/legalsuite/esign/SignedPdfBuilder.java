package com.legalsuite.esign;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds the signed copy: the original (PDF pages or the letter text), a signature page and an audit certificate. */
public final class SignedPdfBuilder {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss 'SAST'").withZone(ZoneId.of("Africa/Johannesburg"));
    private static final float MARGIN = 56;

    public record Evidence(
            String title, String firmName, String signerName, String typedName, String signerEmail,
            String identityNumber, java.time.Instant signedAt, String ip, String userAgent,
            String documentSha256, String signatureHash, String requestId, boolean emailConfirmed,
            java.time.Instant emailVerifiedAt) {}

    private SignedPdfBuilder() {}

    /** sourcePdf may be null, then the letter text is typeset instead. */
    public static byte[] build(byte[] sourcePdf, String bodyText, String signatureDataUrl, Evidence ev) throws IOException {
        try (PDDocument doc = sourcePdf != null ? Loader.loadPDF(sourcePdf) : new PDDocument()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            if (sourcePdf == null) {
                writePages(doc, ev.title(), wrap(clean(bodyText == null ? "" : bodyText), regular, 10), regular, bold);
            }
            // Signature page
            List<String> sig = new ArrayList<>();
            sig.add("Signed electronically by " + clean(nz(ev.signerName())) + ".");
            sig.add("Typed name: " + clean(nz(ev.typedName())));
            if (ev.identityNumber() != null && !ev.identityNumber().isBlank()) sig.add("Identity number: " + mask(ev.identityNumber()));
            sig.add("Date and time: " + TS.format(ev.signedAt()));
            sig.add("");
            sig.add("Signature:");
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                y = heading(cs, bold, "Signature page: " + clean(nz(ev.title())), y);
                for (String l : sig) y = line(cs, regular, 11, l, y);
                byte[] png = imageBytes(signatureDataUrl);
                if (png != null) {
                    try {
                        PDImageXObject img = PDImageXObject.createFromByteArray(doc, png, "signature");
                        float w = Math.min(260, img.getWidth());
                        float h = img.getHeight() * (w / Math.max(1, img.getWidth()));
                        cs.drawImage(img, MARGIN, y - h - 4, w, h);
                        y -= h + 12;
                    } catch (IOException ignored) {
                        y = line(cs, regular, 11, "(drawn signature could not be rendered; see the audit certificate)", y);
                    }
                }
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(MARGIN + 260, y);
                cs.stroke();
                line(cs, regular, 9, clean(nz(ev.typedName())), y - 4);
            }
            // Audit certificate
            List<String> a = new ArrayList<>();
            a.add("Document: " + clean(nz(ev.title())));
            if (ev.firmName() != null) a.add("Firm: " + clean(ev.firmName()));
            a.add("Signature request ID: " + ev.requestId());
            a.add("Signer: " + clean(nz(ev.signerName())) + (ev.signerEmail() == null || ev.signerEmail().isBlank() ? "" : " <" + clean(ev.signerEmail()) + ">"));
            a.add("Typed name: " + clean(nz(ev.typedName())));
            a.add("Signed at: " + TS.format(ev.signedAt()) + " (" + ev.signedAt() + " UTC)");
            a.add("IP address: " + clean(nz(ev.ip())));
            a.add("Device / browser: " + clean(nz(ev.userAgent())));
            a.add("Email one-time code: " + (ev.emailConfirmed() ? "confirmed at " + TS.format(ev.emailVerifiedAt()) : "not used"));
            a.add("SHA-256 of the document signed: " + nz(ev.documentSha256()));
            a.add("Signature hash: " + nz(ev.signatureHash()));
            a.add("");
            a.add("Legal basis: Electronic Communications and Transactions Act 25 of 2002, s 13 (electronic signature where no");
            a.add("particular type is required by law). The signer intended to sign by typing their name and drawing a signature.");
            a.add("This is not an advanced electronic signature (s 13(1)). Where a law requires a signature and specifies no type,");
            a.add("an advanced electronic signature is needed; and ECTA s 4(4) and Schedule 2 exclude wills, long-term leases");
            a.add("over 20 years, the sale or alienation of immovable property, and bills of exchange. Use wet ink for those.");
            a.add("");
            a.add("To check integrity: compute the SHA-256 of the original document and compare it with the value above.");
            PDPage cert = new PDPage(PDRectangle.A4);
            doc.addPage(cert);
            try (PDPageContentStream cs = new PDPageContentStream(doc, cert)) {
                float y = cert.getMediaBox().getHeight() - MARGIN;
                y = heading(cs, bold, "Electronic signature audit certificate", y);
                for (String l : a) {
                    for (String w : wrap(l, regular, 9)) y = line(cs, regular, 9, w, y);
                }
            }
            doc.getDocumentInformation().setTitle(clean(nz(ev.title())) + " (signed)");
            doc.getDocumentInformation().setProducer("Owlaw e-sign");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void writePages(PDDocument doc, String title, List<String> lines, PDType1Font regular, PDType1Font bold) throws IOException {
        int i = 0;
        boolean first = true;
        do {
            PDPage p = new PDPage(PDRectangle.A4);
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                float y = p.getMediaBox().getHeight() - MARGIN;
                if (first) y = heading(cs, bold, clean(nz(title)), y);
                first = false;
                while (i < lines.size() && y > MARGIN) y = line(cs, regular, 10, lines.get(i++), y);
            }
        } while (i < lines.size());
    }

    private static float heading(PDPageContentStream cs, PDType1Font f, String text, float y) throws IOException {
        line(cs, f, 14, text, y);
        return y - 26;
    }

    private static float line(PDPageContentStream cs, PDType1Font f, float size, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(MARGIN, y - size);
        cs.showText(text);
        cs.endText();
        return y - size * 1.45f;
    }

    static List<String> wrap(String text, PDType1Font f, float size) throws IOException {
        float max = PDRectangle.A4.getWidth() - 2 * MARGIN;
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = cur.isEmpty() ? word : cur + " " + word;
                if (f.getStringWidth(trial) / 1000 * size > max && !cur.isEmpty()) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(trial);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    /** WinAnsi-safe text for the standard fonts. */
    static String clean(String s) {
        String t = s.replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201C', '"').replace('\u201D', '"')
                .replace('\u2013', '-').replace('\u2014', '-').replace('\u2192', '>').replace("\t", "    ").replace("\r", "");
        StringBuilder b = new StringBuilder();
        for (char c : t.toCharArray()) b.append(c == '\n' || (c >= 32 && c < 127) || (c >= 160 && c <= 255) ? c : '?');
        return b.toString();
    }

    static String mask(String id) {
        String t = id.trim();
        return t.length() <= 4 ? "****" : "*".repeat(t.length() - 4) + t.substring(t.length() - 4);
    }

    private static byte[] imageBytes(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/")) return null;
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).contains("base64")) return null;
        try { return Base64.getDecoder().decode(dataUrl.substring(comma + 1)); } catch (IllegalArgumentException e) { return null; }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
