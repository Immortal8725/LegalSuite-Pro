package com.legalsuite.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MergeEngine {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}");

    private MergeEngine() {}

    public static String merge(String body, Map<String, String> values) {
        if (body == null) return "";
        Map<String, String> lookup = new HashMap<>();
        values.forEach((k, v) -> lookup.put(k.toLowerCase(Locale.ROOT), v == null ? "" : v));
        lookup.putIfAbsent("today", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        Matcher m = TOKEN.matcher(body);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String replacement = lookup.getOrDefault(key, "");
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
