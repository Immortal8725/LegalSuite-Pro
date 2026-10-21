package com.legalsuite.directory;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Words the directory never shows in firm-written text: claims of expertise or ranking (LPC Code clause 8). */
public final class PublicWording {
    private PublicWording() {}

    static final List<String> BANNED = List.of("specialist", "specialists", "specialise", "specialised", "specialising", "specialize", "specialized", "specializing", "expert", "experts", "best", "top", "leading", "number one", "no. 1", "#1");

    /** The first banned word found, or null. */
    public static String bannedWord(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : BANNED) {
            Pattern p = Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])");
            if (p.matcher(lower).find()) return word;
        }
        return null;
    }
}
