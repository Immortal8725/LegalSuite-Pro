import { requestSite } from "@/lib/seo-host";

export const dynamic = "force-dynamic";

/** Public pages are open. The practice app, admin, API, portal and one-time links are kept out. */
const PRIVATE = [
  "/api/", "/ws/", "/dashboard", "/cases", "/clients", "/contacts", "/calendar", "/tasks", "/time", "/billing", "/trust", "/documents",
  "/esign", "/expenses", "/fica", "/conflicts", "/leads", "/messages", "/reports", "/settings", "/team", "/templates", "/voice",
  "/website", "/usage", "/modules", "/integrations", "/import", "/audit", "/ai", "/bundles", "/fitness", "/staff-activity",
  "/support-access", "/directory-listing", "/product-billing", "/platform", "/portal", "/onboarding", "/change-password",
  "/set-password", "/two-factor", "/pay/", "/sign/", "/status",
];

export async function GET() {
  const { origin } = await requestSite();
  const body = ["User-agent: *", "Allow: /", ...PRIVATE.map((p) => `Disallow: ${p}`), "", `Sitemap: ${origin}/sitemap.xml`, ""].join("\n");
  return new Response(body, { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" } });
}
