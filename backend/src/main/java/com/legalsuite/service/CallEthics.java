package com.legalsuite.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CallEthics {
    private static final Set<String> ALL_PARTY = Set.of(
            "CA", "CT", "DE", "FL", "IL", "MD", "MA", "MI", "MT", "NV", "NH", "OR", "PA", "WA");

    private CallEthics() {}

    public static Map<String, Object> forState(String state) {
        String st = state == null || state.isBlank() ? "US" : state.trim().toUpperCase(Locale.ROOT);
        boolean allParty = ALL_PARTY.contains(st);
        return Map.of(
                "state", st,
                "allPartyConsent", allParty,
                "recordingDefault", false,
                "notice", allParty
                        ? "All-party consent state. Every person on the line must be told before you record. Recording stays opt-in."
                        : "One-party consent in " + st
                                + ". This product still requires an opt-in click. Tell the other party anyway — ethics is not a loophole.");
    }
}
