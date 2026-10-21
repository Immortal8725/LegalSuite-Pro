/** Parent domain for firm sites, e.g. "owlaw.co.za". Empty keeps the old /firm/<slug> paths. */
export const PUBLIC_SITE_ROOT = (process.env.NEXT_PUBLIC_PUBLIC_SITE_ROOT || "").trim().toLowerCase();

/** A firm's public site address: https://<slug>.<root> when subdomains are on, else /firm/<slug>. */
export function publicSiteUrl(slug: string): string {
  return PUBLIC_SITE_ROOT ? `https://${slug}.${PUBLIC_SITE_ROOT}` : `/firm/${slug}`;
}

/** The address as people read it, without https://. */
export function publicSiteLabel(slug: string): string {
  return PUBLIC_SITE_ROOT ? `${slug}.${PUBLIC_SITE_ROOT}` : `owlaw.co.za/firm/${slug}`;
}
