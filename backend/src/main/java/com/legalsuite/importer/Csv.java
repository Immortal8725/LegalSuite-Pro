package com.legalsuite.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small RFC 4180 reader: quoted fields, doubled quotes, commas or semicolons, CRLF or LF. */
public final class Csv {
    private Csv() {}

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) return rows;
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char sep = detect(text);
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(ch);
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                row.add(cell.toString()); cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else cell.append(ch);
        }
        row.add(cell.toString());
        if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
        return rows;
    }

    private static char detect(String text) {
        int nl = text.indexOf('\n');
        String head = nl < 0 ? text : text.substring(0, nl);
        return head.chars().filter(c -> c == ';').count() > head.chars().filter(c -> c == ',').count() ? ';' : ',';
    }

    /** Rows as maps keyed by normalised header (lower case, spaces to underscores). */
    public static List<Map<String, String>> records(String text) {
        List<List<String>> rows = parse(text);
        List<Map<String, String>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<String> head = rows.get(0).stream()
                .map(h -> h.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")).toList();
        for (int r = 1; r < rows.size(); r++) {
            Map<String, String> m = new LinkedHashMap<>();
            List<String> row = rows.get(r);
            for (int c = 0; c < head.size(); c++) m.put(head.get(c), c < row.size() ? row.get(c).trim() : "");
            out.add(m);
        }
        return out;
    }

    public static String escape(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        // Neutralise spreadsheet formulas (CSV injection) and quote when needed.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?[0-9.]+")) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains(";")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
