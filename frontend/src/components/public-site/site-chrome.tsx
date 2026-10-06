"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { PublicSite } from "@/lib/types";

const COOKIE_KEY = "ls_public_cookie";

export function useSiteBase(slug: string) {
  const [base, setBase] = useState(`/firm/${slug}`);
  useEffect(() => {
    const path = window.location.pathname;
    if (path === `/firm/${slug}` || path.startsWith(`/firm/${slug}/`)) setBase(`/firm/${slug}`);
    else setBase("");
  }, [slug]);
  return base;
}

export function href(base: string, path: string) {
  if (!path) return base || "/";
  return `${base}${path}`;
}

function has(site: PublicSite, key: string) {
  return (site.features || []).includes(key);
}

export function SiteFrame({
  site,
  base,
  children,
}: {
  site: PublicSite;
  base: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [cookieChoice, setCookieChoice] = useState<string | null>("unknown");
  const home = href(base, "");
  const links = [
    { href: href(base, "/expertise"), label: "Expertise" },
    has(site, "people") ? { href: href(base, "/people"), label: "People" } : null,
    has(site, "insights") ? { href: href(base, "/insights"), label: "Insights" } : null,
    has(site, "situations") ? { href: href(base, "/how-we-help"), label: "Who we help" } : null,
    has(site, "fees") ? { href: href(base, "/fees"), label: "Fees" } : null,
    has(site, "recognition") ? { href: href(base, "/recognition"), label: "Recognition" } : null,
    { href: href(base, "/contact"), label: "Contact" },
  ].filter((item): item is { href: string; label: string } => item !== null);

  useEffect(() => {
    setCookieChoice(window.localStorage.getItem(COOKIE_KEY));
  }, []);

  function chooseCookie(value: string) {
    window.localStorage.setItem(COOKIE_KEY, value);
    setCookieChoice(value);
  }

  const accent = site.accentHex || "#1b3a4b";

  return (
    <div style={{ ["--ps-accent" as string]: accent }} className="min-h-screen bg-[#f6f3ee] text-[#1c1917]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to main content
      </a>
      <header className="sticky top-0 z-30 border-b border-[#e6e0d6] bg-[#f6f3ee]/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-4">
          <Link href={home} className="text-xl tracking-tight" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
            {site.firmName}
          </Link>
          <nav className="hidden items-center gap-5 lg:flex" aria-label="Primary">
            {links.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#44403c] hover:text-[#1c1917]"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            {site.phone ? (
              <a href={`tel:${site.phone.replace(/\s/g, "")}`} className="hidden text-sm font-semibold sm:inline">
                {site.phone}
              </a>
            ) : null}
            {has(site, "booking") ? (
              <Link
                href={href(base, "/contact")}
                className="hidden bg-[var(--ps-accent)] px-4 py-2 text-sm font-semibold text-white sm:inline"
              >
                Make an enquiry
              </Link>
            ) : null}
            <button
              type="button"
              className="border border-[#d6d0c6] px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.14em] lg:hidden"
              aria-expanded={open}
              onClick={() => setOpen((value) => !value)}
            >
              Menu
            </button>
          </div>
        </div>
        {open ? (
          <nav className="border-t border-[#e6e0d6] px-6 py-3 lg:hidden" aria-label="Mobile">
            {links.map((item) => (
              <Link key={item.href} href={item.href} className="block py-2 text-sm" onClick={() => setOpen(false)}>
                {item.label}
              </Link>
            ))}
            {has(site, "booking") ? (
              <Link href={href(base, "/contact")} className="mt-2 inline-block bg-[var(--ps-accent)] px-4 py-2 text-sm font-semibold text-white" onClick={() => setOpen(false)}>
                Make an enquiry
              </Link>
            ) : null}
          </nav>
        ) : null}
      </header>
      <main id="main">{children}</main>
      <footer className="border-t border-[#e6e0d6] bg-[#efeae2]">
        <div className="mx-auto grid max-w-6xl gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-lg" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {site.firmName}
            </p>
            <p className="mt-3 text-sm text-[#44403c]">{[site.addressLine1, site.city, site.state, site.zip].filter(Boolean).join(", ")}</p>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#78716c]">Visit</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li><Link href={href(base, "/expertise")}>Expertise</Link></li>
              {has(site, "people") ? <li><Link href={href(base, "/people")}>People</Link></li> : null}
              <li><Link href={href(base, "/contact")}>Contact</Link></li>
              {has(site, "newsletter") ? <li><Link href={href(base, "/subscribe")}>Subscribe</Link></li> : null}
            </ul>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#78716c]">Legal</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li><Link href={href(base, "/legal/privacy")}>Privacy notice</Link></li>
              <li><Link href={href(base, "/legal/cookies")}>Cookie policy</Link></li>
              <li><Link href={href(base, "/legal/paia-manual")}>PAIA manual</Link></li>
              <li><Link href={href(base, "/legal/terms")}>Terms</Link></li>
              <li><Link href={href(base, "/legal/accessibility")}>Accessibility</Link></li>
              <li><Link href={href(base, "/legal/fraud-warning")}>Fraud warning</Link></li>
            </ul>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#78716c]">Contact</p>
            <ul className="mt-3 space-y-2 text-sm">
              {site.phone ? <li><a href={`tel:${site.phone.replace(/\s/g, "")}`}>{site.phone}</a></li> : null}
              {site.email ? <li><a href={`mailto:${site.email}`}>{site.email}</a></li> : null}
              {site.whatsappUrl ? (
                <li>
                  <a href={site.whatsappUrl} target="_blank" rel="noreferrer">WhatsApp</a>
                </li>
              ) : null}
              <li><Link href="/portal/login" className="text-[#78716c]">Client portal</Link></li>
            </ul>
          </div>
        </div>
        <p className="border-t border-[#e6e0d6] px-6 py-4 text-center text-xs text-[#78716c]">
          © {new Date().getFullYear()} {site.firmName}. General information, not a mandate to act.
        </p>
      </footer>
      {cookieChoice === null ? (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[#e6e0d6] bg-[#fffcf8] px-6 py-4 shadow-lift" role="dialog" aria-label="Cookie choice">
          <div className="mx-auto flex max-w-6xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-[#44403c]">
              This site can store your cookie choice on this browser. It does not load advertising or analytics scripts.{" "}
              <Link href={href(base, "/legal/cookies")} className="underline">Cookie policy</Link>
            </p>
            <div className="flex gap-2">
              <button type="button" className="border border-[#1c1917] px-4 py-2 text-sm" onClick={() => chooseCookie("necessary")}>
                Necessary only
              </button>
              <button type="button" className="bg-[var(--ps-accent)] px-4 py-2 text-sm font-semibold text-white" onClick={() => chooseCookie("accept")}>
                Accept
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function Portrait({ initials, name }: { initials: string; name: string }) {
  return (
    <div
      className="flex aspect-[4/5] items-end bg-[#e7dfd4] p-4 text-4xl text-[#5c5346]"
      style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}
      role="img"
      aria-label={`Portrait placeholder for ${name}`}
    >
      <span aria-hidden="true">{initials}</span>
    </div>
  );
}

export function PageIntro({ kicker, title, lede }: { kicker: string; title: string; lede?: string }) {
  return (
    <header className="border-b border-[#e6e0d6]">
      <div className="mx-auto max-w-6xl px-6 py-16">
        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--ps-accent)]">{kicker}</p>
        <h1 className="mt-3 max-w-3xl text-4xl leading-tight sm:text-5xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
          {title}
        </h1>
        {lede ? <p className="mt-4 max-w-2xl text-lg text-[#44403c]">{lede}</p> : null}
      </div>
    </header>
  );
}
