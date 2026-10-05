package com.legalsuite.voice;

import com.legalsuite.common.ApiException;
import java.util.Locale;

/** Normalises a staff-entered phone number to E.164 using the firm country when the plus is omitted. */
public final class E164 {
    private E164() {}

    public static String normalize(String raw, String defaultCountry) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Enter a phone number.");
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        boolean explicit = trimmed.startsWith("+") || raw.trim().startsWith("00");
        if (!explicit) {
            digits = applyCountry(digits, country(defaultCountry));
        }
        if (digits.length() < 8 || digits.length() > 15) {
            throw ApiException.badRequest("That phone number is not a full international number. Include the country code.");
        }
        return "+" + digits;
    }

    private static String country(String defaultCountry) {
        if (defaultCountry == null || defaultCountry.isBlank()) return "US";
        String c = defaultCountry.trim().toUpperCase(Locale.ROOT);
        if (c.startsWith("ZA") || "SOUTH AFRICA".equals(c)) return "ZA";
        if ("UK".equals(c) || "UNITED KINGDOM".equals(c)) return "GB";
        if (c.length() >= 2) return c.substring(0, 2);
        return "US";
    }

    private static String applyCountry(String digits, String country) {
        return switch (country) {
            case "ZA" -> {
                if (digits.startsWith("0") && digits.length() >= 9) yield "27" + digits.substring(1);
                if (digits.startsWith("27") && digits.length() >= 11) yield digits;
                throw ApiException.badRequest("Enter a South African number starting with 0, or use + and the country code.");
            }
            case "GB" -> {
                if (digits.startsWith("0") && digits.length() >= 10) yield "44" + digits.substring(1);
                if (digits.startsWith("44") && digits.length() >= 11) yield digits;
                throw ApiException.badRequest("Enter a UK number starting with 0, or use + and the country code.");
            }
            case "AU" -> {
                if (digits.startsWith("0") && digits.length() >= 9) yield "61" + digits.substring(1);
                if (digits.startsWith("61") && digits.length() >= 11) yield digits;
                throw ApiException.badRequest("Enter an Australian number starting with 0, or use + and the country code.");
            }
            case "US", "CA" -> {
                if (digits.length() == 10) yield "1" + digits;
                if (digits.length() == 11 && digits.startsWith("1")) yield digits;
                throw ApiException.badRequest("Enter a 10-digit number, or use + and the country code.");
            }
            default -> throw ApiException.badRequest("Start the number with + and the country code.");
        };
    }
}
