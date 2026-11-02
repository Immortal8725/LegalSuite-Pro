package com.legalsuite.litigation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;

/**
 * Court-day arithmetic for South African civil procedure.
 *
 * A court day is any day other than a Saturday, Sunday or public holiday (Uniform Rules r 1;
 * Magistrates' Courts Rules r 2). The first day is excluded and the last day included
 * (Interpretation Act 33 of 1957 s 4). Public holidays are those in the Public Holidays Act 36 of 1994
 * s 1 and Schedule 1; when one falls on a Sunday the Monday is a holiday (s 2(1)).
 * Days from 16 December to 15 January inclusive are not counted for pleadings and affidavits
 * (dies non) when a rule says so. Once-off holidays the President declares (for example election days)
 * are not known here. Attorney to review.
 */
public final class CourtDays {
    private CourtDays() {}

    private static final MonthDay[] FIXED = {
            MonthDay.of(1, 1), MonthDay.of(3, 21), MonthDay.of(4, 27), MonthDay.of(5, 1), MonthDay.of(6, 16),
            MonthDay.of(8, 9), MonthDay.of(9, 24), MonthDay.of(12, 16), MonthDay.of(12, 25), MonthDay.of(12, 26)};

    public static Set<LocalDate> publicHolidays(int year) {
        Set<LocalDate> out = new HashSet<>();
        for (MonthDay md : FIXED) {
            LocalDate d = md.atYear(year);
            out.add(d);
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) out.add(d.plusDays(1));
        }
        LocalDate easter = easterSunday(year);
        out.add(easter.minusDays(2)); // Good Friday
        out.add(easter.plusDays(1)); // Family Day
        return out;
    }

    public static boolean isPublicHoliday(LocalDate d) {
        return publicHolidays(d.getYear()).contains(d);
    }

    public static boolean isDiesNon(LocalDate d) {
        return (d.getMonthValue() == 12 && d.getDayOfMonth() >= 16) || (d.getMonthValue() == 1 && d.getDayOfMonth() <= 15);
    }

    public static boolean isCourtDay(LocalDate d, boolean skipDiesNon) {
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) return false;
        if (isPublicHoliday(d)) return false;
        return !(skipDiesNon && isDiesNon(d));
    }

    /** The date that is {@code n} court days after {@code from}, not counting {@code from} itself. */
    public static LocalDate addCourtDays(LocalDate from, int n, boolean skipDiesNon) {
        LocalDate d = from;
        int left = n;
        while (left > 0) {
            d = d.plusDays(1);
            if (isCourtDay(d, skipDiesNon)) left--;
        }
        return d;
    }

    /** Interpretation Act s 4: a period ending on a Sunday or public holiday runs to the next day that is neither. */
    public static LocalDate rollPastSundayOrHoliday(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY || isPublicHoliday(d)) d = d.plusDays(1);
        return d;
    }

    public static LocalDate nextCourtDayOnOrAfter(LocalDate d) {
        while (!isCourtDay(d, false)) d = d.plusDays(1);
        return d;
    }

    /** Due date for a rule period. Units: court_days, calendar_days, weeks, months, years, hours. */
    public static LocalDate due(LocalDate from, int amount, String unit, boolean skipDiesNon) {
        return switch (unit) {
            case "court_days" -> addCourtDays(from, amount, skipDiesNon);
            case "calendar_days" -> rollPastSundayOrHoliday(from.plusDays(amount));
            case "weeks" -> rollPastSundayOrHoliday(from.plusWeeks(amount));
            case "months" -> rollPastSundayOrHoliday(from.plusMonths(amount));
            case "years" -> rollPastSundayOrHoliday(from.plusYears(amount));
            // Criminal Procedure Act s 50(1)(d): 48 hours ending outside court hours or on a non-court day runs to the next court day.
            case "hours" -> nextCourtDayOnOrAfter(from.plusDays(Math.max(0, amount / 24)));
            default -> throw new IllegalArgumentException("Unknown period unit " + unit);
        };
    }

    /** Anonymous Gregorian algorithm. */
    static LocalDate easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }
}
