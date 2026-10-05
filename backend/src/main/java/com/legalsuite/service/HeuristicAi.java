package com.legalsuite.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeuristicAi {
    private HeuristicAi() {}

    public static Map<String, Object> screenIntake(String name, String caseType, String description) {
        String text = ((caseType == null ? "" : caseType) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        int score = 40;
        List<String> flags = new ArrayList<>();
        if (containsAny(text, "injury", "accident", "crash", "malpractice")) {
            score += 18;
            flags.add("Personal-injury posture. Confirm the statute of limitations on day one.");
        }
        if (containsAny(text, "urgent", "emergency", "tomorrow", "injunction", "tro")) {
            score += 20;
            flags.add("Time-sensitive language. Calendar a same-week consult.");
        }
        if (containsAny(text, "estate", "will", "probate", "trust")) {
            score += 8;
            flags.add("Estate matter. Ask for the original will and death certificate.");
        }
        if (containsAny(text, "criminal", "arrest", "dui", "felony")) {
            score += 10;
            flags.add("Criminal inquiry. Check for a first-appearance date.");
        }
        if (containsAny(text, "corporation", "llc", "formation", "contract")) {
            score += 6;
            flags.add("Business work. Request formation docs or the disputed agreement.");
        }
        if (containsAny(text, "pro bono", "cannot pay", "no money")) {
            score -= 15;
            flags.add("Fee sensitivity flagged. Discuss limited-scope or referral.");
        }
        if (name != null && name.toLowerCase(Locale.ROOT).contains("corp")) {
            flags.add("Entity name. Run a conflicts search against officers as well as the company.");
        }
        score = Math.max(5, Math.min(98, score));
        String band = score >= 75 ? "retain_now" : score >= 55 ? "consult" : "screen";
        String summary = "Intake for " + (name == null || name.isBlank() ? "an unnamed prospect" : name)
                + " scores " + score + "/100 (" + band.replace('_', ' ') + "). "
                + (flags.isEmpty() ? "No special flags." : flags.get(0));
        return Map.of(
                "score", score,
                "band", band,
                "summary", summary,
                "flags", flags,
                "recommendedNext", band.equals("retain_now")
                        ? "Open a matter, send a fee agreement, and run conflicts."
                        : band.equals("consult")
                                ? "Book a consult and send a conflicts questionnaire."
                                : "Reply with a short declination or a referral.");
    }

    public static String summarize(String title, List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return "No notes on " + (title == null ? "this matter" : title) + " yet. Add a first entry after the next call.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Matter summary");
        if (title != null && !title.isBlank()) sb.append(": ").append(title);
        sb.append(".\n\n");
        sb.append("There are ").append(notes.size()).append(" note(s) on file:\n");
        int i = 1;
        for (String n : notes) {
            if (n == null || n.isBlank()) continue;
            String clip = n.replaceAll("\\s+", " ").trim();
            if (clip.length() > 220) clip = clip.substring(0, 217) + "…";
            sb.append(i++).append(". ").append(clip).append("\n");
            if (i > 6) break;
        }
        sb.append("\nNext: confirm the upcoming deadline and log the last client conversation as a time entry.");
        return sb.toString();
    }

    public static String draftEmail(String kind, String clientName, String matterTitle, String extra) {
        String who = clientName == null || clientName.isBlank() ? "Client" : clientName;
        String matter = matterTitle == null || matterTitle.isBlank() ? "your matter" : matterTitle;
        String note = extra == null ? "" : extra.trim();
        return switch (kind == null ? "status" : kind.toLowerCase(Locale.ROOT)) {
            case "retainer" -> """
                    Subject: Engagement letter: %s

                    Dear %s,

                    Thank you for asking Smith & Associates to represent you in %s. Enclosed is our engagement letter and fee agreement. Please review, sign, and return it so we can open the file.

                    If anything in the letter is unclear, call the office and we will walk through it.

                    %s

                    Respectfully,
                    """.formatted(matter, who, matter, note);
            case "demand" -> """
                    Subject: Demand: %s

                    Counsel:

                    We represent %s in connection with %s. This letter is a demand that your client cure the issues described below and confirm a response within fourteen (14) days.

                    %s

                    We remain willing to discuss a practical resolution.

                    Very truly yours,
                    """.formatted(matter, who, matter, note.isBlank() ? "Please see the facts already exchanged." : note);
            case "status" -> """
                    Subject: Status update: %s

                    Dear %s,

                    A short update on %s. Work is moving. We will notify you as soon as a hearing date, filing, or settlement number is in hand.

                    %s

                    Please call if questions come up before then.

                    Warm regards,
                    """.formatted(matter, who, matter, note);
            default -> """
                    Subject: %s

                    Dear %s,

                    %s

                    Sincerely,
                    """.formatted(matter, who, note.isBlank() ? "Please see the attached." : note);
        };
    }

    public static boolean matches(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
