"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMemo, useState } from "react";
import type { PublicPerson, PublicSite } from "@/lib/types";
import { apiPost } from "@/lib/api";
import { usePublicSite } from "@/components/public-site/site-context";
import { EnquiryForm } from "@/components/public-site/enquiry-form";
import { PageIntro, Portrait, SiteFrame, href, useSiteBase } from "@/components/public-site/site-chrome";

export type FirmPage =
  | "home"
  | "expertise"
  | "practice"
  | "people"
  | "person"
  | "insights"
  | "insight"
  | "situations"
  | "fees"
  | "contact"
  | "recognition"
  | "subscribe"
  | "legal";

const FEATURE_FOR: Partial<Record<FirmPage, string>> = {
  people: "people",
  person: "people",
  insights: "insights",
  insight: "insights",
  situations: "situations",
  fees: "fees",
  recognition: "recognition",
  subscribe: "newsletter",
};

export function FirmView({ view }: { view: FirmPage }) {
  const { site, error, loading } = usePublicSite();
  const base = useSiteBase(site?.slug || "");

  if (loading) return <p className="px-6 py-16 text-sm text-[#78716c]">Loading…</p>;
  if (error || !site) {
    return (
      <div className="mx-auto max-w-xl px-6 py-20">
        <h1 className="text-3xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>Firm not found</h1>
        <p className="mt-3 text-sm text-[#44403c]">{error || "This address does not match a firm."}</p>
      </div>
    );
  }
  if (!site.published) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24">
        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[#78716c]">Public site</p>
        <h1 className="mt-3 text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{site.firmName}</h1>
        <p className="mt-4 text-[#44403c]">This public site is not live yet.</p>
      </div>
    );
  }

  const needed = FEATURE_FOR[view];
  if (needed && !(site.features || []).includes(needed)) {
    return (
      <SiteFrame site={site} base={base}>
        <div className="mx-auto max-w-3xl px-6 py-20">
          <h1 className="text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>This page is not on the public site.</h1>
          <Link href={href(base, "")} className="mt-6 inline-block text-sm font-semibold underline">Back to {site.firmName}</Link>
        </div>
      </SiteFrame>
    );
  }

  return (
    <SiteFrame site={site} base={base}>
      <ViewBody view={view} site={site} base={base} />
    </SiteFrame>
  );
}

function ViewBody({ view, site, base }: { view: FirmPage; site: PublicSite; base: string }) {
  if (view === "home") return <Home site={site} base={base} />;
  if (view === "expertise") return <Expertise site={site} base={base} />;
  if (view === "practice") return <Practice site={site} base={base} />;
  if (view === "people") return <People site={site} base={base} />;
  if (view === "person") return <Person site={site} base={base} />;
  if (view === "insights") return <Insights site={site} base={base} />;
  if (view === "insight") return <Insight site={site} base={base} />;
  if (view === "situations") return <Situations site={site} base={base} />;
  if (view === "fees") return <Fees site={site} />;
  if (view === "contact") return <Contact site={site} base={base} />;
  if (view === "recognition") return <Recognition site={site} />;
  if (view === "subscribe") return <Subscribe site={site} base={base} />;
  return <Legal site={site} base={base} />;
}

function place(site: PublicSite) {
  return [site.city, site.state].filter(Boolean).join(", ");
}

function Home({ site, base }: { site: PublicSite; base: string }) {
  const features = new Set(site.features || []);
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "LegalService",
    name: site.firmName,
    telephone: site.phone,
    email: site.email,
    address: {
      "@type": "PostalAddress",
      streetAddress: site.addressLine1,
      addressLocality: site.city,
      addressRegion: site.state,
      postalCode: site.zip,
      addressCountry: site.country,
    },
  };
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <section className="border-b border-[#e6e0d6]">
        <div className="mx-auto grid max-w-6xl items-end gap-12 px-6 py-20 lg:grid-cols-12">
          <div className="lg:col-span-7">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--ps-accent)]">{place(site) || "Law firm"}</p>
            <h1 className="mt-4 text-4xl leading-[1.05] sm:text-6xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {site.tagline || site.firmName}
            </h1>
            {site.about ? <p className="mt-6 max-w-xl text-lg leading-relaxed text-[#44403c]">{site.about}</p> : null}
            <div className="mt-8 flex flex-wrap gap-3">
              {features.has("booking") ? (
                <Link href={href(base, "/contact")} className="bg-[var(--ps-accent)] px-5 py-3 text-sm font-semibold text-white">
                  Make an enquiry
                </Link>
              ) : null}
              <Link href={href(base, "/expertise")} className="border border-[#1c1917] px-5 py-3 text-sm font-semibold">
                View expertise
              </Link>
            </div>
          </div>
          <aside className="bg-[#efeae2] p-8 lg:col-span-5">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#78716c]">Office</p>
            <p className="mt-4 text-2xl leading-snug" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {[site.addressLine1, place(site)].filter(Boolean).join(", ")}
            </p>
            {site.phone ? (
              <a href={`tel:${site.phone.replace(/\s/g, "")}`} className="mt-6 block text-lg font-semibold">
                {site.phone}
              </a>
            ) : null}
            {site.whatsappUrl ? (
              <a href={site.whatsappUrl} className="mt-2 inline-block text-sm underline" target="_blank" rel="noreferrer">
                WhatsApp the firm
              </a>
            ) : null}
          </aside>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-16">
        <div className="flex items-end justify-between gap-4">
          <h2 className="text-3xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>Expertise</h2>
          <Link href={href(base, "/expertise")} className="text-sm font-semibold underline">All practices</Link>
        </div>
        <ul className="mt-8 divide-y divide-[#e6e0d6] border-y border-[#e6e0d6]">
          {(site.practices || []).map((practice) => (
            <li key={practice.slug}>
              <Link href={href(base, `/expertise/${practice.slug}`)} className="flex items-center justify-between py-4 text-lg hover:text-[var(--ps-accent)]">
                <span>{practice.name}</span>
                <span aria-hidden="true">→</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {features.has("people") && site.people?.length ? (
        <section className="border-y border-[#e6e0d6] bg-[#fffcf8]">
          <div className="mx-auto max-w-6xl px-6 py-16">
            <div className="flex items-end justify-between gap-4">
              <h2 className="text-3xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>People</h2>
              <Link href={href(base, "/people")} className="text-sm font-semibold underline">Directory</Link>
            </div>
            <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {site.people.map((person) => (
                <Link key={person.id} href={href(base, `/people/${person.id}`)} className="group">
                  <Portrait initials={person.initials} name={person.fullName} />
                  <p className="mt-3 text-lg" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{person.fullName}</p>
                  <p className="text-sm text-[#57534e]">{person.title}</p>
                </Link>
              ))}
            </div>
          </div>
        </section>
      ) : null}

      {features.has("insights") && site.insights?.length ? (
        <section className="mx-auto max-w-6xl px-6 py-16">
          <h2 className="text-3xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>Insights</h2>
          <div className="mt-8 grid gap-6 md:grid-cols-2">
            {site.insights.map((article) => (
              <Link key={article.slug} href={href(base, `/insights/${article.slug}`)} className="border border-[#e6e0d6] bg-[#fffcf8] p-6">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--ps-accent)]">{article.type} / {article.date}</p>
                <h3 className="mt-3 text-2xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{article.title}</h3>
                <p className="mt-3 text-sm text-[#44403c]">{article.summary}</p>
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      {features.has("booking") ? (
        <section className="border-t border-[#e6e0d6]">
          <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-2">
            <div>
              <h2 className="text-3xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>Make an enquiry</h2>
              <p className="mt-4 text-[#44403c]">Tell the firm what happened. A conflict check comes before any mandate.</p>
            </div>
            <EnquiryForm site={site} base={base} />
          </div>
        </section>
      ) : null}

      {features.has("recognition") && site.recognition?.length ? (
        <section className="border-t border-[#e6e0d6] bg-[#efeae2]">
          <div className="mx-auto max-w-6xl px-6 py-12">
            <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em]">Recognition</h2>
            <ul className="mt-4 space-y-2">
              {site.recognition.map((item) => (
                <li key={`${item.year}-${item.title}`} className="text-sm">
                  <span className="font-semibold">{item.year}</span> {item.title} <span className="text-[#57534e]">({item.source})</span>
                </li>
              ))}
            </ul>
          </div>
        </section>
      ) : null}
    </>
  );
}

function Expertise({ site, base }: { site: PublicSite; base: string }) {
  return (
    <>
      <PageIntro kicker="Expertise" title="Practice areas" lede="Each practice is a starting point. The firm confirms it can act before a file is opened." />
      <ul className="mx-auto max-w-6xl divide-y divide-[#e6e0d6] px-6 py-10">
        {(site.practices || []).map((practice) => (
          <li key={practice.slug} className="py-6">
            <Link href={href(base, `/expertise/${practice.slug}`)} className="text-2xl hover:text-[var(--ps-accent)]" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>
              {practice.name}
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

function Practice({ site, base }: { site: PublicSite; base: string }) {
  const params = useParams<{ practice: string }>();
  const practice = (site.practices || []).find((item) => item.slug === params.practice);
  const contact = site.people?.[0];
  if (!practice) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>That practice is not listed.</h1>
      </div>
    );
  }
  return (
    <>
      <PageIntro kicker="Expertise" title={practice.name} lede={`${site.firmName} advises on ${practice.name}. This page is a stub, not a full advice note.`} />
      <div className="mx-auto max-w-3xl space-y-6 px-6 py-12 text-[#44403c]">
        <p>Work starts with a conflict check and, where the firm is instructed, a written mandate. Nothing on this page is a promise to act.</p>
        <ContactBlock site={site} base={base} person={contact} />
      </div>
    </>
  );
}

function People({ site, base }: { site: PublicSite; base: string }) {
  const people = site.people || [];
  const [query, setQuery] = useState("");
  const [title, setTitle] = useState("All");
  const titles = useMemo(() => ["All", ...Array.from(new Set(people.map((person) => person.title)))], [people]);
  const shown = people.filter((person) => {
    const hay = `${person.fullName} ${person.title}`.toLowerCase();
    const matchesQuery = hay.includes(query.trim().toLowerCase());
    const matchesTitle = title === "All" || person.title === title;
    return matchesQuery && matchesTitle;
  });
  return (
    <>
      <PageIntro kicker="People" title="Find a lawyer" lede="Titles follow the role the firm uses on its letterhead. Photographs are not uploaded in this version." />
      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
          <label className="block flex-1 text-sm">
            Search by name or title
            <input value={query} onChange={(e) => setQuery(e.target.value)} className="mt-1 w-full border border-[#d6d0c6] bg-white px-3 py-2" />
          </label>
          <div className="flex flex-wrap gap-2">
            {titles.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setTitle(item)}
                className={`border px-3 py-2 text-sm ${title === item ? "border-[#1c1917] bg-[#1c1917] text-white" : "border-[#d6d0c6]"}`}
              >
                {item}
              </button>
            ))}
          </div>
        </div>
        <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((person) => (
            <Link key={person.id} href={href(base, `/people/${person.id}`)}>
              <Portrait initials={person.initials} name={person.fullName} />
              <p className="mt-3 text-lg" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{person.fullName}</p>
              <p className="text-sm text-[#57534e]">{person.title}</p>
            </Link>
          ))}
        </div>
        {shown.length === 0 ? <p className="mt-8 text-sm text-[#57534e]">No one matches that search.</p> : null}
      </div>
    </>
  );
}

function Person({ site, base }: { site: PublicSite; base: string }) {
  const params = useParams<{ id: string }>();
  const person = (site.people || []).find((item) => item.id === params.id);
  if (!person) {
    return <div className="mx-auto max-w-3xl px-6 py-16"><h1 className="text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>That profile is not listed.</h1></div>;
  }
  return (
    <>
      <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-12">
        <div className="lg:col-span-4">
          <Portrait initials={person.initials} name={person.fullName} />
        </div>
        <div className="lg:col-span-8">
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--ps-accent)]">{person.title}</p>
          <h1 className="mt-2 text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{person.fullName}</h1>
          {person.bio ? <p className="mt-4 max-w-2xl text-lg text-[#44403c]">{person.bio}</p> : null}
          <ul className="mt-6 space-y-2 text-sm">
            {person.phone ? <li><a href={`tel:${person.phone.replace(/\s/g, "")}`}>{person.phone}</a></li> : null}
            {person.email ? <li><a href={`mailto:${person.email}`}>{person.email}</a></li> : null}
          </ul>
          <button type="button" className="mt-6 border border-[#1c1917] px-4 py-2 text-sm" onClick={() => downloadVcard(site, person)}>
            Download vCard
          </button>
          <div className="mt-10">
            <ContactBlock site={site} base={base} person={person} />
          </div>
        </div>
      </div>
    </>
  );
}

function downloadVcard(site: PublicSite, person: PublicPerson) {
  const lines = [
    "BEGIN:VCARD",
    "VERSION:3.0",
    `FN:${person.fullName}`,
    `TITLE:${person.title}`,
    `ORG:${site.firmName}`,
    person.phone ? `TEL:${person.phone}` : "",
    person.email ? `EMAIL:${person.email}` : "",
    "END:VCARD",
  ].filter(Boolean);
  const blob = new Blob([lines.join("\n")], { type: "text/vcard" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${person.fullName.replace(/\s+/g, "-").toLowerCase()}.vcf`;
  link.click();
  URL.revokeObjectURL(url);
}

function Insights({ site, base }: { site: PublicSite; base: string }) {
  return (
    <>
      <PageIntro kicker="Insights" title="Notes from the firm" lede="Short notes for general reading. They are not advice on your matter." />
      <div className="mx-auto grid max-w-6xl gap-6 px-6 py-12 md:grid-cols-2">
        {(site.insights || []).map((article) => (
          <Link key={article.slug} href={href(base, `/insights/${article.slug}`)} className="border border-[#e6e0d6] bg-[#fffcf8] p-6">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--ps-accent)]">{article.type} / {article.date}</p>
            <h2 className="mt-3 text-2xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{article.title}</h2>
            <p className="mt-3 text-sm text-[#44403c]">{article.summary}</p>
          </Link>
        ))}
      </div>
    </>
  );
}

function Insight({ site, base }: { site: PublicSite; base: string }) {
  const params = useParams<{ article: string }>();
  const article = (site.insights || []).find((item) => item.slug === params.article);
  if (!article) {
    return <div className="mx-auto max-w-3xl px-6 py-16"><h1 className="text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>That note is not listed.</h1></div>;
  }
  return (
    <article className="mx-auto max-w-3xl px-6 py-16">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--ps-accent)]">{article.type} / {article.date}</p>
      <h1 className="mt-3 text-4xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{article.title}</h1>
      <p className="mt-6 text-lg leading-relaxed text-[#44403c]">{article.body}</p>
      <Link href={href(base, "/insights")} className="mt-8 inline-block text-sm underline">All insights</Link>
    </article>
  );
}

function Situations({ site, base }: { site: PublicSite; base: string }) {
  return (
    <>
      <PageIntro kicker="Who we help" title="Start with the situation" lede="Clients often arrive with a problem, not a practice name." />
      <ul className="mx-auto max-w-3xl space-y-6 px-6 py-12">
        {(site.situations || []).map((item) => (
          <li key={item.slug} className="border-b border-[#e6e0d6] pb-6">
            <h2 className="text-2xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{item.title}</h2>
            <p className="mt-2 text-[#44403c]">{item.summary}</p>
          </li>
        ))}
      </ul>
      <div className="mx-auto max-w-3xl px-6 pb-16">
        <Link href={href(base, "/contact")} className="bg-[var(--ps-accent)] px-5 py-3 text-sm font-semibold text-white">Contact the firm</Link>
      </div>
    </>
  );
}

function Fees({ site }: { site: PublicSite }) {
  return (
    <>
      <PageIntro kicker="Fees" title="How fees are agreed" lede={site.feesNote} />
      <div className="mx-auto max-w-3xl px-6 py-12 text-[#44403c]">
        <p>A practitioner reviews the mandate before it is signed. This page does not compare the firm with any other practice.</p>
      </div>
    </>
  );
}

function Contact({ site, base }: { site: PublicSite; base: string }) {
  const booking = (site.features || []).includes("booking");
  return (
    <>
      <PageIntro kicker="Contact" title="Talk to the firm" lede={place(site) ? `The office is in ${place(site)}.` : undefined} />
      <div className="mx-auto grid max-w-6xl gap-12 px-6 py-12 lg:grid-cols-2">
        <div className="text-sm text-[#44403c]">
          <p className="text-2xl text-[#1c1917]" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{site.firmName}</p>
          <p className="mt-4">{[site.addressLine1, site.city, site.state, site.zip].filter(Boolean).join(", ")}</p>
          {site.phone ? <p className="mt-3"><a href={`tel:${site.phone.replace(/\s/g, "")}`} className="text-lg font-semibold text-[#1c1917]">{site.phone}</a></p> : null}
          {site.email ? <p className="mt-2"><a href={`mailto:${site.email}`}>{site.email}</a></p> : null}
          {site.whatsappUrl ? <p className="mt-4"><a className="underline" href={site.whatsappUrl} target="_blank" rel="noreferrer">WhatsApp the firm</a></p> : null}
        </div>
        <div>
          {booking ? <EnquiryForm site={site} base={base} /> : <p>The enquiry form is not open. Call the office number on this page.</p>}
        </div>
      </div>
    </>
  );
}

function Recognition({ site }: { site: PublicSite }) {
  const items = site.recognition || [];
  return (
    <>
      <PageIntro kicker="Recognition" title="Credentials, with a year and a source" lede="The firm does not publish client names here." />
      {items.length === 0 ? (
        <p className="mx-auto max-w-3xl px-6 py-12 text-[#44403c]">Nothing is listed yet.</p>
      ) : (
        <ul className="mx-auto max-w-3xl space-y-4 px-6 py-12">
          {items.map((item) => (
            <li key={`${item.year}-${item.title}`} className="border-b border-[#e6e0d6] pb-4">
              <p className="text-xl" style={{ fontFamily: "var(--font-public-serif), Georgia, serif" }}>{item.title}</p>
              <p className="mt-1 text-sm text-[#57534e]">{item.year} / {item.source}</p>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function Subscribe({ site, base }: { site: PublicSite; base: string }) {
  const [email, setEmail] = useState("");
  const [consent, setConsent] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <>
      <PageIntro kicker="Newsletter" title="Occasional notes by email" lede="The box starts unticked. The firm does not send a message from this demo until a mail tool is connected. The opt-in is still recorded." />
      <form
        className="mx-auto max-w-xl space-y-4 px-6 py-12"
        onSubmit={async (event) => {
          event.preventDefault();
          setError(null);
          try {
            const res = await apiPost<{ message: string }>(`/api/v1/public/sites/${site.slug}/subscribe`, { email, consent });
            setMessage(res.message);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not save the opt-in.");
          }
        }}
      >
        {message ? <p className="border border-[#d6d0c6] bg-white p-4 text-sm">{message}</p> : null}
        {error ? <p className="border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p> : null}
        {!message ? (
          <>
            <label className="block text-sm">
              Email
              <input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="mt-1 w-full border border-[#d6d0c6] bg-white px-3 py-2" />
            </label>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} className="mt-1" />
              <span>
                I agree to receive email updates from {site.firmName}. I can ask the firm to stop. See the{" "}
                <Link href={href(base, "/legal/privacy")} className="underline">privacy notice</Link>.
              </span>
            </label>
            <button type="submit" className="bg-[var(--ps-accent)] px-5 py-3 text-sm font-semibold text-white">Save opt-in</button>
          </>
        ) : null}
      </form>
    </>
  );
}

function Legal({ site, base }: { site: PublicSite; base: string }) {
  const params = useParams<{ doc: string }>();
  const copy = legalCopy(params.doc, site);
  return (
    <>
      <PageIntro kicker="Legal" title={copy.title} lede="A practitioner must review this text before it is used as the firm's live notice." />
      <div className="mx-auto max-w-3xl space-y-4 px-6 py-12 text-[#44403c]">
        {copy.paragraphs.map((paragraph) => (
          <p key={paragraph}>{paragraph}</p>
        ))}
        <p>
          <Link href={href(base, "/contact")} className="underline">Contact the firm</Link>
        </p>
      </div>
    </>
  );
}

function legalCopy(doc: string, site: PublicSite) {
  const officer = [site.informationOfficerName, site.informationOfficerEmail].filter(Boolean).join(", ");
  const pages: Record<string, { title: string; paragraphs: string[] }> = {
    privacy: {
      title: "Privacy notice",
      paragraphs: [
        `${site.firmName} collects a name, email, phone number, and a short description when you use the enquiry form or the newsletter box.`,
        "You supply that information so the firm can decide whether it can act, or so it can send the updates you opted in to. Supplying it is voluntary. If you do not, the firm may not be able to respond.",
        `The firm's address is on the contact page.${officer ? ` Information officer: ${officer}.` : ""} You may ask for access, correction, or deletion, and you may complain to the Information Regulator in South Africa.`,
        "This is a starting notice for the demo. It is not a finished POPIA section 18 notice.",
      ],
    },
    cookies: {
      title: "Cookie policy",
      paragraphs: [
        "The public site stores one choice on this browser: whether you picked Accept or Necessary only.",
        "The demo does not load advertising or analytics scripts, either way. Staff sign-in uses separate storage on the practice desk, not on this marketing site.",
      ],
    },
    "paia-manual": {
      title: "PAIA manual",
      paragraphs: [
        "A private body keeps a section 51 manual and makes it available on its website, at its principal office, and on request.",
        officer
          ? `Ask ${officer} for the manual. This page is a placeholder, not the manual itself.`
          : "This page is a placeholder, not the manual itself. Ask the firm for a copy.",
      ],
    },
    terms: {
      title: "Website terms",
      paragraphs: [
        "The pages on this site are general information. They are not legal advice and they are not a mandate.",
        "The firm does not claim to be superior to any other practice. Client names are not published here without written consent.",
      ],
    },
    accessibility: {
      title: "Accessibility",
      paragraphs: [
        "The firm aims at WCAG 2.2 AA: text you can read, a skip link, labels on forms, and a visible focus state.",
        "If something on this site blocks you, use the contact page and say what failed.",
      ],
    },
    "fraud-warning": {
      title: "Fraud warning",
      paragraphs: [
        `${site.firmName} will not change its banking details by email.`,
        "If you receive a message that asks you to pay a new account, phone the office on the number published on this site before you pay.",
      ],
    },
  };
  return pages[doc] || {
    title: "Not found",
    paragraphs: ["That legal page is not on this site."],
  };
}

function ContactBlock({ site, base, person }: { site: PublicSite; base: string; person?: PublicPerson }) {
  return (
    <aside className="border border-[#e6e0d6] bg-[#fffcf8] p-5 text-sm">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#78716c]">Contact</p>
      {person ? <p className="mt-2 font-semibold text-[#1c1917]">{person.fullName}, {person.title}</p> : null}
      {site.phone ? <p className="mt-2"><a href={`tel:${site.phone.replace(/\s/g, "")}`}>{site.phone}</a></p> : null}
      <p className="mt-2"><Link href={href(base, "/contact")} className="underline">Enquiry page</Link></p>
    </aside>
  );
}
