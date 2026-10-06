package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Closed list of public-site features and accent colours. */
public final class PublicSiteCatalog {
    public record Feature(String key, String label, String description) {}

    public record Article(String slug, String title, String type, String date, String summary, String body) {}

    public record Situation(String slug, String title, String summary) {}

    public static final List<Feature> FEATURES = List.of(
            new Feature("people", "People directory", "Names, titles, and contact details for the lawyers."),
            new Feature("insights", "Insights", "Short notes. A full article editor is not in this version."),
            new Feature("situations", "Who we help", "Wayfinding by situation, for clients who do not think in practice names."),
            new Feature("fees", "Fees", "How fees are agreed. No quote for a specific matter."),
            new Feature("whatsapp", "WhatsApp", "A WhatsApp link beside the office phone."),
            new Feature("booking", "Enquiry", "The consultation form on the public site."),
            new Feature("newsletter", "Newsletter", "An unticked email opt-in. No message is sent until a mail tool is connected."),
            new Feature("recognition", "Recognition", "Awards and credentials with a year and a source."));

    private static final Map<String, String> ACCENTS = new LinkedHashMap<>();
    private static final Map<String, String> ACCENT_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> THEMES = new LinkedHashMap<>();

    static {
        ACCENTS.put("navy", "#1b3a4b");
        ACCENTS.put("forest", "#1f4d3a");
        ACCENTS.put("oxblood", "#7a2e2e");
        ACCENTS.put("copper", "#8c5a2b");
        ACCENT_LABELS.put("navy", "Navy");
        ACCENT_LABELS.put("forest", "Forest");
        ACCENT_LABELS.put("oxblood", "Oxblood");
        ACCENT_LABELS.put("copper", "Copper");
        THEMES.put("light", "Light");
        THEMES.put("dark", "Dark");
    }

    private static final List<Article> ZA_INSIGHTS = List.of(
            new Article(
                    "raf-clocks",
                    "Road Accident Fund claims have a clock",
                    "Note",
                    "2026-03-01",
                    "The Fund Act sets a time limit. Missing it can end the claim.",
                    "The Road Accident Fund Act sets a time limit on claims. The date of the accident is usually the start. Missing the limit can end the claim. This note is general information. It is not advice on your matter. Ask the firm to look at your dates before you rely on it."),
            new Article(
                    "organ-of-state-notice",
                    "Notice before a claim against an organ of state",
                    "Note",
                    "2026-03-01",
                    "Act 40 of 2002 requires notice before many claims against a municipality, a department, or the police.",
                    "The Institution of Legal Proceedings against certain Organs of State Act 40 of 2002 requires notice before many claims against a municipality, a department, or the police. The notice period is short. This note is general information. It is not advice on your matter."));

    private static final List<Article> OTHER_INSIGHTS = List.of(
            new Article(
                    "deadlines-differ",
                    "Deadlines depend on the claim",
                    "Note",
                    "2026-03-01",
                    "Limitation periods change with the kind of claim and with who is sued.",
                    "Limitation periods depend on the kind of claim and on who the defendant is. This note is general information. It is not advice on your matter."));

    private static final List<Situation> ZA_SITUATIONS = List.of(
            new Situation("road-accident", "Injured in a road accident", "A claim against the Road Accident Fund, including hit and run where the facts allow it."),
            new Situation("organ-of-state", "A claim against a municipality or department", "Notice under Act 40 of 2002, then the claim itself."),
            new Situation("workplace", "A workplace dispute", "CCMA referrals and the Labour Relations Act clock."),
            new Situation("estate", "An estate", "Reporting an estate and the work of an executor."));

    private static final List<Situation> OTHER_SITUATIONS = List.of(
            new Situation("court-dispute", "A dispute in court", "Civil claims, with the deadline checked before a file is opened."),
            new Situation("family", "A family matter", "Divorce, parenting, and related court work."),
            new Situation("estate", "An estate", "Wills, probate, and administration."));

    private PublicSiteCatalog() {}

    public static Feature requireFeature(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return FEATURES.stream().filter(f -> f.key().equals(k)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown site feature"));
    }

    public static String requireAccent(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!ACCENTS.containsKey(k)) {
            throw ApiException.badRequest("Choose an accent from the allowed set");
        }
        return k;
    }

    public static String hex(String key) {
        String hex = ACCENTS.get(key);
        return hex == null ? ACCENTS.get("navy") : hex;
    }

    public static List<Map<String, String>> accentChoices() {
        return ACCENTS.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "hex", e.getValue(), "label", ACCENT_LABELS.get(e.getKey())))
                .toList();
    }

    public static Set<String> accentKeys() {
        return ACCENTS.keySet();
    }

    public static String requireTheme(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!THEMES.containsKey(k)) {
            throw ApiException.badRequest("Choose light or dark");
        }
        return k;
    }

    public static List<Map<String, String>> themeChoices() {
        return THEMES.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "label", e.getValue()))
                .toList();
    }

    public static String slugify(String name) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "practice" : slug;
    }

    public static String whatsappUrl(String phone, String country) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if ("ZA".equalsIgnoreCase(country) && digits.startsWith("0") && digits.length() >= 10) {
            digits = "27" + digits.substring(1);
        }
        return "https://wa.me/" + digits;
    }

    public static List<Article> insights(String country) {
        return "ZA".equalsIgnoreCase(country) ? ZA_INSIGHTS : OTHER_INSIGHTS;
    }

    public static List<Situation> situations(String country) {
        return "ZA".equalsIgnoreCase(country) ? ZA_SITUATIONS : OTHER_SITUATIONS;
    }

    public static String feesNote(String country) {
        if ("ZA".equalsIgnoreCase(country)) {
            return "Fees are set in a written mandate before the firm acts. The usual basis is hourly, plus VAT where the law requires it. This page does not quote a fee for your matter.";
        }
        return "Fees are set in a written engagement letter before the firm acts. The usual basis is hourly. This page does not quote a fee for your matter.";
    }
}
