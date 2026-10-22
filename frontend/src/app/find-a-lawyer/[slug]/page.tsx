import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AreaLanding } from "@/components/area-landing";
import { FirmListing } from "@/components/firm-listing";
import { JsonLd } from "@/components/json-ld";
import { SITE_URL, getCatalog, getFirm, searchAll } from "@/lib/seo";

export const revalidate = 3600;

type Props = { params: Promise<{ slug: string }> };

/** Practice-area slugs win over firm slugs at this level; firm slugs are checked against them on sign-up. */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const catalog = await getCatalog();
  const area = catalog?.practiceAreas.find((a) => a.slug === slug);
  if (area) {
    const firms = await searchAll({ area: slug }, 1);
    const title = `${area.label} lawyers in South Africa`;
    const description = `Find South African law firms that handle ${area.label.toLowerCase()} matters, by city and language. LPC-checked practitioners. Listings rotate daily.`;
    return {
      title,
      description,
      alternates: { canonical: `/find-a-lawyer/${slug}` },
      openGraph: { title: `${title} | Owlaw`, description, url: `/find-a-lawyer/${slug}` },
      robots: firms.length ? undefined : { index: false, follow: true },
    };
  }
  const firm = await getFirm(slug);
  if (!firm) return { title: "Find a Lawyer", robots: { index: false, follow: true } };
  const places = firm.cities.map((c) => catalog?.cities.find((x) => x.slug === c)?.label || c).slice(0, 3).join(", ");
  const areas = firm.practiceAreaOptions.map((o) => o.label).slice(0, 3).join(", ");
  const title = `${firm.firmName}${places ? `, ${places}` : ""}`;
  const description = (firm.bio?.trim() || `${firm.firmName} is listed on Owlaw Find a Lawyer${areas ? ` for ${areas.toLowerCase()}` : ""}${places ? ` in ${places}` : ""}.`).slice(0, 160);
  return {
    title,
    description,
    alternates: { canonical: `/find-a-lawyer/${slug}` },
    openGraph: { type: "profile", title: `${title} | Owlaw`, description, url: `/find-a-lawyer/${slug}` },
  };
}

export default async function Page({ params }: Props) {
  const { slug } = await params;
  const catalog = await getCatalog();
  if (catalog?.practiceAreas.some((a) => a.slug === slug)) {
    return <AreaLanding catalog={catalog} area={slug} firms={await searchAll({ area: slug })} />;
  }
  const firm = await getFirm(slug);
  const ld = firm
    ? {
        "@context": "https://schema.org",
        "@type": "LegalService",
        name: firm.firmName,
        url: `${SITE_URL}/find-a-lawyer/${slug}`,
        ...(firm.bio ? { description: firm.bio.slice(0, 500) } : {}),
        ...(firm.phone ? { telephone: firm.phone } : {}),
        ...(firm.publicSite && firm.publicSite.startsWith("http") ? { sameAs: [firm.publicSite] } : {}),
        areaServed: firm.cities.map((c) => ({ "@type": "City", name: catalog?.cities.find((x) => x.slug === c)?.label || c })),
        knowsAbout: firm.practiceAreaOptions.map((o) => o.label),
        knowsLanguage: firm.languages.map((l) => catalog?.languages.find((x) => x.slug === l)?.label || l),
        employee: firm.practitioners.map((p) => ({ "@type": "Attorney", name: p.fullName })),
      }
    : null;
  if (!firm && catalog === null) {
    // API asleep: keep the client page, which retries in the browser.
    return <FirmListing />;
  }
  if (!firm) notFound();
  return (
    <>
      {ld ? <JsonLd data={ld} /> : null}
      <FirmListing />
    </>
  );
}
