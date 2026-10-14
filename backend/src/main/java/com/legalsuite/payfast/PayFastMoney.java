package com.legalsuite.payfast;

import com.legalsuite.common.ApiException;
import com.legalsuite.service.Pricing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Seat price is rands in {@link Pricing} and cents on the PayFast adhoc API. */
public final class PayFastMoney {
    /** Firm plan price per attorney. Kept as the default for checkouts without a plan. */
    public static final long LIGHT_SEAT_CENTS = Pricing.FIRM_MONTHLY_ZAR.movePointRight(2).longValueExact();
    public static final long STAFF_SEAT_CENTS = Pricing.STAFF_SEAT_MONTHLY_ZAR.movePointRight(2).longValueExact();
    public static final int MAX_SEATS = 100;

    private PayFastMoney() {}

    public static long seatsCents(int seats) {
        if (seats < 1 || seats > MAX_SEATS) {
            throw ApiException.badRequest("Seat count must be from 1 to " + MAX_SEATS);
        }
        return Math.multiplyExact((long) seats, LIGHT_SEAT_CENTS);
    }

    public static long planSeatCents(String planSlug) {
        return Pricing.plan(planSlug).monthlyZar().movePointRight(2).longValueExact();
    }

    /** Monthly subscription for attorneys on a plan plus staff seats. Solo is one attorney and no staff seats. */
    public static long subscriptionCents(String planSlug, int attorneys, int staff) {
        Pricing.PlanSpec plan = Pricing.plan(planSlug);
        if (attorneys < 1 || attorneys > MAX_SEATS) {
            throw ApiException.badRequest("Seat count must be from 1 to " + MAX_SEATS);
        }
        if (staff < 0 || staff > MAX_SEATS) {
            throw ApiException.badRequest("Staff seats must be from 0 to " + MAX_SEATS);
        }
        if (plan.maxAttorneys() != null && attorneys > plan.maxAttorneys()) {
            throw ApiException.badRequest(plan.name() + " is for one attorney. Choose Firm or Practice for more attorneys.");
        }
        if (!plan.staffSeats() && staff > 0) {
            throw ApiException.badRequest(plan.name() + " has no staff seats. Choose Firm or Practice to add support staff.");
        }
        return Math.addExact(
                Math.multiplyExact((long) attorneys, planSeatCents(plan.slug())),
                Math.multiplyExact((long) staff, STAFF_SEAT_CENTS));
    }

    /** PayFast checkout form amount, two decimal rands. 119900 cents is {@code 1199.00}. */
    public static String formatRands(long cents) {
        long abs = Math.abs(cents);
        String text = (abs / 100) + "." + String.format(Locale.US, "%02d", abs % 100);
        return cents < 0 ? "-" + text : text;
    }

    public static long parseRands(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing amount");
        }
        try {
            return new BigDecimal(raw.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Amount is not a rand value");
        }
    }

    public static long randsToCents(BigDecimal rands) {
        if (rands == null) return 0L;
        return rands.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
