package com.legalsuite.voice;

import java.util.Set;

/**
 * Emergency and crisis short codes must be dialled on the handset, never through the firm bridge.
 * Matching is exact on the whole digit string or on the national number after a known country code.
 */
public final class EmergencyNumbers {
    private static final Set<String> CODES = Set.of(
            "000", "112", "911", "999", "988",
            "10111", "10177", "107",
            "110", "111", "113", "114", "119",
            "100", "102", "108", "190", "192", "193");

    private static final String[] COUNTRY_CODES = {"1", "27", "44", "61", "33", "49", "31", "32", "34", "39", "353", "64", "81"};

    private EmergencyNumbers() {}

    public static boolean isEmergency(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return false;
        if (matches(digits)) return true;
        // 00 is an international prefix, but 000 itself is the Australian emergency number.
        if (digits.startsWith("00") && digits.length() > 3 && matches(digits.substring(2))) return true;
        return false;
    }

    private static boolean matches(String digits) {
        if (CODES.contains(digits)) return true;
        for (String cc : COUNTRY_CODES) {
            if (digits.startsWith(cc) && digits.length() > cc.length()) {
                String national = digits.substring(cc.length());
                if (CODES.contains(national)) return true;
                if (national.startsWith("0") && CODES.contains(national.substring(1))) return true;
            }
        }
        return false;
    }

    public static void rejectIfEmergency(String raw) {
        if (isEmergency(raw)) {
            throw com.legalsuite.common.ApiException.badRequest(
                    "Emergency numbers stay on the device dialer. This app will not place that call.");
        }
    }
}
