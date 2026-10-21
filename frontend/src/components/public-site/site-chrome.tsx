"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { PublicSite } from "@/lib/types";
import { LANGS, useLang, type Lang } from "@/lib/i18n";

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
  const { lang, t, setLang } = useLang();
  const [open, setOpen] = useState(false);
  const [cookieChoice, setCookieChoice] = useState<string | null>("unknown");
  const home = href(base, "");
  const links = [
    { href: href(base, "/expertise"), label: t.expertise },
    has(site, "people") ? { href: href(base, "/people"), label: t.people } : null,
    has(site, "insights") ? { href: href(base, "/insights"), label: t.insights } : null,
    has(site, "situations") ? { href: href(base, "/how-we-help"), label: t.whoWeHelp } : null,
    has(site, "fees") ? { href: href(base, "/fees"), label: t.fees } : null,
    has(site, "recognition") ? { href: href(base, "/recognition"), label: t.recognition } : null,
    { href: href(base, "/contact"), label: t.contact },
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
        {t.skip}
      </a>
      <header className="ps-nav sticky top-0 z-30 border-b border-[var(--ps-line)] bg-[var(--ps-paper)]">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-6 py-3.5">
          <Link href={home} className="text-base font-semibold tracking-tight text-[var(--ps-ink)]" style={serifStyle}>
            {site.firmName}
          </Link>
          <nav className="hidden items-center gap-5 lg:flex" aria-label="Primary">
            {links.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="ps-nav-link text-[11px] font-medium uppercase tracking-[0.14em] text-[var(--ps-meta)] hover:text-[var(--ps-ink)]"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-4">
            <select aria-label={t.language} value={lang} onChange={(e) => setLang(e.target.value as Lang)}
              className="border border-[var(--ps-line)] bg-transparent px-1 py-1 text-xs text-[var(--ps-meta)]">
              {LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
            </select>
            {site.phone ? (
              <a href={`tel:${site.phone.replace(/\s/g, "")}`} className="ps-phone hidden text-sm font-normal text-[var(--ps-meta)] sm:inline">
                {site.phone}
              </a>
            ) : null}
            {has(site, "booking") ? (
              <Link
                href={href(base, "/contact")}
                className="ps-cta hidden px-4 py-2.5 text-sm font-semibold sm:inline"
              >
                {t.enquire}
              </Link>
            ) : null}
            <button
              type="button"
              className="border border-[var(--ps-line)] px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.14em] lg:hidden"
              aria-expanded={open}
              onClick={() => setOpen((value) => !value)}
            >
              {t.menu}
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
              <Link href={href(base, "/contact")} className="ps-cta mt-2 inline-block px-4 py-2 text-sm font-semibold" onClick={() => setOpen(false)}>
                {t.enquire}
              </Link>
            ) : null}
          </nav>
        ) : null}
      </header>
      <main id="main">{children}</main>
      <footer className="border-t border-[var(--ps-line)] bg-[var(--ps-clay)]">
        <div className="mx-auto grid grid-cols-1 max-w-6xl gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-lg" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {site.firmName}
            </p>
            <p className="mt-3 text-sm text-[var(--ps-body)]">{[site.addressLine1, site.city, site.state, site.zip].filter(Boolean).join(", ")}</p>
          </div>
          <div>
            <p className="ps-kicker text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">{t.visit}</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li><Link href={href(base, "/expertise")}>{t.expertise}</Link></li>
              {has(site, "people") ? <li><Link href={href(base, "/people")}>{t.people}</Link></li> : null}
              <li><Link href={href(base, "/contact")}>{t.contact}</Link></li>
              {has(site, "newsletter") ? <li><Link href={href(base, "/subscribe")}>{t.subscribe}</Link></li> : null}
            </ul>
          </div>
          <div>
            <p className="ps-kicker text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">{t.legal}</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li><Link href={href(base, "/legal/privacy")}>{t.privacy}</Link></li>
              <li><Link href={href(base, "/legal/cookies")}>{t.cookies}</Link></li>
              <li><Link href={href(base, "/legal/paia-manual")}>{t.paia}</Link></li>
              <li><Link href={href(base, "/legal/terms")}>{t.terms}</Link></li>
              <li><Link href={href(base, "/legal/accessibility")}>{t.accessibility}</Link></li>
              <li><Link href={href(base, "/legal/fraud-warning")}>{t.fraud}</Link></li>
            </ul>
          </div>
          <div>
            <p className="ps-kicker text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--ps-meta)]">{t.contact}</p>
            <ul className="mt-3 space-y-2 text-sm">
              {site.phone ? <li><a href={`tel:${site.phone.replace(/\s/g, "")}`}>{site.phone}</a></li> : null}
              {site.email ? <li><a href={`mailto:${site.email}`}>{site.email}</a></li> : null}
              {site.whatsappUrl ? (
                <li>
                  <a href={site.whatsappUrl} target="_blank" rel="noreferrer">WhatsApp</a>
                </li>
              ) : null}
              <li><Link href="/portal/login" className="text-[var(--ps-meta)]">{t.portal}</Link></li>
            </ul>
          </div>
        </div>
        <p className="border-t border-[var(--ps-line)] px-6 py-4 text-center text-xs text-[var(--ps-meta)]">
          © {new Date().getFullYear()} {site.firmName}. {t.notMandate}
        </p>
      </footer>
      {cookieChoice === null ? (
        <div className="ps-cookie fixed inset-x-0 bottom-0 z-40 border-t border-[var(--ps-line)] bg-[var(--ps-card)] px-6 py-4 shadow-[0_12px_30px_-18px_rgba(24,24,24,0.2)]" role="dialog" aria-label="Cookie choice">
          <div className="mx-auto flex max-w-6xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-[var(--ps-body)]">
              {t.cookieText}{" "}
              <Link href={href(base, "/legal/cookies")} className="underline">{t.cookies}</Link>
            </p>
            <div className="flex gap-2">
              <button type="button" className="ps-ghost px-4 py-2 text-sm" onClick={() => chooseCookie("necessary")}>
                {t.necessaryOnly}
              </button>
              <button type="button" className="ps-cta px-4 py-2 text-sm font-semibold" onClick={() => chooseCookie("accept")}>
                {t.accept}
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
      <div className="ps-photo relative aspect-[4/5] w-full overflow-hidden">
        <Image src={src} alt={`Portrait of ${name}`} fill sizes="(min-width: 1024px) 33vw, 90vw" className="object-cover object-[center_18%]" />
      </div>
    );
  }
  return (
    <div
      className="flex aspect-[4/5] items-end bg-[var(--ps-sand)] p-5 text-5xl font-semibold text-[var(--ps-meta)]"
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
        <p className="ps-kicker text-[11px] font-medium uppercase tracking-[0.22em] text-[var(--ps-meta)]">{kicker}</p>
        <h1 className="mt-4 max-w-4xl text-5xl font-semibold leading-[0.95] tracking-[-0.03em] text-[var(--ps-ink)] sm:text-6xl lg:text-7xl" style={serifStyle}>
          {title}
          <span className="ps-rule" aria-hidden="true" />
        </h1>
        {lede ? <p className="mt-6 max-w-2xl text-lg leading-relaxed text-[var(--ps-body)]">{lede}</p> : null}
      </div>
    </header>
  );
}

export function SectionTitle({ kicker, title }: { kicker?: string; title: string }) {
  return (
    <div>
      {kicker ? <p className="ps-kicker text-[11px] font-medium uppercase tracking-[0.22em] text-[var(--ps-meta)]">{kicker}</p> : null}
      <h2 className={`${kicker ? "mt-3" : ""} text-4xl font-semibold leading-[1.05] tracking-[-0.02em] text-[var(--ps-ink)] sm:text-5xl`} style={serifStyle}>
        {title}
        <span className="ps-rule" aria-hidden="true" />
      </h2>
    </div>
  );
}
