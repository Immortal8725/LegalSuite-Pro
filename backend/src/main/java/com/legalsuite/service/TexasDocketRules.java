package com.legalsuite.service;

import com.legalsuite.domain.LegalCase;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texas-first limitation and notice clocks. Citations are to the Civil Practice
 * and Remedies Code and the Estates Code. This is a docket engine, not advice.
 */
public final class TexasDocketRules {
    public static final String JURISDICTION = "TX";

    private static final Pattern DAYS_AGO = Pattern.compile("(\\d+)\\s+days?\\s+ago", Pattern.CASE_INSENSITIVE);

    private TexasDocketRules() {}

    public static class Facts {
        public String practiceArea;
        public String caseType;
        public String description;
        public String opposingParty;
        public LocalDate accrualDate;
        public LocalDate discoveryDate;
        public LocalDate dateOfBirth;
        public LocalDate probateOpened;
        public boolean governmentalDefendant;
        public boolean rafClaimLodged;
        public LocalDate rafLodgedDate;
        public boolean noticeServed;
        public boolean hitAndRun;

        public static Facts from(Map<String, Object> body) {
            Facts f = new Facts();
            if (body == null) return f;
            f.practiceArea = str(body, "practiceArea");
            f.caseType = str(body, "caseType");
            f.description = str(body, "description");
            f.opposingParty = str(body, "opposingParty");
            f.accrualDate = parseDate(body.get("accrualDate"));
            f.discoveryDate = parseDate(body.get("discoveryDate"));
            f.dateOfBirth = parseDate(body.get("dateOfBirth"));
            f.probateOpened = parseDate(body.get("probateOpened"));
            f.governmentalDefendant = bool(body.get("governmentalDefendant"));
            f.rafClaimLodged = bool(body.get("rafClaimLodged"));
            f.rafLodgedDate = parseDate(body.get("rafLodgedDate"));
            f.noticeServed = bool(body.get("noticeServed"));
            f.hitAndRun = bool(body.get("hitAndRun"));
            return f;
        }
    }

    public record Clock(
            String kind,
            String ruleId,
            String citation,
            String title,
            LocalDate date,
            String reason,
            String assumption) {
        public Map<String, Object> asMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("kind", kind);
            m.put("ruleId", ruleId);
            m.put("citation", citation);
            m.put("title", title);
            m.put("date", date == null ? null : date.toString());
            m.put("reason", reason);
            m.put("assumption", assumption == null ? "" : assumption);
            return m;
        }
    }

    public record Result(String jurisdiction, String track, List<Clock> clocks, List<String> caveats) {
        public LocalDate solDate() {
            return clocks.stream()
                    .filter(c -> "sol".equals(c.kind) || "raf_lodge".equals(c.kind) || "ccma".equals(c.kind))
                    .map(Clock::date)
                    .min(Comparator.naturalOrder())
                    .orElse(controlling() == null ? null : controlling().date);
        }

        public Clock controlling() {
            return clocks.stream().min(Comparator.comparing(Clock::date)).orElse(null);
        }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("jurisdiction", jurisdiction);
            m.put("track", track);
            Clock ctrl = controlling();
            m.put("solDate", solDate());
            m.put("controllingDate", ctrl == null ? null : ctrl.date());
            m.put("controllingKind", ctrl == null ? null : ctrl.kind());
            m.put("controllingCitation", ctrl == null ? null : ctrl.citation());
            m.put("clocks", clocks.stream().map(Clock::asMap).toList());
            m.put("caveats", caveats);
            m.put("disclaimer", "ZA".equals(jurisdiction)
                    ? "South African docket clocks from filed facts. Confirm interruptions, condonation, RAF lodging, and the latest LPC practice notes before you rely on a date. The attorney remains responsible. This is not legal advice."
                    : "Texas docket clocks from filed facts. Confirm exceptions, tolling, and local rules before you rely on a date. The attorney remains responsible. This is not legal advice.");
            return m;
        }
    }

    public static Result compute(Facts facts) {
        Facts f = facts == null ? new Facts() : facts;
        LocalDate today = LocalDate.now();
        List<String> caveats = new ArrayList<>();
        String track = track(f);
        boolean gov = f.governmentalDefendant || looksGovernmental(f.opposingParty);
        LocalDate accrual = f.accrualDate != null ? f.accrualDate : inferAccrual(f.description, today);
        String assumedAccrual = f.accrualDate == null
                ? "No incident date on file. Accrued " + accrual + " from the intake narrative or today."
                : "";
        if (f.accrualDate == null) caveats.add(assumedAccrual);
        if (gov && !f.governmentalDefendant) {
            caveats.add("Treated as a governmental defendant from the opposing-party name (TTCA notice may apply).");
        }

        List<Clock> clocks = new ArrayList<>();
        switch (track) {
            case "medical_malpractice" -> medMal(f, accrual, assumedAccrual, clocks, caveats);
            case "estate" -> estate(f, accrual, assumedAccrual, clocks, caveats);
            case "contract" -> contract(accrual, assumedAccrual, clocks);
            case "personal_injury" -> personalInjury(f, accrual, gov, assumedAccrual, clocks, caveats);
            default -> {
                clocks.add(clock("sol", "tx.default.16.003", "Tex. Civ. Prac. & Rem. Code § 16.003",
                        "Limitations (unclassified)", accrual.plusYears(2),
                        "Unclassified Texas civil claim. Two years is the default personal-injury / tort clock until the chapter is confirmed.",
                        assumedAccrual));
                caveats.add("Practice area did not match PI, med-mal, contract, or estate. Confirm the correct limitations chapter.");
            }
        }

        clocks.sort(Comparator.comparing(Clock::date));
        return new Result("TX", track, clocks, caveats);
    }

    public static void stamp(LegalCase c, Result result) {
        if (c == null || result == null) return;
        Clock sol = result.clocks().stream().filter(x -> "sol".equals(x.kind)).findFirst().orElse(result.controlling());
        if (sol != null) {
            c.setStatuteOfLimitations(sol.date());
            c.setSolRuleId(sol.ruleId());
            c.setSolCitation(sol.citation());
            c.setSolReason(sol.reason());
        }
        Clock ctrl = result.controlling();
        if (ctrl != null) {
            c.setControllingKind(ctrl.kind());
        }
        c.setDocketTrack(result.track());
        c.setDocketClocksJson(com.legalsuite.common.JsonLists.toJson(result.clocks().stream().map(Clock::asMap).toList()));
    }

    public static Facts factsFromCase(LegalCase c) {
        Facts f = new Facts();
        if (c == null) return f;
        f.practiceArea = c.getPracticeArea();
        f.caseType = c.getCaseType();
        f.description = c.getDescription();
        f.opposingParty = c.getOpposingParty();
        f.accrualDate = c.getAccrualDate();
        f.discoveryDate = c.getDiscoveryDate();
        f.dateOfBirth = c.getPlaintiffDob();
        f.probateOpened = c.getProbateOpened();
        f.governmentalDefendant = c.isGovernmentalDefendant();
        f.rafClaimLodged = c.isRafClaimLodged();
        f.rafLodgedDate = c.getRafLodgedDate();
        f.noticeServed = c.isNoticeServed();
        f.hitAndRun = c.isHitAndRun();
        return f;
    }

    static String track(Facts f) {
        String blob = blob(f);
        if (contains(blob, "malprac", "med mal", "medical malpractice", "health care liability")) return "medical_malpractice";
        if (contains(blob, "estate", "probate", "will contest", "heir", "letters testamentary")) return "estate";
        if (contains(blob, "injur", "accident", "collision", "crash", "negligence", "tort", "vehicle", "slip",
                "transit", "auto", "wrongful death", "personal injury")) {
            return "personal_injury";
        }
        if (contains(blob, "contract", "breach", "agreement", "debt", "invoice")) return "contract";
        if (contains(blob, "litigation")) return "personal_injury";
        return "other";
    }

    private static void personalInjury(
            Facts f, LocalDate accrual, boolean gov, String assumed, List<Clock> clocks, List<String> caveats) {
        LocalDate solStart = accrual;
        String reason = "Two years from the day the cause of action accrues.";
        if (f.discoveryDate != null && f.discoveryDate.isAfter(accrual)) {
            solStart = f.discoveryDate;
            reason = "Two years from discovery. The discovery rule is fact-specific. Confirm it actually applies.";
            caveats.add("Discovery-rule clock used because a discovery date is later than the incident date.");
        }
        LocalDate sol = solStart.plusYears(2);
        if (f.dateOfBirth != null) {
            LocalDate eighteenth = f.dateOfBirth.plusYears(18);
            if (accrual.isBefore(eighteenth)) {
                sol = eighteenth.plusYears(2);
                reason = "Minority tolls limitations until the 18th birthday; the two-year period then runs. Tex. Civ. Prac. & Rem. Code § 16.001.";
                clocks.add(clock("sol", "tx.pi.minor.16.001", "Tex. Civ. Prac. & Rem. Code §§ 16.001, 16.003",
                        "SOL (tolled for minority)", sol, reason, assumed));
            } else {
                clocks.add(clock("sol", "tx.pi.16.003", "Tex. Civ. Prac. & Rem. Code § 16.003",
                        "Statute of limitations", sol, reason, assumed));
            }
        } else {
            clocks.add(clock("sol", "tx.pi.16.003", "Tex. Civ. Prac. & Rem. Code § 16.003",
                    "Statute of limitations", sol, reason, assumed));
        }
        if (gov) {
            clocks.add(clock("notice", "tx.ttca.101.101", "Tex. Civ. Prac. & Rem. Code § 101.101",
                    "TTCA governmental notice", accrual.plusMonths(6),
                    "Written notice to a governmental unit is generally due within six months of the incident. Missing it can kill the claim before limitations runs.",
                    assumed));
        }
    }

    private static void medMal(Facts f, LocalDate accrual, String assumed, List<Clock> clocks, List<String> caveats) {
        LocalDate sol = accrual.plusYears(2);
        String reason = "Health-care liability: two years from the occurrence or hospitalization.";
        if (f.dateOfBirth != null && accrual.isBefore(f.dateOfBirth.plusYears(12))) {
            sol = f.dateOfBirth.plusYears(14);
            reason = "If the patient was under 12 at occurrence, the claim must be filed by the 14th birthday. § 74.251(b).";
            caveats.add("Minor medical-malpractice clock (age 14) applied from date of birth.");
        }
        clocks.add(clock("sol", "tx.medmal.74.251", "Tex. Civ. Prac. & Rem. Code § 74.251",
                "Medical-malpractice limitations", sol, reason, assumed));
        clocks.add(clock("repose", "tx.medmal.74.251c", "Tex. Civ. Prac. & Rem. Code § 74.251(c)",
                "Medical-malpractice repose", accrual.plusYears(10),
                "Ten-year statute of repose from the occurrence, with narrow exceptions.",
                assumed));
    }

    private static void contract(LocalDate accrual, String assumed, List<Clock> clocks) {
        clocks.add(clock("sol", "tx.contract.16.004", "Tex. Civ. Prac. & Rem. Code § 16.004",
                "Contract limitations", accrual.plusYears(4),
                "Four years for debt and most written-contract actions.",
                assumed));
    }

    private static void estate(Facts f, LocalDate accrual, String assumed, List<Clock> clocks, List<String> caveats) {
        LocalDate letters = f.probateOpened != null ? f.probateOpened : accrual;
        if (f.probateOpened == null) {
            caveats.add("No probate-opened date. Treating " + letters + " as the date letters issued.");
        }
        String lettersNote = f.probateOpened == null
                ? "Assumed letters issued " + letters + "."
                : assumed;
        clocks.add(clock("notice", "tx.estate.creditor", "Tex. Estates Code § 355.001 et seq.",
                "Estate creditor claim", letters.plusMonths(4),
                "Unsecured claims are generally due within four months after letters testamentary or of administration issue.",
                lettersNote));
        clocks.add(clock("sol", "tx.estate.256.204", "Tex. Estates Code § 256.204",
                "Will contest", letters.plusYears(2),
                "A will contest after probate is generally barred two years from the date the will is admitted.",
                lettersNote));
    }

    static Clock clock(
            String kind, String ruleId, String citation, String title, LocalDate date, String reason, String assumption) {
        return new Clock(kind, ruleId, citation, title, date, reason, assumption);
    }

    static boolean looksGovernmental(String opposing) {
        if (opposing == null || opposing.isBlank()) return false;
        String n = opposing.toLowerCase(Locale.ROOT);
        return contains(n, "transit", "authority", "city of", "county", "isd", "independent school",
                "university", "dps", "police", "municip", "state of texas", "metro");
    }

    static LocalDate inferAccrual(String description, LocalDate fallback) {
        if (description == null || description.isBlank()) return fallback;
        String d = description.toLowerCase(Locale.ROOT);
        if (d.contains("yesterday")) return fallback.minusDays(1);
        if (d.contains("today")) return fallback;
        Matcher m = DAYS_AGO.matcher(d);
        if (m.find()) {
            return fallback.minusDays(Long.parseLong(m.group(1)));
        }
        return fallback;
    }

    public static LocalDate parseDate(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        if (s.length() >= 10) s = s.substring(0, 10);
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static boolean bool(Object raw) {
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String blob(Facts f) {
        return ((f.practiceArea == null ? "" : f.practiceArea) + " "
                + (f.caseType == null ? "" : f.caseType) + " "
                + (f.description == null ? "" : f.description)).toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String blob, String... needles) {
        for (String n : needles) {
            if (blob.contains(n)) return true;
        }
        return false;
    }
}
