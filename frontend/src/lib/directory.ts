export type Option = { slug: string; label: string };
export type Catalog = {
  practiceAreas: Option[];
  provinces: Option[];
  cities: { slug: string; label: string; province: string }[];
  languages: Option[];
  consultOptions: Option[];
  responseTimes: Option[];
};
export type FirmCard = {
  slug: string;
  firmName: string;
  practiceAreas: string[];
  cities: string[];
  languages: string[];
  consultOptions: string[];
  responseTime?: string | null;
  verifiedPractitioners: number;
};
export type SearchResult = { results: FirmCard[]; total: number; page: number; pageSize: number; order: string; catalog: Catalog };
export type FirmPage = FirmCard & {
  practiceAreaOptions: Option[];
  provinces: string[];
  bio: string;
  fixedConsultFee?: number | null;
  phone?: string | null;
  practitioners: { fullName: string; lpcNumber: string; verifiedOn?: string | null }[];
  publicSite?: string | null;
  lpcRegisterUrl: string;
  consentVersion: string;
  consentText: string;
};

/** Public calls: no session, no token. */
export async function publicFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, headers: { "Content-Type": "application/json", ...(init?.headers || {}) } });
  let body: { success?: boolean; message?: string; data: T } | null = null;
  try {
    body = await res.json();
  } catch {
    throw new Error(res.status >= 500 ? "The service is not responding. Try again in a moment." : `Request failed (${res.status})`);
  }
  if (!res.ok || !body || body.success === false) throw new Error(body?.message || `Request failed (${res.status})`);
  return body.data;
}
