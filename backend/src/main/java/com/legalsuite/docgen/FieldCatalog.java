package com.legalsuite.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every merge field a template can use, written {{name}}. Firm and attorney fields come from the firm's
 * branding and the signed-in user; matter fields are pre-filled from the chosen matter; any other name
 * becomes a fill-in field on the generate form. {{name:default}} prints the default (for example the
 * original blank "______") when the field is left empty.
 */
public final class FieldCatalog {
    public record Field(String key, String label, String group, String description) {}

    public static final List<Field> FIELDS = List.of(
            new Field("firm_logo", "Firm logo", "firm", "Uploaded logo image, placed where the field sits (top of the letterhead)."),
            new Field("firm_name", "Firm name", "firm", "Registered firm name, e.g. \"ABC Attorneys\"."),
            new Field("firm_address", "Firm address", "firm", "Physical office address."),
            new Field("firm_phone", "Firm telephone", "firm", "Office telephone."),
            new Field("firm_email", "Firm email", "firm", "Office email."),
            new Field("firm_ref", "Firm reference", "firm", "Reference suffix or initials used in \"Our Ref\"."),
            new Field("firm_website", "Firm website", "firm", "Firm website."),
            new Field("information_officer", "Information officer", "firm", "POPIA information officer."),
            new Field("trust_account_name", "Trust account name", "firm", "Trust account holder name."),
            new Field("trust_account_number", "Trust account number", "firm", "Trust account number."),
            new Field("bank_name", "Bank", "firm", "Bank of the trust account."),
            new Field("branch_name", "Branch name", "firm", "Branch name."),
            new Field("branch_code", "Branch code", "firm", "Branch code."),
            new Field("footer_text", "Footer text", "firm", "Footer line. Defaults to \"attorney name, attorney title\"."),
            new Field("attorney_name", "Attorney name", "attorney", "Attorney who signs or acts (full name), from the user profile."),
            new Field("attorney_title", "Attorney title", "attorney", "Attorney's title or qualification, from the user profile."),
            new Field("attorney_initials", "Attorney initials", "attorney", "Initials from the user profile."),
            new Field("attorney_email", "Attorney email", "attorney", "Attorney's email."),
            new Field("date", "Today's date", "date", "Today, e.g. 15 October 2026."),
            new Field("client_name", "Client name", "matter", "Client on the matter."),
            new Field("client_id_number", "Client ID number", "matter", "Client identity number (remembered on the matter once entered)."),
            new Field("client_email", "Client email", "matter", "Client email."),
            new Field("client_phone", "Client phone", "matter", "Client phone or mobile."),
            new Field("client_address", "Client address", "matter", "Client address."),
            new Field("matter_ref", "Matter reference", "matter", "Matter number."),
            new Field("matter_title", "Matter title", "matter", "Matter title."),
            new Field("court_name", "Court", "matter", "Court name."),
            new Field("court_case_number", "Court case number", "matter", "Court case number."),
            new Field("opposing_party", "Opposing party", "matter", "Opposing party."),
            new Field("accident_date", "Date of accident", "matter", "RAF matters: accident date from the matter."),
            new Field("deceased_name", "Deceased's full names", "matter", "Estates: remembered on the matter once entered."),
            new Field("date_of_death", "Date of death", "matter", "Estates: remembered on the matter once entered."),
            new Field("estate_number", "Estate number", "matter", "Estates: Master's reference, remembered on the matter."),
            new Field("file_number", "File number", "matter", "Firm file number in \"Our Ref\"; defaults to the matter number."));

    /** Filled from branding and the profile, never asked on the form. */
    public static final Set<String> SYSTEM = Set.of(
            "firm_logo", "firm_name", "firm_address", "firm_phone", "firm_email", "firm_ref", "firm_website",
            "information_officer", "trust_account_name", "trust_account_number", "bank_name", "branch_name",
            "branch_code", "footer_text", "attorney_name", "attorney_title", "attorney_initials", "attorney_email", "date");

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        FIELDS.forEach(f -> LABELS.put(f.key(), f.label()));
        LABELS.put("signed_at", "Signed at (place)");
        LABELS.put("signed_day", "Signed on day");
        LABELS.put("signed_month", "Signed in month");
        LABELS.put("signed_year", "Year (last two digits)");
        LABELS.put("letter_day", "Letter date: day");
        LABELS.put("letter_month", "Letter date: month");
        LABELS.put("letter_date", "Letter date");
        LABELS.put("represented_name", "Represented person's name");
        LABELS.put("represented_id_number", "Represented person's ID number");
        LABELS.put("client_relationship", "Client's relationship (e.g. spouse)");
        LABELS.put("saps_ar_number", "SAPS AR number");
        LABELS.put("your_ref", "Your reference");
    }

    private FieldCatalog() {}

    public static String label(String key) {
        String known = LABELS.get(key);
        if (known != null) return known;
        String words = key.replace('_', ' ').replace('.', ' ').trim();
        if (words.isEmpty()) return key;
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    public static String group(String key) {
        return FIELDS.stream().filter(f -> f.key().equals(key)).map(Field::group).findFirst()
                .orElse(SYSTEM.contains(key) ? "firm" : "fill");
    }
}
