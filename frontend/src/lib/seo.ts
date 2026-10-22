import type { Catalog, FirmCard, FirmPage, SearchResult } from "@/lib/directory";

/** The canonical public origin of the main site. */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || "https://owlaw.co.za").replace(/\/+$/, "");
const API = (process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:18081").replace(/\/+$/, "");

/** Server-side public API read. Returns null on any failure so pages and the sitemap still render while the API wakes up. */
export async function apiRead<T>(path: string, revalidate = 3600, timeoutMs = 8000): Promise<T | null> {
  try {
    const res = await fetch(`${API}${path}`, { next: { revalidate }, signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) return null;
    const body = (await res.json()) as { success?: boolean; data?: T };
    return body && body.success !== false && body.data ? body.data : null;
  } catch {
    return null;
  }
}

export const getCatalog = () => apiRead<Catalog>("/api/v1/public/directory/catalog", 86400);
export const getFirm = (slug: string) => apiRead<FirmPage>(`/api/v1/public/directory/firms/${encodeURIComponent(slug)}`);

/** Every listed firm, in the API's own daily rotation order. Owlaw does not rank firms. */
export async function searchAll(params: Record<string, string> = {}, maxPages = 10): Promise<FirmCard[]> {
  const out: FirmCard[] = [];
  for (let page = 1; page <= maxPages; page++) {
    const q = new URLSearchParams({ ...params, page: String(page) });
    const r = await apiRead<SearchResult>(`/api/v1/public/directory/search?${q}`);
    if (!r) break;
    out.push(...r.results);
    if (r.results.length === 0 || page * r.pageSize >= r.total) break;
  }
  return out;
}

export function jsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
