package com.legalsuite.service;

import com.legalsuite.domain.Tenant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CallEthics {
    private static final Set<String> ALL_PARTY = Set.of(
            "CA", "CT", "DE", "FL", "IL", "MD", "MA", "MI", "MT", "NV", "NH", "OR", "PA", "WA");
    private static final Set<String> ZA_PROVINCES = Set.of("GP", "WC", "KZN", "EC", "FS", "MP", "NW", "NC", "LP", "ZA");

    private CallEthics() {}

    public static Map<String, Object> forState(String state) {
        return forJurisdiction(null, state);
    }

    public static Map<String, Object> forTenant(Tenant tenant) {
        if (tenant == null) return forJurisdiction(null, "US");
        return forJurisdiction(tenant.getCountry(), tenant.getState());
    }

    public static Map<String, Object> forJurisdiction(String country, String state) {
        String ctry = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        String st = state == null || state.isBlank() ? "US" : state.trim().toUpperCase(Locale.ROOT);
        if (ctry.startsWith("ZA") || "SOUTH AFRICA".equalsIgnoreCase(country) || (ctry.isBlank() && ZA_PROVINCES.contains(st))) {
            Map<String, Object> m = new HashMap<>();
            m.put("state", st);
            m.put("country", "ZA");
            m.put("statute", "RICA Act 70 of 2002 s 4");
            m.put("allPartyConsent", false);
            m.put("recordingDefault", false);
            m.put("notice", "South Africa is one-party under RICA s 4 if you are on the line. LPC ethics and POPIA still require an opt-in click and a spoken notice before you record a client or a witness. Recording stays off until you tick it.");
            return m;
        }
        boolean allParty = ALL_PARTY.contains(st);
        Map<String, Object> m = new HashMap<>();
        m.put("state", st);
        m.put("country", ctry.isBlank() ? "US" : ctry);
        m.put("allPartyConsent", allParty);
        m.put("recordingDefault", false);
        m.put("notice", allParty
                ? "All-party consent state. Every person on the line must be told before you record. Recording stays opt-in."
                : "One-party consent in " + st
                        + ". This product still requires an opt-in click. Tell the other party anyway — ethics is not a loophole.");
        return m;
    }

    /** Shown before a public-network dial. Recording still follows {@link #forTenant}. */
    public static String pstnNotice() {
        return "Outbound calls on the public network present a rented number, a verified personal number, or the caller ID configured on the server. "
                + "Your phone rings first. Emergency numbers stay on the device dialer. "
                + "Recording stays opt-in under the same rule as in-app calls. Buying a number is optional.";
    }
}
