package com.legalsuite.directory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Closed lists for the Find a Lawyer directory: practice areas, places, languages and consultation options. */
public final class DirectoryCatalog {
    private DirectoryCatalog() {}

    public record Option(String slug, String label) {}

    public record City(String slug, String name, String province, double lat, double lng) {}

    public static final List<Option> PRACTICE_AREAS = List.of(
            new Option("divorce-and-family", "Divorce and family"),
            new Option("raf-claims", "RAF claims"),
            new Option("personal-injury", "Personal injury"),
            new Option("conveyancing-and-property", "Conveyancing and property"),
            new Option("criminal", "Criminal"),
            new Option("labour-and-employment", "Labour and employment"),
            new Option("wills-and-estates", "Wills and estates"),
            new Option("commercial-and-contracts", "Commercial and contracts"),
            new Option("debt-collection", "Debt collection"),
            new Option("immigration", "Immigration"),
            new Option("notarial", "Notarial"),
            new Option("litigation", "Litigation"),
            new Option("medical-negligence", "Medical negligence"),
            new Option("tax", "Tax"));

    public static final List<Option> PROVINCES = List.of(
            new Option("eastern-cape", "Eastern Cape"),
            new Option("free-state", "Free State"),
            new Option("gauteng", "Gauteng"),
            new Option("kwazulu-natal", "KwaZulu-Natal"),
            new Option("limpopo", "Limpopo"),
            new Option("mpumalanga", "Mpumalanga"),
            new Option("north-west", "North West"),
            new Option("northern-cape", "Northern Cape"),
            new Option("western-cape", "Western Cape"));

    public static final List<City> CITIES = List.of(
            new City("johannesburg", "Johannesburg", "gauteng", -26.2041, 28.0473),
            new City("sandton", "Sandton", "gauteng", -26.1076, 28.0567),
            new City("pretoria", "Pretoria", "gauteng", -25.7479, 28.2293),
            new City("centurion", "Centurion", "gauteng", -25.8603, 28.1894),
            new City("midrand", "Midrand", "gauteng", -25.9992, 28.1263),
            new City("soweto", "Soweto", "gauteng", -26.2485, 27.8540),
            new City("ekurhuleni", "Ekurhuleni", "gauteng", -26.1700, 28.3200),
            new City("vereeniging", "Vereeniging", "gauteng", -26.6731, 27.9261),
            new City("cape-town", "Cape Town", "western-cape", -33.9249, 18.4241),
            new City("stellenbosch", "Stellenbosch", "western-cape", -33.9321, 18.8602),
            new City("paarl", "Paarl", "western-cape", -33.7342, 18.9621),
            new City("george", "George", "western-cape", -33.9630, 22.4617),
            new City("durban", "Durban", "kwazulu-natal", -29.8587, 31.0218),
            new City("pietermaritzburg", "Pietermaritzburg", "kwazulu-natal", -29.6006, 30.3794),
            new City("umhlanga", "Umhlanga", "kwazulu-natal", -29.7270, 31.0850),
            new City("richards-bay", "Richards Bay", "kwazulu-natal", -28.7807, 32.0383),
            new City("newcastle", "Newcastle", "kwazulu-natal", -27.7577, 29.9318),
            new City("gqeberha", "Gqeberha", "eastern-cape", -33.9608, 25.6022),
            new City("east-london", "East London", "eastern-cape", -33.0153, 27.9116),
            new City("mthatha", "Mthatha", "eastern-cape", -31.5889, 28.7844),
            new City("bloemfontein", "Bloemfontein", "free-state", -29.0852, 26.1596),
            new City("welkom", "Welkom", "free-state", -27.9774, 26.7351),
            new City("polokwane", "Polokwane", "limpopo", -23.9045, 29.4689),
            new City("thohoyandou", "Thohoyandou", "limpopo", -22.9456, 30.4850),
            new City("mbombela", "Mbombela", "mpumalanga", -25.4658, 30.9853),
            new City("emalahleni", "eMalahleni", "mpumalanga", -25.8713, 29.2332),
            new City("mahikeng", "Mahikeng", "north-west", -25.8560, 25.6403),
            new City("rustenburg", "Rustenburg", "north-west", -25.6676, 27.2421),
            new City("potchefstroom", "Potchefstroom", "north-west", -26.7145, 27.0970),
            new City("kimberley", "Kimberley", "northern-cape", -28.7282, 24.7499),
            new City("upington", "Upington", "northern-cape", -28.4478, 21.2561));

    public static final List<Option> LANGUAGES = List.of(
            new Option("english", "English"),
            new Option("isizulu", "isiZulu"),
            new Option("isixhosa", "isiXhosa"),
            new Option("afrikaans", "Afrikaans"),
            new Option("sesotho", "Sesotho"),
            new Option("setswana", "Setswana"),
            new Option("sepedi", "Sepedi"),
            new Option("xitsonga", "Xitsonga"),
            new Option("tshivenda", "Tshivenda"),
            new Option("isindebele", "isiNdebele"),
            new Option("siswati", "siSwati"),
            new Option("sasl", "South African Sign Language"));

    public static final List<Option> CONSULT_OPTIONS = List.of(
            new Option("free-first", "Free first consultation"),
            new Option("fixed-fee", "Fixed-fee first consultation"),
            new Option("online", "Online video consultation"),
            new Option("in-person", "In-person consultation"),
            new Option("after-hours", "After-hours appointments"),
            new Option("legal-insurance", "Legal insurance accepted"),
            new Option("contingency", "Contingency fees (no win, no fee)"));

    public static final List<Option> RESPONSE_TIMES = List.of(
            new Option("same-day", "Usually replies the same day"),
            new Option("one-day", "Usually replies within 1 working day"),
            new Option("two-days", "Usually replies within 2 working days"));

    public static Optional<Option> area(String slug) {
        return find(PRACTICE_AREAS, slug);
    }

    public static Optional<Option> province(String slug) {
        return find(PROVINCES, slug);
    }

    public static Optional<Option> language(String slug) {
        return find(LANGUAGES, slug);
    }

    public static Optional<Option> consult(String slug) {
        return find(CONSULT_OPTIONS, slug);
    }

    public static Optional<Option> responseTime(String slug) {
        return find(RESPONSE_TIMES, slug);
    }

    public static Optional<City> city(String slug) {
        String s = norm(slug);
        return CITIES.stream().filter(c -> c.slug().equals(s)).findFirst();
    }

    public static String label(List<Option> list, String slug) {
        return find(list, slug).map(Option::label).orElse(slug);
    }

    private static Optional<Option> find(List<Option> list, String slug) {
        String s = norm(slug);
        return list.stream().filter(o -> o.slug().equals(s)).findFirst();
    }

    static String norm(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Great-circle distance in km. */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    public static Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("practiceAreas", PRACTICE_AREAS);
        m.put("provinces", PROVINCES);
        m.put("cities", CITIES.stream().map(c -> Map.of("slug", c.slug(), "label", c.name(), "province", c.province())).toList());
        m.put("languages", LANGUAGES);
        m.put("consultOptions", CONSULT_OPTIONS);
        m.put("responseTimes", RESPONSE_TIMES);
        return m;
    }
}
