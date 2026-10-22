import { Source_Sans_3, Source_Serif_4 } from "next/font/google";
import type { Metadata } from "next";
import { PublicSiteRoot } from "@/components/public-site/site-context";
import { JsonLd } from "@/components/json-ld";
import { SITE_URL, apiRead } from "@/lib/seo";

type Site = {
  published?: boolean;
  slug: string;
  siteUrl?: string;
  firmName: string;
  tagline?: string;
  about?: string;
  phone?: string;
  email?: string;
  addressLine1?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
  practices?: { label?: string; title?: string; name?: string }[];
  people?: { name?: string; fullName?: string }[];
};

type Props = { params: Promise<{ slug: string }> };

const getSite = (slug: string) => apiRead<Site>(`/api/v1/public/sites/${encodeURIComponent(slug)}`);
const origin = (s: Site) => (s.siteUrl && s.siteUrl.startsWith("http") ? s.siteUrl : `${SITE_URL}/firm/${s.slug}`);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const site = await getSite(slug);
  if (!site || site.published === false) return { title: site?.firmName || "Law firm", robots: { index: false, follow: false } };
  const base = origin(site);
  const description = (site.tagline || site.about || `${site.firmName}${site.city ? `, attorneys in ${site.city}` : ""}.`).slice(0, 160);
  return {
    metadataBase: new URL(base.endsWith("/") ? base : `${base}/`),
    title: { default: site.firmName, template: `%s | ${site.firmName}` },
    description,
    alternates: { canonical: base },
    openGraph: { type: "website", siteName: site.firmName, title: site.firmName, description, url: base, locale: "en_ZA" },
  };
}

function siteLd(site: Site) {
  const name = (x: { label?: string; title?: string; name?: string; fullName?: string }) => x.label || x.title || x.name || x.fullName || "";
  return {
    "@context": "https://schema.org",
    "@type": "LegalService",
    name: site.firmName,
    url: origin(site),
    ...(site.about ? { description: site.about.slice(0, 500) } : {}),
    ...(site.phone ? { telephone: site.phone } : {}),
    ...(site.email ? { email: site.email } : {}),
    ...(site.addressLine1 || site.city
      ? {
          address: {
            "@type": "PostalAddress",
            streetAddress: site.addressLine1 || undefined,
            addressLocality: site.city || undefined,
            addressRegion: site.state || undefined,
            postalCode: site.zip || undefined,
            addressCountry: site.country || "ZA",
          },
        }
      : {}),
    knowsAbout: (site.practices || []).map(name).filter(Boolean),
    employee: (site.people || []).map(name).filter(Boolean).map((n) => ({ "@type": "Attorney", name: n })),
  };
}

const serif = Source_Serif_4({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-public-serif",
  display: "swap",
});

const sans = Source_Sans_3({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-public-sans",
  display: "swap",
});

export default async function FirmLayout({ children, params }: { children: React.ReactNode } & Props) {
  const { slug } = await params;
  const site = await getSite(slug);
  return (
    <div className={`${serif.variable} ${sans.variable}`}>
      {site && site.published !== false ? <JsonLd data={siteLd(site)} /> : null}
      <PublicSiteRoot>{children}</PublicSiteRoot>
    </div>
  );
}
