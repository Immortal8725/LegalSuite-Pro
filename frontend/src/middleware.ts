import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const MARKETING = [
  "/expertise",
  "/people",
  "/insights",
  "/how-we-help",
  "/fees",
  "/contact",
  "/recognition",
  "/subscribe",
  "/legal",
];

function slugFromHost(host: string): string | null {
  const hostname = host.split(":")[0].toLowerCase();
  const configured = (process.env.NEXT_PUBLIC_PUBLIC_SITE_ROOT || "").toLowerCase().trim();
  const suffixes = ["localhost"];
  if (configured) suffixes.push(configured);
  for (const suffix of suffixes) {
    if (hostname === suffix || !hostname.endsWith(`.${suffix}`)) continue;
    const sub = hostname.slice(0, -(suffix.length + 1));
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(sub)) return null;
    if (sub === "www" || sub === "app" || sub === "api") return null;
    return sub;
  }
  return null;
}

export function middleware(req: NextRequest) {
  const slug = slugFromHost(req.headers.get("host") || "");
  if (!slug) return NextResponse.next();
  const { pathname } = req.nextUrl;
  if (
    pathname.startsWith("/firm/") ||
    pathname.startsWith("/api") ||
    pathname.startsWith("/_next") ||
    pathname.startsWith("/login") ||
    pathname.startsWith("/portal")
  ) {
    return NextResponse.next();
  }
  const marketing =
    pathname === "/" || MARKETING.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
  if (!marketing) return NextResponse.next();
  const url = req.nextUrl.clone();
  url.pathname = pathname === "/" ? `/firm/${slug}` : `/firm/${slug}${pathname}`;
  return NextResponse.rewrite(url);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|manifest.json|icons/).*)"],
};
