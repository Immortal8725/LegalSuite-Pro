import { LEGAL_DOCS } from "@/lib/legal";
import { apiRead, getCatalog, searchAll } from "@/lib/seo";
import { requestSite } from "@/lib/seo-host";

export const dynamic = "force-dynamic";

type Entry = { loc: string; changefreq?: string; priority?: number };

const FIRM_PAGES: [string, string | null][] = [
  ["", null], ["/expertise", "practices"], ["/people", "people"], ["/insights", "insights"], ["/how-we-help", "situations"],
  ["/fees", "feesNote"], ["/recognition", "recognition"], ["/contact", null],
];

function xml(entries: Entry[]): string {
  const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
  const rows = entries.map(
    (e) => `  <url><loc>${esc(e.loc)}</loc>${e.changefreq ? `<changefreq>${e.changefreq}</changefreq>` : ""}${e.priority != null ? `<priority>${e.priority}</priority>` : ""}</url>`,
  );
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${rows.join("\n")}\n</urlset>\n`;
}

async function mainSite(origin: string): Promise<Entry[]> {
  const out: Entry[] = [
    { loc: `${origin}/`, changefreq: "weekly", priority: 1 },
    { loc: `${origin}/find-a-lawyer`, changefreq: "daily", priority: 0.9 },
    { loc: `${origin}/register`, changefreq: "monthly", priority: 0.6 },
    { loc: `${origin}/help`, changefreq: "monthly", priority: 0.5 },
    { loc: `${origin}/security`, changefreq: "monthly", priority: 0.4 },
    ...LEGAL_DOCS.map((d) => ({ loc: `${origin}/legal/${d.slug}`, changefreq: "yearly", priority: 0.2 })),
  ];
  const catalog = await getCatalog();
  const firms = await searchAll();
  if (catalog) {
    const areaHasFirm = new Set(firms.flatMap((f) => f.practiceAreas));
    const pairs = new Set(firms.flatMap((f) => f.practiceAreas.flatMap((a) => f.cities.map((c) => `${a}/${c}`))));
    for (const a of catalog.practiceAreas) {
      if (!areaHasFirm.has(a.slug)) continue;
      out.push({ loc: `${origin}/find-a-lawyer/${a.slug}`, changefreq: "daily", priority: 0.7 });
      for (const c of catalog.cities) {
        if (pairs.has(`${a.slug}/${c.slug}`)) out.push({ loc: `${origin}/find-a-lawyer/${a.slug}/${c.slug}`, changefreq: "daily", priority: 0.6 });
      }
    }
  }
  for (const f of firms) out.push({ loc: `${origin}/find-a-lawyer/${f.slug}`, changefreq: "weekly", priority: 0.5 });
  return out;
}

async function firmSite(origin: string, slug: string): Promise<Entry[]> {
  const site = await apiRead<Record<string, unknown>>(`/api/v1/public/sites/${encodeURIComponent(slug)}`);
  if (!site || site.published === false) return [];
  const has = (k: string | null) => {
    if (!k) return true;
    const v = site[k];
    return Array.isArray(v) ? v.length > 0 : Boolean(v);
  };
  return FIRM_PAGES.filter(([, k]) => has(k)).map(([p]) => ({ loc: `${origin}${p || "/"}`, changefreq: "weekly", priority: p ? 0.6 : 1 }));
}

export async function GET() {
  const { origin, firmSlug } = await requestSite();
  const entries = firmSlug ? await firmSite(origin, firmSlug) : await mainSite(origin);
  return new Response(xml(entries), { headers: { "Content-Type": "application/xml; charset=utf-8", "Cache-Control": "public, max-age=3600" } });
}
