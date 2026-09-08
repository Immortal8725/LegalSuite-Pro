package com.legalsuite.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Party-aware conflict matching: clients, adverse parties, counsel, and related
 * last names — not a single substring on the display name.
 */
public final class ConflictEngine {
    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP = Set.of(
            "the", "and", "of", "for", "vs", "v", "llc", "inc", "ltd", "co", "corp",
            "company", "incorporated", "corporation", "lp", "pllc", "pc", "pa",
            "pty", "proprietary", "limited", "npc", "cc", "rf", "soc");
    private static final Set<String> FIRST_SKIP = Set.of("a", "an", "mr", "mrs", "ms", "dr", "hon");

    private ConflictEngine() {}

    public record Party(
            String role,
            String displayName,
            String email,
            String lastName,
            String firstName,
            String entityName,
            String caseNumber,
            String matterTitle) {}

    public static List<Map<String, Object>> search(List<String> queries, List<Party> parties) {
        List<Map<String, Object>> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (queries == null || parties == null) return hits;
        for (String q : queries) {
            if (q == null || q.isBlank()) continue;
            for (Party p : parties) {
                Map<String, Object> hit = score(q.trim(), p);
                if (hit == null) continue;
                String key = p.role() + "|" + norm(p.displayName()) + "|" + (p.caseNumber() == null ? "" : p.caseNumber());
                if (!seen.add(key)) continue;
                hits.add(hit);
            }
        }
        hits.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("confidence")).doubleValue()).reversed());
        return hits;
    }

    static Map<String, Object> score(String query, Party p) {
        if (p == null || p.displayName() == null || p.displayName().isBlank()) return null;
        String qn = norm(query);
        String dn = norm(p.displayName());
        String en = norm(p.entityName());
        double confidence = 0;
        String how = null;

        String qEmail = query.contains("@") ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (!qEmail.isBlank() && p.email() != null && qEmail.equals(p.email().trim().toLowerCase(Locale.ROOT))) {
            confidence = 0.99;
            how = "email";
        } else if (!qn.isBlank() && qn.equals(dn)) {
            confidence = 0.95;
            how = "exact name";
        } else if (!en.isBlank() && !qn.isBlank() && (qn.equals(en) || jaccard(tokens(qn), tokens(en)) >= 0.5)) {
            Set<String> inter = intersect(tokens(qn), tokens(en));
            boolean strong = inter.stream().anyMatch(t -> t.length() >= 4);
            if (strong || qn.equals(en)) {
                confidence = qn.equals(en) ? 0.93 : 0.88;
                how = "entity";
            }
        } else {
            String qLast = lastName(query);
            String pLast = p.lastName() == null || p.lastName().isBlank() ? lastName(p.displayName()) : p.lastName();
            pLast = pLast == null ? "" : pLast.toLowerCase(Locale.ROOT);
            if (qLast.length() >= 4 && qLast.equals(pLast)) {
                String qFirst = firstName(query);
                String pFirst = p.firstName() == null ? firstName(p.displayName()) : p.firstName().toLowerCase(Locale.ROOT);
                if (!qFirst.isBlank() && !pFirst.isBlank() && (qFirst.equals(pFirst) || qFirst.startsWith(pFirst) || pFirst.startsWith(qFirst))) {
                    confidence = 0.9;
                    how = "same person";
                } else {
                    confidence = 0.74;
                    how = "related last name";
                }
            }
        }

        if (confidence < 0.7) return null;
        String role = p.role() == null ? "party" : p.role();
        if ("related last name".equals(how) && "client".equals(role)) role = "related";
        Map<String, Object> m = new HashMap<>();
        m.put("type", role);
        m.put("role", role);
        m.put("name", p.displayName());
        m.put("detail", detail(p, how));
        m.put("confidence", confidence);
        m.put("how", how);
        m.put("caseNumber", p.caseNumber() == null ? "" : p.caseNumber());
        return m;
    }

    private static String detail(Party p, String how) {
        List<String> bits = new ArrayList<>();
        if (how != null) bits.add(how);
        if (p.matterTitle() != null && !p.matterTitle().isBlank()) bits.add(p.matterTitle());
        if (p.caseNumber() != null && !p.caseNumber().isBlank()) bits.add(p.caseNumber());
        if (p.email() != null && !p.email().isBlank()) bits.add(p.email());
        return String.join(" · ", bits);
    }

    static String norm(String raw) {
        if (raw == null) return "";
        return tokens(raw.toLowerCase(Locale.ROOT)).stream().collect(Collectors.joining(" "));
    }

    static List<String> tokens(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(SPLIT.split(raw.toLowerCase(Locale.ROOT)))
                .filter(t -> t.length() >= 2 && !STOP.contains(t))
                .toList();
    }

    static String lastName(String name) {
        List<String> t = tokens(name);
        if (t.isEmpty()) return "";
        return t.get(t.size() - 1);
    }

    static String firstName(String name) {
        List<String> t = tokens(name);
        if (t.isEmpty()) return "";
        String first = t.get(0);
        return FIRST_SKIP.contains(first) && t.size() > 1 ? t.get(1) : first;
    }

    private static double jaccard(List<String> a, List<String> b) {
        Set<String> inter = intersect(a, b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0;
        return (double) inter.size() / union.size();
    }

    private static Set<String> intersect(List<String> a, List<String> b) {
        Set<String> s = new LinkedHashSet<>(a);
        s.retainAll(b);
        return s;
    }
}
