/**
 * AdSense for the public blog only (/blog and /blog/<slug>). Everything is off until
 * NEXT_PUBLIC_ADSENSE_CLIENT is set (for example ca-pub-1234567890123456). Slot ids come from
 * NEXT_PUBLIC_ADSENSE_SLOT_POST (inside a post, below the article) and NEXT_PUBLIC_ADSENSE_SLOT_LIST
 * (the blog list). A slot without an id renders nothing.
 */
const CLIENT_RE = /^ca-pub-\d{10,20}$/;

export function adsenseClient(): string | null {
  const c = (process.env.NEXT_PUBLIC_ADSENSE_CLIENT || "").trim();
  return CLIENT_RE.test(c) ? c : null;
}

/** The publisher id for ads.txt: "pub-…" from "ca-pub-…". */
export function adsensePublisherId(): string | null {
  const c = adsenseClient();
  return c ? c.replace(/^ca-/, "") : null;
}

export type AdSlotName = "post" | "list";

export function adsenseSlot(name: AdSlotName): string | null {
  const raw = name === "post" ? process.env.NEXT_PUBLIC_ADSENSE_SLOT_POST : process.env.NEXT_PUBLIC_ADSENSE_SLOT_LIST;
  const s = (raw || "").trim();
  return /^\d{6,20}$/.test(s) ? s : null;
}

export const ADS_CONSENT_COOKIE = "owlaw_ads_consent";
/** "npa" = non-personalised ads (the default), "personalised" = the reader agreed, "none" = no ads at all. */
export type AdsConsent = "npa" | "personalised" | "none";

export function parseConsent(v: string | null | undefined): AdsConsent | null {
  return v === "npa" || v === "personalised" || v === "none" ? v : null;
}

/** Ads only on the main Owlaw domain (never on firm subdomains or other hosts that reach /blog). */
export function isMainSiteHost(hostname: string, root = process.env.NEXT_PUBLIC_PUBLIC_SITE_ROOT || ""): boolean {
  const h = hostname.toLowerCase();
  const r = root.toLowerCase().trim();
  if (h === "localhost" || h === "127.0.0.1") return true;
  if (!r) return true;
  return h === r || h === `www.${r}`;
}

/** Only these paths may show ads. */
export function isBlogPath(pathname: string): boolean {
  if (pathname === "/blog") return true;
  return /^\/blog\/[a-z0-9-]+\/?$/.test(pathname) && pathname !== "/blog/rss.xml";
}

