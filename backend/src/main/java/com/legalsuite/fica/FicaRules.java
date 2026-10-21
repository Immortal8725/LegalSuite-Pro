package com.legalsuite.fica;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Risk scoring and completeness rules. A firm's own RMCP may be stricter; these are the defaults. */
public final class FicaRules {
    private FicaRules() {}

    /** factor -> weight. Sanctions match is not scored: it blocks the client. */
    public static final Map<String, Integer> FACTORS = new LinkedHashMap<>();
    static {
        FACTORS.put("pep_or_ppo", 3);            // Domestic or foreign prominent influential person (s 21F to 21H)
        FACTORS.put("high_risk_country", 3);     // FATF grey or black list link
        FACTORS.put("cash_heavy", 2);            // Large or frequent cash
        FACTORS.put("complex_structure", 2);     // Trust, layered companies, nominee shareholders
        FACTORS.put("non_face_to_face", 1);      // Never met in person
        FACTORS.put("unusual_instruction", 2);   // Transaction with no clear economic purpose
        FACTORS.put("property_transfer", 1);     // Conveyancing or high value asset
        FACTORS.put("third_party_funding", 1);   // Money from someone else
    }

    public static final Set<String> SANCTIONS_RESULTS = Set.of("clear", "possible_match", "match");

    public static int score(List<String> factors) {
        int s = 0;
        for (String f : factors) s += FACTORS.getOrDefault(f, 0);
        return s;
    }

    public static String rating(int score, String pep) {
        if ("yes".equals(pep) || score >= 4) return "high";
        if (score >= 2) return "medium";
        return "low";
    }

    /** Re-verification interval by risk. */
    public static LocalDate nextReview(String rating, LocalDate from) {
        return switch (rating == null ? "medium" : rating) {
            case "high" -> from.plusYears(1);
            case "low" -> from.plusYears(3);
            default -> from.plusYears(2);
        };
    }

    public static List<String> missing(FicaProfile p, LocalDate today) {
        List<String> m = new ArrayList<>();
        if (p.getIdNumber() == null || p.getIdNumber().isBlank()) m.add("ID or registration number");
        if (p.getIdDocumentId() == null) m.add("Copy of ID or registration document");
        if (p.getIdExpiry() != null && p.getIdExpiry().isBefore(today)) m.add("ID document has expired");
        if (p.getPoaDocumentId() == null) m.add("Proof of address");
        else if (p.getPoaDate() != null && p.getPoaDate().isBefore(today.minusMonths(3)) && p.getVerifiedAt() == null) m.add("Proof of address older than 3 months");
        if (p.getPepStatus() == null) m.add("PEP / prominent influential person check");
        if (p.getSanctionsCheckedAt() == null) m.add("Targeted financial sanctions check");
        if (p.getRiskRating() == null) m.add("Risk rating");
        if ("high".equals(p.getRiskRating()) && (p.getSourceOfFunds() == null || p.getSourceOfFunds().isBlank())) m.add("Source of funds (high risk)");
        return m;
    }
}
