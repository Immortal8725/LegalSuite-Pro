package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The matter statuses Owlaw accepts and which moves between them make sense. */
public final class CaseStatusRules {
    /** Every value the web app, mobile app and backend have used. Kept so older matters stay valid. */
    public static final List<String> ALLOWED = List.of(
            "limited", "intake", "open", "pending", "on_hold", "discovery", "mediation",
            "issued", "hearing", "trial", "settled", "closed", "archived", "declined");

    /** A finished matter. It only comes back through an explicit reopen to "open". */
    public static final Set<String> FINISHED = Set.of("settled", "closed", "archived", "declined");

    /** Statuses that put the firm in front of a court, so they respect the docket hold. */
    public static final Set<String> APPEARANCE = Set.of("issued", "hearing", "trial");

    private CaseStatusRules() {}

    public static String normalise(Object raw) {
        if (raw == null) throw ApiException.badRequest("Choose a status for the matter.");
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (!ALLOWED.contains(s)) {
            throw ApiException.badRequest("Unknown matter status \"" + String.valueOf(raw).trim()
                    + "\". Use one of: " + String.join(", ", ALLOWED) + ".");
        }
        return s;
    }

    /** For imports from other systems: map common words, fall back to open instead of failing the row. */
    public static String lenient(String raw) {
        if (raw == null || raw.isBlank()) return "open";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (ALLOWED.contains(s)) return s;
        return switch (s) {
            case "active", "new", "in_progress", "ongoing" -> "open";
            case "won", "lost", "finalised", "finalized", "complete", "completed", "inactive" -> "closed";
            case "onhold", "paused", "dormant" -> "on_hold";
            default -> "open";
        };
    }

    /** Throws a 400 when the move is not allowed. Same status is a no-op and always fine. */
    public static void checkTransition(String from, String to) {
        if (from == null || from.equals(to)) return;
        String f = from.toLowerCase(Locale.ROOT);
        if (!FINISHED.contains(f)) return;
        if ("open".equals(to)) return; // reopen
        if ("archived".equals(to)) return; // filing away a finished matter
        if ("settled".equals(f) && "closed".equals(to)) return;
        throw ApiException.badRequest("This matter is " + f + ". Reopen it to open first, then change the status.");
    }
}
