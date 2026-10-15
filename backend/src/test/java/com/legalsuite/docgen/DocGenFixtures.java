package com.legalsuite.docgen;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Two demo firms with different logos and letterhead details. */
final class DocGenFixtures {
    private DocGenFixtures() {}

    static byte[] logo(String initials, Color colour, boolean round) {
        try {
            BufferedImage img = new BufferedImage(420, 140, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(colour);
            if (round) g.fillOval(6, 6, 128, 128);
            else g.fillRoundRect(6, 6, 128, 128, 24, 24);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 56));
            g.drawString(initials, 26, 92);
            g.setColor(colour);
            g.setStroke(new BasicStroke(5));
            g.drawLine(150, 104, 410, 104);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            g.drawString("ATTORNEYS", 150, 82);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, String> firmA() {
        Map<String, String> v = new HashMap<>();
        v.put("firm_name", "Mokoena Inc. Attorneys");
        v.put("firm_address", "22 Fatima Bhayat Street, Rustenburg, 0299");
        v.put("firm_phone", "014 592 0101");
        v.put("firm_email", "info@mokoenainc.co.za");
        v.put("firm_ref", "KM");
        v.put("attorney_name", "Kagiso Mokoena");
        v.put("attorney_title", "Attorney of the High Court");
        v.put("information_officer", "Kagiso Mokoena");
        v.put("trust_account_name", "Mokoena Inc. Trust Account");
        v.put("trust_account_number", "1048 552 731");
        v.put("bank_name", "Absa Bank");
        v.put("branch_name", "Rustenburg");
        v.put("branch_code", "632005");
        v.put("footer_text", BrandingService.defaultFooter("Kagiso Mokoena", "Attorney of the High Court"));
        v.put("date", "15 October 2026");
        return v;
    }

    static Map<String, String> firmB() {
        Map<String, String> v = new HashMap<>();
        v.put("firm_name", "Van der Merwe & Dlamini Attorneys");
        v.put("firm_address", "Block C, 101 Witch-Hazel Avenue, Centurion, 0157");
        v.put("firm_phone", "012 665 4400");
        v.put("firm_email", "admin@vdmdlaw.co.za");
        v.put("firm_ref", "VDM");
        v.put("attorney_name", "Nomsa Dlamini");
        v.put("attorney_title", "Director, Conveyancer and Notary");
        v.put("information_officer", "Pieter van der Merwe");
        v.put("trust_account_name", "VDM & Dlamini Inc Trust");
        v.put("trust_account_number", "62 0099 1187");
        v.put("bank_name", "First National Bank");
        v.put("branch_name", "Centurion Mall");
        v.put("branch_code", "250655");
        v.put("footer_text", BrandingService.defaultFooter("Nomsa Dlamini", "Director, Conveyancer and Notary"));
        v.put("date", "15 October 2026");
        return v;
    }

    /** Client details for firm A's samples; firm B's samples keep the blanks to show the unfilled form. */
    static void clientA(Map<String, String> v) {
        v.put("client_name", "Lerato Palesa Molefe");
        v.put("client_id_number", "8807150456081");
        v.put("accident_date", "3 March 2026");
        v.put("deceased_name", "Johannes Tumelo Molefe");
        v.put("date_of_death", "12 June 2026");
        v.put("estate_number", "008123/2026");
        v.put("file_number", "MOL001");
        v.put("signed_at", "Rustenburg");
    }
}
