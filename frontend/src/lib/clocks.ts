import type { DocketPreview } from "@/lib/types";

/** Label a computed clock in the firm's jurisdiction. South African files must not say Texas. */
export function clockCaption(docket?: DocketPreview | null, country?: string | null) {
  const za = docket?.jurisdiction === "ZA" || (!docket?.jurisdiction && country === "ZA");
  const kind = docket?.controllingKind;
  if (kind === "notice") return za ? "Act 40 notice" : "Texas notice";
  if (kind === "raf_lodge") return "RAF lodge";
  if (kind === "raf_summons") return "RAF summons";
  if (kind === "ccma") return "CCMA referral";
  if (kind === "inspection") return "Inspection";
  if (kind === "sol") return za ? "Prescription" : "Texas limitations";
  return za ? "South African clock" : "Texas clock";
}
