package com.legalsuite.service;

import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DocketEngine {
    private static final Set<String> ZA_PROVINCES = Set.of("GP", "WC", "KZN", "EC", "FS", "MP", "NW", "NC", "LP", "ZA");

    private DocketEngine() {}

    public static String of(Tenant tenant) {
        if (tenant == null) return "TX";
        String country = tenant.getCountry() == null ? "" : tenant.getCountry().trim().toUpperCase(Locale.ROOT);
        if (country.startsWith("ZA") || "SOUTH AFRICA".equalsIgnoreCase(tenant.getCountry())) return "ZA";
        String state = tenant.getState() == null ? "" : tenant.getState().trim().toUpperCase(Locale.ROOT);
        if (ZA_PROVINCES.contains(state) && !"US".equals(country)) return "ZA";
        if ("TX".equals(state) || "US".equals(country)) return "TX";
        return country.isBlank() ? "TX" : country;
    }

    public static String currency(Tenant tenant) {
        return "ZA".equals(of(tenant)) ? "ZAR" : "USD";
    }

    public static String trustLabel(Tenant tenant) {
        return "ZA".equals(of(tenant)) ? "section 86 trust" : "IOLTA";
    }

    public static TexasDocketRules.Result compute(String jurisdiction, TexasDocketRules.Facts facts) {
        if ("ZA".equalsIgnoreCase(jurisdiction)) return SouthAfricanDocketRules.compute(facts);
        return TexasDocketRules.compute(facts);
    }

    public static TexasDocketRules.Result compute(Tenant tenant, TexasDocketRules.Facts facts) {
        return compute(of(tenant), facts);
    }

    public static TexasDocketRules.Result preview(Tenant tenant, Map<String, Object> body) {
        String override = body == null || body.get("jurisdiction") == null ? null : String.valueOf(body.get("jurisdiction"));
        String j = override == null || override.isBlank() || "null".equals(override) ? of(tenant) : override.trim().toUpperCase(Locale.ROOT);
        return compute(j, TexasDocketRules.Facts.from(body));
    }

    public static void stamp(LegalCase c, TexasDocketRules.Result result) {
        TexasDocketRules.stamp(c, result);
    }
}
