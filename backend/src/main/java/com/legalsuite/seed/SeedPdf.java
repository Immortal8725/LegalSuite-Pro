package com.legalsuite.seed;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Writes a small, valid one-page PDF so seeded documents open in the browser like real uploads. */
final class SeedPdf {
    private SeedPdf() {}

    static byte[] render(String title, List<String> lines) {
        StringBuilder text = new StringBuilder("BT /F1 16 Tf 72 760 Td (").append(esc(title)).append(") Tj ET\n");
        int y = 728;
        for (String line : wrap(lines, 90)) {
            text.append("BT /F1 11 Tf 72 ").append(y).append(" Td (").append(esc(line)).append(") Tj ET\n");
            y -= 16;
            if (y < 60) break;
        }
        byte[] stream = text.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        offsets.add(out.size());
        write(out, "5 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        out.writeBytes(stream);
        write(out, "endstream\nendobj\n");
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static List<String> wrap(List<String> lines, int width) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String rest = line;
            while (rest.length() > width) {
                int cut = rest.lastIndexOf(' ', width);
                if (cut <= 0) cut = width;
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut).trim();
            }
            out.add(rest);
        }
        return out;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
