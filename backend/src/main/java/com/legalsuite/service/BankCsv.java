package com.legalsuite.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parse FNB / Standard Bank / ABSA-style CSV trust statements. */
public final class BankCsv {
    public record Row(LocalDate date, String description, BigDecimal amount, BigDecimal balance) {}

    public record Result(List<Row> rows, BigDecimal closingBalance, String source) {}

    private BankCsv() {}

    public static Result parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("CSV is empty");
        }
        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<Row> rows = new ArrayList<>();
        int dateIdx = 0;
        int descIdx = 1;
        int amountIdx = -1;
        int debitIdx = -1;
        int creditIdx = -1;
        int balanceIdx = -1;
        boolean headerSeen = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#")) continue;
            List<String> cols = split(line);
            if (!headerSeen && looksHeader(cols)) {
                headerSeen = true;
                for (int i = 0; i < cols.size(); i++) {
                    String h = cols.get(i).toLowerCase(Locale.ROOT);
                    if (h.contains("date")) dateIdx = i;
                    else if (h.contains("desc") || h.contains("narration") || h.contains("reference")) descIdx = i;
                    else if (h.equals("amount") || h.contains("amount")) amountIdx = i;
                    else if (h.contains("debit")) debitIdx = i;
                    else if (h.contains("credit")) creditIdx = i;
                    else if (h.contains("balance") || h.contains("bal")) balanceIdx = i;
                }
                continue;
            }
            if (cols.size() < 2) continue;
            LocalDate date = TexasDocketRules.parseDate(cols.get(Math.min(dateIdx, cols.size() - 1)));
            String desc = cols.size() > descIdx ? cols.get(descIdx) : "";
            BigDecimal amount = BigDecimal.ZERO;
            if (amountIdx >= 0 && amountIdx < cols.size()) amount = money(cols.get(amountIdx));
            else {
                BigDecimal debit = debitIdx >= 0 && debitIdx < cols.size() ? money(cols.get(debitIdx)) : BigDecimal.ZERO;
                BigDecimal credit = creditIdx >= 0 && creditIdx < cols.size() ? money(cols.get(creditIdx)) : BigDecimal.ZERO;
                amount = credit.subtract(debit);
            }
            BigDecimal bal = balanceIdx >= 0 && balanceIdx < cols.size() ? money(cols.get(balanceIdx)) : null;
            rows.add(new Row(date, desc, amount, bal));
        }
        BigDecimal closing = null;
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).balance() != null) {
                closing = rows.get(i).balance();
                break;
            }
        }
        if (closing == null && !rows.isEmpty()) {
            closing = rows.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (closing == null) throw new IllegalArgumentException("No balance column and no amounts to sum");
        return new Result(rows, closing, "csv");
    }

    private static boolean looksHeader(List<String> cols) {
        String blob = String.join(" ", cols).toLowerCase(Locale.ROOT);
        return blob.contains("date") || blob.contains("description") || blob.contains("balance");
    }

    static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        String s = raw.trim().replace("R", "").replace("ZAR", "").replace(" ", "").replace(",", "");
        if (s.startsWith("(") && s.endsWith(")")) s = "-" + s.substring(1, s.length() - 1);
        if (s.isBlank() || "-".equals(s)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                q = !q;
            } else if ((c == ',' || c == ';') && !q) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
