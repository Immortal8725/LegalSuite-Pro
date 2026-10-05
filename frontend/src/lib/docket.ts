/** Labels for a computed clock. The wording follows the matter's jurisdiction. */
export function statutoryClockLabel(jurisdiction?: string | null, kind?: string | null) {
  const j = (jurisdiction || "").trim().toUpperCase();
  const za = j === "ZA";
  if (kind === "notice") return za ? "Organ-of-state notice" : j === "TX" ? "TTCA notice" : "Statutory notice";
  if (kind === "raf_lodge") return "Lodge RAF 1";
  if (kind === "raf_summons") return "RAF summons";
  if (kind === "ccma") return "CCMA referral";
  if (kind === "repose") return "Outer limit";
  if (kind === "inspection") return "L&D inspection";
  if (kind === "sol" || !kind) {
    if (za) return "Prescription";
    if (j === "TX") return "Texas clock";
    if (j) return `${j} clock`;
    return "Statutory clock";
  }
  if (za) return "Prescription";
  if (j === "TX") return "Texas clock";
  if (j) return `${j} clock`;
  return "Statutory clock";
}

export function daysUntil(iso?: string | null) {
  if (!iso) return null;
  const [y, m, d] = iso.slice(0, 10).split("-").map(Number);
  if (!y || !m || !d) return null;
  const target = Date.UTC(y, m - 1, d);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target - today) / 86_400_000);
}

export function daysLeftLabel(days: number | null | undefined) {
  if (days == null || Number.isNaN(days)) return null;
  if (days < 0) return `${Math.abs(days)} days overdue`;
  if (days === 0) return "Due today";
  return `${days} days left`;
}

export function todayIso() {
  const n = new Date();
  const m = String(n.getMonth() + 1).padStart(2, "0");
  const d = String(n.getDate()).padStart(2, "0");
  return `${n.getFullYear()}-${m}-${d}`;
}
