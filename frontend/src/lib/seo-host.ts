import { headers } from "next/headers";
import { slugFromHost } from "@/middleware";
import { SITE_URL } from "@/lib/seo";

/** Which site this request is for: the main Owlaw site, or a firm subdomain. */
export async function requestSite(): Promise<{ origin: string; firmSlug: string | null }> {
  const h = await headers();
  const host = (h.get("x-forwarded-host") || h.get("host") || "").split(",")[0].trim().toLowerCase();
  const firmSlug = host ? slugFromHost(host) : null;
  return { origin: firmSlug ? `https://${host.split(":")[0]}` : SITE_URL, firmSlug };
}
