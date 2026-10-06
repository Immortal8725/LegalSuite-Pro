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
    <div style={{ ["--ps-accent" as string]: accent }} className="min-h-screen bg-[var(--ps-paper)] text-[var(--ps-ink)]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to main content
      </a>
      <header className="sticky top-0 z-30 border-b border-[var(--ps-line)] bg-[var(--ps-paper)]">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-6 py-3.5">
          <Link href={home} className="text-base font-semibold tracking-tight text-[var(--ps-ink)]" style={serifStyle}>
            {site.firmName}
          </Link>
          <nav className="hidden items-center gap-5 lg:flex" aria-label="Primary">
            {links.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-[11px] font-medium uppercase tracking-[0.14em] text-[var(--ps-meta)] hover:text-[var(--ps-ink)]"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-4">
            {site.phone ? (
              <a href={`tel:${site.phone.replace(/\s/g, "")}`} className="hidden text-sm font-normal text-[var(--ps-meta)] sm:inline">
                {site.phone}
              </a>
            ) : null}
            {has(site, "booking") ? (
              <Link
                href={href(base, "/contact")}
                className="hidden bg-[var(--ps-accent)] px-4 py-2.5 text-sm font-semibold text-white shadow-card sm:inline"
              >
                Make an enquiry
              </Link>
            ) : null}
            <button
              type="button"
              className="border border-[var(--ps-line)] px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.14em] lg:hidden"
              aria-expanded={open}
              onClick={() => setOpen((value) => !value)}
            >
              Menu
            </button>
          </div>
        </div>
        {open ? (
          <nav className="border-t border-[var(--ps-line)] px-6 py-3 lg:hidden" aria-label="Mobile">
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
      <footer className="border-t border-[var(--ps-line)] bg-[var(--ps-clay)]">
        <div className="mx-auto grid max-w-6xl gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-lg" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {site.firmName}
            </p>
            <p className="mt-3 text-sm text-[var(--ps-body)]">{[site.addressLine1, site.city, site.state, site.zip].filter(Boolean).join(", ")}</p>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">Visit</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li><Link href={href(base, "/expertise")}>Expertise</Link></li>
              {has(site, "people") ? <li><Link href={href(base, "/people")}>People</Link></li> : null}
              <li><Link href={href(base, "/contact")}>Contact</Link></li>
              {has(site, "newsletter") ? <li><Link href={href(base, "/subscribe")}>Subscribe</Link></li> : null}
            </ul>
          </div>
          <div>
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">Legal</p>
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
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">Contact</p>
            <ul className="mt-3 space-y-2 text-sm">
              {site.phone ? <li><a href={`tel:${site.phone.replace(/\s/g, "")}`}>{site.phone}</a></li> : null}
              {site.email ? <li><a href={`mailto:${site.email}`}>{site.email}</a></li> : null}
              {site.whatsappUrl ? (
                <li>
                  <a href={site.whatsappUrl} target="_blank" rel="noreferrer">WhatsApp</a>
                </li>
              ) : null}
              <li><Link href="/portal/login" className="text-[var(--ps-meta)]">Client portal</Link></li>
            </ul>
          </div>
        </div>
        <p className="border-t border-[var(--ps-line)] px-6 py-4 text-center text-xs text-[var(--ps-meta)]">
          © {new Date().getFullYear()} {site.firmName}. General information, not a mandate to act.
        </p>
      </footer>
      {cookieChoice === null ? (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--ps-line)] bg-[var(--ps-card)] px-6 py-4 shadow-[0_12px_30px_-18px_rgba(74,42,16,0.45)]" role="dialog" aria-label="Cookie choice">
          <div className="mx-auto flex max-w-6xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-[var(--ps-body)]">
              This site can store your cookie choice on this browser. It does not load advertising or analytics scripts.{" "}
              <Link href={href(base, "/legal/cookies")} className="underline">Cookie policy</Link>
            </p>
            <div className="flex gap-2">
              <button type="button" className="border border-[var(--ps-line)] bg-[var(--ps-card)] px-4 py-2 text-sm text-[var(--ps-body)]" onClick={() => chooseCookie("necessary")}>
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

export const serifStyle = { fontFamily: "var(--font-public-serif), Georgia, serif" } as const;

export function Portrait({ initials, name, src }: { initials: string; name: string; src?: string }) {
  if (src) {
    return (
      <img
        src={src}
        alt=""
        className="aspect-[4/5] w-full object-cover object-[center_18%]"
      />
    );
  }
  return (
    <div
      className="flex aspect-[4/5] items-end bg-[#e8d3b8] p-5 text-5xl font-semibold text-[#6b5340]"
      style={serifStyle}
      role="img"
      aria-label={`Portrait placeholder for ${name}`}
    >
      <span aria-hidden="true">{initials}</span>
    </div>
  );
}

export function PageIntro({ kicker, title, lede }: { kicker: string; title: string; lede?: string }) {
  return (
    <header className="bg-[var(--ps-paper)]">
      <div className="mx-auto max-w-6xl px-6 pb-16 pt-16 lg:pb-20 lg:pt-24">
        <p className="text-[11px] font-medium uppercase tracking-[0.22em] text-[var(--ps-meta)]">{kicker}</p>
        <h1 className="mt-4 max-w-4xl text-5xl font-semibold leading-[0.95] tracking-[-0.03em] text-[var(--ps-ink)] sm:text-6xl lg:text-7xl" style={serifStyle}>
          {title}
        </h1>
        {lede ? <p className="mt-6 max-w-2xl text-lg leading-relaxed text-[var(--ps-body)]">{lede}</p> : null}
      </div>
    </header>
  );
}

export function SectionTitle({ kicker, title }: { kicker?: string; title: string }) {
  return (
    <div>
      {kicker ? <p className="text-[11px] font-medium uppercase tracking-[0.22em] text-[var(--ps-meta)]">{kicker}</p> : null}
      <h2 className={`${kicker ? "mt-3" : ""} text-4xl font-semibold leading-[1.05] tracking-[-0.02em] text-[var(--ps-ink)] sm:text-5xl`} style={serifStyle}>
        {title}
      </h2>
    </div>
  );
}
