import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { RESERVED_SLUGS } from "@/lib/reserved-slugs";

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

/** Subdomains Owlaw keeps for itself; they never resolve to a firm site. Keep in step with AuthService.RESERVED_SLUGS. */
export const RESERVED = RESERVED_SLUGS;
const DNS_LABEL = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;

function rootDomain(): string {
  return (process.env.NEXT_PUBLIC_PUBLIC_SITE_ROOT || "").toLowerCase().trim();
}

export function slugFromHost(host: string, root = rootDomain()): string | null {
  const hostname = host.split(":")[0].toLowerCase();
  const suffixes = ["localhost"];
  if (root) suffixes.push(root);
  for (const suffix of suffixes) {
    if (hostname === suffix || !hostname.endsWith(`.${suffix}`)) continue;
    const sub = hostname.slice(0, -(suffix.length + 1));
    if (!DNS_LABEL.test(sub) || RESERVED.has(sub)) return null;
    return sub;
  }
  return null;
}

export function middleware(req: NextRequest) {
  const host = (req.headers.get("host") || "").split(":")[0].toLowerCase();
  const { pathname, search } = req.nextUrl;
  const root = rootDomain();

  // www.owlaw.co.za (and any www.*) goes to the bare domain, keeping the path.
  if (host.startsWith("www.")) {
    return NextResponse.redirect(`https://${host.slice(4)}${pathname}${search}`, 301);
  }

  const slug = slugFromHost(host, root);
  if (!slug) {
    // Old links on the main domain: /firm/<slug>/... moves to <slug>.<root>/... once subdomains are on.
    const old = pathname.match(/^\/firm\/([a-z0-9-]+)(\/.*)?$/);
    if (root && old && DNS_LABEL.test(old[1]) && !RESERVED.has(old[1]) && (host === root || host.endsWith(".onrender.com"))) {
      return NextResponse.redirect(`https://${old[1]}.${root}${old[2] || ""}${search}`, 301);
    }
    return NextResponse.next();
  }
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
