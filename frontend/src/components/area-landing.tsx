import Link from "next/link";
import type { Catalog, FirmCard } from "@/lib/directory";

/** Area or area-plus-city landing page. Firms appear in the API's daily rotation order: no ranking, ratings or paid placement. */
export function AreaLanding({ catalog, area, city, firms }: { catalog: Catalog; area: string; city?: string; firms: FirmCard[] }) {
  const areaLabel = catalog.practiceAreas.find((a) => a.slug === area)?.label || area;
  const cityRow = city ? catalog.cities.find((c) => c.slug === city) : undefined;
  const label = (list: { slug: string; label: string }[], slug: string) => list.find((o) => o.slug === slug)?.label || slug;
  const where = cityRow ? ` in ${cityRow.label}` : " in South Africa";
  const search = `/find-a-lawyer?area=${area}${cityRow ? `&city=${cityRow.slug}&province=${cityRow.province}` : ""}`;
  return (
    <div>
      <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
        <Link href="/find-a-lawyer" className="hover:text-navy">Find a Lawyer</Link> /{" "}
        {cityRow ? <Link href={`/find-a-lawyer/${area}`} className="hover:text-navy">{areaLabel}</Link> : <span>{areaLabel}</span>}
        {cityRow ? <> / <span>{cityRow.label}</span></> : null}
      </nav>
      <h1 className="mt-3 text-3xl font-extrabold text-navy">
        {areaLabel} lawyers{where}
      </h1>
      <p className="mt-2 max-w-3xl text-slate-600">
        Law firms listed on Owlaw that handle {areaLabel.toLowerCase()} matters{where}. Every firm listed has at least one practitioner whose Legal
        Practice Council number Owlaw has checked. Listings rotate daily. Owlaw does not rate or rank firms and firms do not pay for placement.
      </p>

      <h2 className="mt-8 text-xl font-bold text-navy">Listed firms</h2>
      {firms.length === 0 ? (
        <p className="mt-3 rounded-xl border bg-white p-5 text-slate-700">
          No firms are listed here yet. <Link href={search} className="font-semibold text-sky-700">Search the full directory</Link> for nearby options.
        </p>
      ) : (
        <ul className="mt-3 grid gap-4 sm:grid-cols-2">
          {firms.map((f) => (
            <li key={f.slug} className="rounded-2xl border bg-white p-5 shadow-sm">
              <h3 className="text-lg font-bold text-navy">
                <Link href={`/find-a-lawyer/${f.slug}`} className="hover:underline">{f.firmName}</Link>
              </h3>
              <p className="mt-1 text-sm text-slate-600">{f.practiceAreas.map((a) => label(catalog.practiceAreas, a)).join(", ")}</p>
              <p className="mt-1 text-sm text-slate-500">{f.cities.map((c) => label(catalog.cities, c)).join(", ")}</p>
              {f.languages.length ? <p className="mt-1 text-sm text-slate-500">Languages: {f.languages.map((l) => label(catalog.languages, l)).join(", ")}</p> : null}
            </li>
          ))}
        </ul>
      )}

      <h2 className="mt-10 text-xl font-bold text-navy">{areaLabel} lawyers by city</h2>
      <ul className="mt-3 flex flex-wrap gap-2 text-sm">
        {catalog.cities.map((c) => (
          <li key={c.slug}>
            <Link href={`/find-a-lawyer/${area}/${c.slug}`} className="inline-block rounded-full border bg-white px-3 py-1 text-slate-700 hover:text-navy">
              {c.label}
            </Link>
          </li>
        ))}
      </ul>

      <h2 className="mt-8 text-xl font-bold text-navy">Other types of lawyer{cityRow ? ` in ${cityRow.label}` : ""}</h2>
      <ul className="mt-3 flex flex-wrap gap-2 text-sm">
        {catalog.practiceAreas
          .filter((a) => a.slug !== area)
          .map((a) => (
            <li key={a.slug}>
              <Link href={`/find-a-lawyer/${a.slug}${cityRow ? `/${cityRow.slug}` : ""}`} className="inline-block rounded-full border bg-white px-3 py-1 text-slate-700 hover:text-navy">
                {a.label}
              </Link>
            </li>
          ))}
      </ul>
    </div>
  );
}
