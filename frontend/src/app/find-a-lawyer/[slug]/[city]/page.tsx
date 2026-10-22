import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AreaLanding } from "@/components/area-landing";
import { getCatalog, searchAll } from "@/lib/seo";

export const revalidate = 3600;

type Props = { params: Promise<{ slug: string; city: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug, city } = await params;
  const catalog = await getCatalog();
  const area = catalog?.practiceAreas.find((a) => a.slug === slug);
  const c = catalog?.cities.find((x) => x.slug === city);
  if (!area || !c) return { title: "Find a Lawyer", robots: { index: false, follow: true } };
  const firms = await searchAll({ area: slug, city }, 1);
  const title = `${area.label} lawyers in ${c.label}`;
  const description = `Law firms in ${c.label} listed on Owlaw for ${area.label.toLowerCase()} matters. LPC-checked practitioners. Listings rotate daily and are not ranked.`;
  const url = `/find-a-lawyer/${slug}/${city}`;
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: { title: `${title} | Owlaw`, description, url },
    // Thin pages stay out of the index until a firm is listed there.
    robots: firms.length ? undefined : { index: false, follow: true },
  };
}

export default async function Page({ params }: Props) {
  const { slug, city } = await params;
  const catalog = await getCatalog();
  if (!catalog) notFound();
  const c = catalog.cities.find((x) => x.slug === city);
  if (!catalog.practiceAreas.some((a) => a.slug === slug) || !c) notFound();
  return <AreaLanding catalog={catalog} area={slug} city={city} firms={await searchAll({ area: slug, city })} />;
}
