package com.legalsuite.docgen;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Placeholder handling: {{key}} and {{key:default}}. */
public final class TokenMerge {
    public static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*(?::([^}]*))?\\}\\}");
    public static final String LOGO = "firm_logo";
    static final String EMPTY_BLANK = "____________________";

    private TokenMerge() {}

    public static Set<String> keys(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) out.add(m.group(1).toLowerCase());
        return out;
    }

    /** Replaces every token except the logo. Empty fill-in fields print their original blank. */
    public static String merge(String text, Map<String, String> values) {
        if (text == null) return "";
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            String rep;
            if (LOGO.equals(key)) {
                rep = m.group(0);
            } else {
                String v = values.get(key);
                if (v != null && !v.isBlank()) {
                    rep = v;
                    // "SIGNED AT______" filled in reads "SIGNED AT Rustenburg"
                    if (m.group(2) != null && m.group(2).startsWith("_")) {
                        if (m.start() > 0 && Character.isLetterOrDigit(text.charAt(m.start() - 1))) rep = " " + rep;
                        if (m.end() < text.length() && Character.isLetterOrDigit(text.charAt(m.end()))) rep = rep + " ";
                    }
                }
                else if (m.group(2) != null) rep = m.group(2);
                else rep = FieldCatalog.SYSTEM.contains(key) ? "" : EMPTY_BLANK;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The text as it appeared in the source document: blanks restored, firm fields left as {{key}}. */
    public static String original(String text) {
        if (text == null) return "";
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String rep = m.group(2) != null ? m.group(2) : "{{" + m.group(1) + "}}";
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static String stripLogo(String text) {
        return text.replaceAll("\\{\\{\\s*firm_logo\\s*\\}\\}", "");
    }

    public static boolean hasLogo(String text) {
        return text != null && text.matches("(?s).*\\{\\{\\s*firm_logo\\s*\\}\\}.*");
    }
}
