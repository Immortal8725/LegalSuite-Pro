package com.legalsuite.service;

import com.legalsuite.domain.LegalCase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * South African prescription, RAF, organ-of-state notice, and CCMA clocks.
 * Citations are to the Acts. This is a docket engine, not advice.
 */
public final class SouthAfricanDocketRules {
    public static final String JURISDICTION = "ZA";

    private SouthAfricanDocketRules() {}

    public static TexasDocketRules.Result compute(TexasDocketRules.Facts facts) {
        TexasDocketRules.Facts f = facts == null ? new TexasDocketRules.Facts() : facts;
        LocalDate today = LocalDate.now();
        List<String> caveats = new ArrayList<>();
        String track = track(f);
        boolean raf = "raf".equals(track) || looksRaf(f);
        boolean organ = !raf && (f.governmentalDefendant || looksOrganOfState(f.opposingParty));
        if (organ && !f.governmentalDefendant) {
            caveats.add("Treated as an organ of state from the opposing-party name. Act 40 of 2002 notice may apply.");
        }
        LocalDate accrual = f.accrualDate != null ? f.accrualDate : TexasDocketRules.inferAccrual(f.description, today);
        String assumed = f.accrualDate == null
                ? "No incident date on file — accrued " + accrual + " from the intake narrative or today."
                : "";
        if (f.accrualDate == null) caveats.add(assumed);
        boolean hitAndRun = f.hitAndRun || looksHitAndRun(f);
        LocalDate knowledge = f.discoveryDate != null && f.discoveryDate.isAfter(accrual) ? f.discoveryDate : accrual;
        if (knowledge.isAfter(accrual)) {
            caveats.add("Prescription Act s 12(3): clock runs from knowledge of the debtor and the facts, not only the incident date.");
        }

        List<TexasDocketRules.Clock> clocks = new ArrayList<>();
        switch (track) {
            case "labour" -> labour(accrual, assumed, f, clocks);
            case "raf" -> raf(f, accrual, knowledge, hitAndRun, assumed, clocks, caveats);
            case "medical_negligence" -> delict(f, knowledge, assumed, clocks, caveats, "medical_negligence");
            case "estate" -> estate(f, accrual, assumed, clocks, caveats);
            case "organ_of_state" -> organOfState(f, accrual, knowledge, assumed, clocks, caveats);
            case "personal_injury" -> {
                if (organ) organOfState(f, accrual, knowledge, assumed, clocks, caveats);
                else delict(f, knowledge, assumed, clocks, caveats, "delict");
            }
            case "contract" -> contract(f, accrual, assumed, clocks);
            default -> {
                if (organ) organOfState(f, accrual, knowledge, assumed, clocks, caveats);
                else {
                    clocks.add(clock("sol", "za.debt.11d", "Prescription Act 68 of 1969 s 11(d)",
                            "Prescription (unclassified debt)", knowledge.plusYears(3),
                            "Three years for any other debt, from when the debt is due and the creditor has the knowledge required by s 12(3).",
                            assumed));
                    caveats.add("Practice area did not match RAF, delict, labour, contract, or deceased estate. Confirm the correct prescription period.");
                }
            }
        }

        clocks.sort(Comparator.comparing(TexasDocketRules.Clock::date));
        return new TexasDocketRules.Result(JURISDICTION, track, clocks, caveats);
    }

    public static void stamp(LegalCase c, TexasDocketRules.Result result) {
        TexasDocketRules.stamp(c, result);
    }

    static String track(TexasDocketRules.Facts f) {
        String blob = blob(f);
        if (contains(blob, "ccma", "unfair dismiss", "retrench", "labour court", "ulp", "unfair labour")) {
            return "labour";
        }
        if (looksRaf(f) || contains(blob, "raf", "road accident fund", "hit and run", "unidentified driver")) {
            return "raf";
        }
        if (contains(blob, "malprac", "med mal", "medical neglig", "hospital neglig", "misdiagnosis")) {
            return "medical_negligence";
        }
        if (contains(blob, "estate", "deceased", "master of the high court", "will contest", "executor", "l&d")) {
            return "estate";
        }
        if (looksOrganOfState(f.opposingParty) || f.governmentalDefendant) {
            if (contains(blob, "injur", "accident", "collision", "pothole", "assault", "police", "wrongful arrest",
                    "negligence", "tort", "delict")) {
                return "organ_of_state";
            }
        }
        if (contains(blob, "injur", "accident", "collision", "crash", "negligence", "delict", "vehicle", "slip",
                "taxi", "bakkie", "wrongful death", "personal injury")) {
            return "personal_injury";
        }
        if (contains(blob, "contract", "breach", "agreement", "debt", "invoice", "sale of goods")) return "contract";
        if (looksOrganOfState(f.opposingParty) || f.governmentalDefendant) return "organ_of_state";
        return "other";
    }

    private static void raf(
            TexasDocketRules.Facts f,
            LocalDate accrual,
            LocalDate knowledge,
            boolean hitAndRun,
            String assumed,
            List<TexasDocketRules.Clock> clocks,
            List<String> caveats) {
        int lodgeYears = hitAndRun ? 2 : 3;
        LocalDate lodgeStart = knowledge;
        String lodgeReason = hitAndRun
                ? "Hit-and-run / unidentified vehicle: lodge with the Fund within two years of the cause of action."
                : "Identified vehicle: the right to claim from the Fund prescribes three years from the cause of action (RAF Act s 23(1)).";
        if (f.dateOfBirth != null) {
            LocalDate majority = f.dateOfBirth.plusYears(18);
            if (accrual.isBefore(majority)) {
                lodgeStart = majority;
                lodgeReason = "RAF Act s 23(2): prescription does not run against a minor. The lodge period runs from the 18th birthday.";
                caveats.add("Minor RAF clock: s 23(2) suspends prescription until majority.");
            }
        }
        LocalDate lodge = lodgeStart.plusYears(lodgeYears);
        clocks.add(clock("raf_lodge", hitAndRun ? "za.raf.23.hitrun" : "za.raf.23.1",
                "Road Accident Fund Act 56 of 1996 s 23",
                hitAndRun ? "Lodge RAF claim (hit-and-run)" : "Lodge RAF 1 claim",
                lodge, lodgeReason, assumed));
        if (f.rafClaimLodged) {
            LocalDate summons = accrual.plusYears(5);
            clocks.add(clock("raf_summons", "za.raf.23.3", "Road Accident Fund Act 56 of 1996 s 23(3)",
                    "Issue summons (lodged RAF claim)", summons,
                    "Once lodged under s 17, the claim may not prescribe before five years from the cause of action. Issue in time.",
                    assumed));
        } else {
            caveats.add("RAF 1 not marked lodged — the five-year summons protection in s 23(3) does not start until the claim is lodged.");
        }
    }

    private static void delict(
            TexasDocketRules.Facts f,
            LocalDate knowledge,
            String assumed,
            List<TexasDocketRules.Clock> clocks,
            List<String> caveats,
            String flavour) {
        LocalDate ordinary = knowledge.plusYears(3);
        String reason = "Prescription Act s 11(d) read with s 12(3): three years from knowledge of the debtor and the facts.";
        if (f.dateOfBirth != null) {
            LocalDate majority = f.dateOfBirth.plusYears(18);
            if (knowledge.isBefore(majority)) {
                LocalDate earliest = majority.plusYears(1);
                if (ordinary.isBefore(earliest)) {
                    ordinary = earliest;
                    reason = "Prescription Act s 13: the period is not completed until one year after majority (18).";
                    caveats.add("Minority delay (s 13) applied from date of birth.");
                }
            }
        }
        String title = "medical_negligence".equals(flavour) ? "Medical-negligence prescription" : "Delict prescription";
        clocks.add(clock("sol", "medical_negligence".equals(flavour) ? "za.medneg.11d" : "za.delict.11d",
                "Prescription Act 68 of 1969 ss 11(d), 12(3)",
                title, ordinary, reason, assumed));
    }

    private static void organOfState(
            TexasDocketRules.Facts f,
            LocalDate accrual,
            LocalDate knowledge,
            String assumed,
            List<TexasDocketRules.Clock> clocks,
            List<String> caveats) {
        if (!f.noticeServed) {
            clocks.add(clock("notice", "za.act40.s3", "Institution of Legal Proceedings Against Certain Organs of State Act 40 of 2002 s 3",
                    "Organ-of-state notice", knowledge.plusMonths(6),
                    "Written notice of intended legal proceedings is generally due within six months from when the debt became due. Missing it requires condonation before you issue.",
                    assumed));
        } else {
            caveats.add("Act 40 s 3 notice marked served — notice clock cleared. Prescription still runs.");
        }
        delict(f, knowledge, assumed, clocks, caveats, "delict");
        if (accrual != null && !accrual.equals(knowledge)) {
            caveats.add("Notice and prescription both use the knowledge date where s 12(3) / Act 40 s 3(3) knowledge is later than the incident.");
        }
    }

    private static void labour(LocalDate accrual, String assumed, TexasDocketRules.Facts f, List<TexasDocketRules.Clock> clocks) {
        String blob = blob(f);
        if (contains(blob, "discriminat", "eea", "equal")) {
            clocks.add(clock("ccma", "za.eea.10", "Employment Equity Act 55 of 1998 s 10",
                    "Refer discrimination dispute", accrual.plusMonths(6),
                    "Unfair-discrimination disputes are generally referred within six months.",
                    assumed));
            return;
        }
        if (contains(blob, "unfair labour", "ulp", "demotion", "promotion")) {
            clocks.add(clock("ccma", "za.lra.191.ulp", "Labour Relations Act 66 of 1995 s 191(1)(b)(ii)",
                    "Refer unfair labour practice", accrual.plusDays(90),
                    "Unfair labour practice: refer to the CCMA within 90 days of the act or of when the employee became aware of it.",
                    assumed));
            return;
        }
        clocks.add(clock("ccma", "za.lra.191.dismiss", "Labour Relations Act 66 of 1995 s 191(1)(b)(i)",
                "Refer unfair dismissal", accrual.plusDays(30),
                "Unfair dismissal: refer to the CCMA within 30 days of the dismissal. Late referrals need condonation.",
                assumed));
    }

    private static void contract(TexasDocketRules.Facts f, LocalDate accrual, String assumed, List<TexasDocketRules.Clock> clocks) {
        String blob = blob(f);
        if (contains(blob, "bill of exchange", "cheque", "promissory note")) {
            clocks.add(clock("sol", "za.contract.11c", "Prescription Act 68 of 1969 s 11(c)",
                    "Negotiable-instrument prescription", accrual.plusYears(6),
                    "Six years for bills of exchange and other negotiable instruments under s 11(c).",
                    assumed));
            return;
        }
        clocks.add(clock("sol", "za.contract.11d", "Prescription Act 68 of 1969 s 11(d)",
                "Contract prescription", accrual.plusYears(3),
                "Three years for ordinary contractual debts. South Africa is not a four-year limitations state.",
                assumed));
    }

    private static void estate(
            TexasDocketRules.Facts f, LocalDate accrual, String assumed, List<TexasDocketRules.Clock> clocks, List<String> caveats) {
        LocalDate letters = f.probateOpened != null ? f.probateOpened : accrual;
        if (f.probateOpened == null) {
            caveats.add("No letters of executorship date — treating " + letters + " as the appointment date.");
        }
        String note = f.probateOpened == null ? "Assumed letters " + letters + "." : assumed;
        clocks.add(clock("notice", "za.estate.s29", "Administration of Estates Act 66 of 1965 s 29",
                "Estate creditor advertisement", letters.plusMonths(3),
                "Executor must advertise; creditors should lodge claims before the account is advertised. Treat three months from letters as the practical window.",
                note));
        clocks.add(clock("inspection", "za.estate.ld", "Administration of Estates Act 66 of 1965 s 35",
                "Lodge objection to L&D account", letters.plusDays(21),
                "The liquidation and distribution account lies for inspection; objections are generally due within 21 days of advertisement. Confirm the actual advertisement date.",
                note));
        clocks.add(clock("sol", "za.estate.11d", "Prescription Act 68 of 1969 s 11(d)",
                "Estate debt prescription", letters.plusYears(3),
                "Ordinary debts of the estate still prescribe in three years unless interrupted.",
                note));
    }

    static boolean looksRaf(TexasDocketRules.Facts f) {
        String blob = blob(f) + " " + (f.opposingParty == null ? "" : f.opposingParty.toLowerCase(Locale.ROOT));
        return contains(blob, "road accident fund", " raf", "raf ", "unidentified vehicle");
    }

    static boolean looksHitAndRun(TexasDocketRules.Facts f) {
        String blob = blob(f);
        return contains(blob, "hit and run", "hit-and-run", "unidentified driver", "unknown driver", "unidentified vehicle");
    }

    static boolean looksOrganOfState(String opposing) {
        if (opposing == null || opposing.isBlank()) return false;
        String n = opposing.toLowerCase(Locale.ROOT);
        if (contains(n, "road accident fund")) return false;
        return contains(n, "city of", "municip", "metro", "saps", "south african police", "department of",
                "province", "gauteng", "western cape", "kwazulu", "transnet", "prasa", "sanral", "eskom",
                "home affairs", "correctional", "national prosecuting", "state hospital", "department of health",
                "minister of", "mec for");
    }

    private static TexasDocketRules.Clock clock(
            String kind, String ruleId, String citation, String title, LocalDate date, String reason, String assumption) {
        return TexasDocketRules.clock(kind, ruleId, citation, title, date, reason, assumption);
    }

    private static String blob(TexasDocketRules.Facts f) {
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
