import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function tenantCurrency() {
  if (typeof window === "undefined") return "USD";
  try {
    const raw = localStorage.getItem("ls_tenant");
    if (!raw) return "USD";
    const t = JSON.parse(raw) as { currency?: string; country?: string };
    if (t.currency) return t.currency;
    if (t.country === "ZA") return "ZAR";
    return "USD";
  } catch {
    return "USD";
  }
}

export function money(n: number | string | null | undefined, currency?: string) {
  const v = Number(n ?? 0);
  const ccy = currency || tenantCurrency();
  const locale = ccy === "ZAR" ? "en-ZA" : "en-US";
  return new Intl.NumberFormat(locale, { style: "currency", currency: ccy, maximumFractionDigits: 0 }).format(v);
}

export function moneyExact(n: number | string | null | undefined, currency?: string) {
  const v = Number(n ?? 0);
  const ccy = currency || tenantCurrency();
  const locale = ccy === "ZAR" ? "en-ZA" : "en-US";
  return new Intl.NumberFormat(locale, { style: "currency", currency: ccy }).format(v);
}

export function formatDate(value?: string | null) {
  if (!value) return "None";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export function formatDateTime(value?: string | null) {
  if (!value) return "None";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return d.toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

export function hoursFromMinutes(mins: number) {
  return (mins / 60).toFixed(1);
}
