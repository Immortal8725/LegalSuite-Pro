package com.legalsuite.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Owlaw prices in rand, excluding VAT. Plans are priced per attorney per month.
 * Support staff take a staff seat. The phone is usage: there is no minute bundle and no unlimited voice.
 */
public final class Pricing {
    public static final String SOLO_SLUG = "solo";
    public static final String FIRM_SLUG = "firm";
    public static final String PRACTICE_SLUG = "practice";
    /** The old single plan. Existing tenants on it are treated as Firm. */
    public static final String LEGACY_LIGHT_SLUG = "light";
    public static final String DEFAULT_SLUG = FIRM_SLUG;

    public static final BigDecimal SOLO_MONTHLY_ZAR = new BigDecimal("899");
    public static final BigDecimal FIRM_MONTHLY_ZAR = new BigDecimal("1199");
    public static final BigDecimal PRACTICE_MONTHLY_ZAR = new BigDecimal("1599");
    public static final BigDecimal STAFF_SEAT_MONTHLY_ZAR = new BigDecimal("349");

    /** Calls on the firm number, per minute. */
    public static final BigDecimal CALL_PER_MINUTE_ZAR = new BigDecimal("1.49");
    /** Verified own landline as caller ID: monthly fee and per-minute rate. */
    public static final BigDecimal LANDLINE_MONTHLY_ZAR = new BigDecimal("149");
    public static final BigDecimal LANDLINE_PER_MINUTE_ZAR = new BigDecimal("8.90");
    /** Public website add-on for Solo. Firm and Practice include the website. */
    public static final BigDecimal SOLO_WEBSITE_MONTHLY_ZAR = new BigDecimal("499");
    public static final BigDecimal SOLO_WEBSITE_SETUP_ZAR = new BigDecimal("1500");
    public static final int STAFF_AI_ACTIONS = 50;

    /** Public-network minutes included with a seat. Zero means pay-what-you-use. */
    public static final int INCLUDED_PSTN_MINUTES = 0;
    /** Kept for older callers: the optional local number is now priced into the call rate. */
    public static final BigDecimal DID_MONTHLY_ZAR = BigDecimal.ZERO;

    public record PlanSpec(
            String slug,
            String name,
            BigDecimal monthlyZar,
            Integer maxAttorneys,
            int aiActionsPerAttorney,
            boolean websiteIncluded,
            boolean staffSeats,
            boolean callRecording,
            boolean prioritySupport,
            String summary,
            int sortOrder) {}

    public static final PlanSpec SOLO = new PlanSpec(SOLO_SLUG, "Solo", SOLO_MONTHLY_ZAR, 1, 100, false, false, false, false,
            "R899 per month for one attorney. Matters, docket clocks, billing, invoices, payments, write-offs, proof-of-payment review, section 86 trust including trust-to-fee, documents, calendar, the firm number, SMS, WhatsApp and email, and 100 assistant actions. The public website is an add-on at R499 per month plus R1,500 setup.",
            1);
    public static final PlanSpec FIRM = new PlanSpec(FIRM_SLUG, "Firm", FIRM_MONTHLY_ZAR, null, 300, true, true, false, false,
            "R1,199 per attorney per month. Everything in Solo plus the public website (people, expertise, insights, contact, theme and accent colours), 300 assistant actions per attorney, and staff seats at R349.",
            2);
    public static final PlanSpec PRACTICE = new PlanSpec(PRACTICE_SLUG, "Practice", PRACTICE_MONTHLY_ZAR, null, 1000, true, true, true, true,
            "R1,599 per attorney per month. Everything in Firm plus 1,000 assistant actions per attorney, call recording, advanced reporting, priority support and fast-track for website feature requests.",
            3);
    public static final List<PlanSpec> PLANS = List.of(SOLO, FIRM, PRACTICE);

    private Pricing() {}

    /** Maps a requested or stored plan slug to a current plan. Unknown and legacy slugs become Firm. */
    public static PlanSpec plan(String slug) {
        String s = canonicalSlug(slug);
        for (PlanSpec p : PLANS) {
            if (p.slug().equals(s)) return p;
        }
        return FIRM;
    }

    public static String canonicalSlug(String slug) {
        if (slug == null || slug.isBlank()) return DEFAULT_SLUG;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_LIGHT_SLUG.equals(s)) return FIRM_SLUG;
        for (PlanSpec p : PLANS) {
            if (p.slug().equals(s)) return s;
        }
        return DEFAULT_SLUG;
    }

    /** Monthly subscription in rand for attorneys plus staff seats on a plan. */
    public static BigDecimal monthlyTotal(String slug, int attorneys, int staff) {
        PlanSpec p = plan(slug);
        return p.monthlyZar().multiply(BigDecimal.valueOf(attorneys))
                .add(STAFF_SEAT_MONTHLY_ZAR.multiply(BigDecimal.valueOf(staff)));
    }

    /** Negative values used to mean unlimited. Owlaw does not sell unlimited voice. */
    public static int capIncludedMinutes(Integer raw) {
        if (raw == null || raw < 0) return INCLUDED_PSTN_MINUTES;
        return raw;
    }
}
