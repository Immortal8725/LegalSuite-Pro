const TOKEN_KEY = "ls_access";
const REFRESH_KEY = "ls_refresh";
const USER_KEY = "ls_user";
const TENANT_KEY = "ls_tenant";

export type User = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  initials: string;
  role: string;
  title?: string;
  phone?: string;
  hourlyRate?: number;
  onlineStatus?: string;
  totpEnabled?: boolean;
};

export type Tenant = {
  id: string;
  firmName: string;
  slug: string;
  planSlug?: string;
  status?: string;
  onboardingCompleted?: boolean;
  enabledModules?: string[];
  phone?: string;
  email?: string;
  website?: string;
  addressLine1?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
  jurisdiction?: string;
  currency?: string;
  trustLabel?: string;
  tagline?: string;
  practiceAreas?: string[];
  ffcNumber?: string;
  ffcExpiresOn?: string;
  ffcHolderName?: string;
  ffcCurrent?: boolean;
  bankFeedImportedAt?: string;
  lastBankFeedSource?: string;
  informationOfficerName?: string;
  informationOfficerEmail?: string;
  paiaManualBody?: string;
  popiaOperatorAcknowledged?: boolean;
  popiaReady?: boolean;
};

type ApiEnvelope<T> = { success: boolean; message?: string; data: T; errors?: unknown };

export function getToken() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setSession(payload: { accessToken: string; refreshToken?: string; user: User; tenant: Tenant }) {
  localStorage.setItem(TOKEN_KEY, payload.accessToken);
  if (payload.refreshToken) localStorage.setItem(REFRESH_KEY, payload.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(payload.user));
  localStorage.setItem(TENANT_KEY, JSON.stringify(payload.tenant));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(TENANT_KEY);
}

export function readUser(): User | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as User) : null;
}

export function readTenant(): Tenant | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(TENANT_KEY);
  return raw ? (JSON.parse(raw) as Tenant) : null;
}

async function parse<T>(res: Response): Promise<T> {
  const json = (await res.json()) as ApiEnvelope<T>;
  if (!res.ok || json.success === false) {
    throw new Error(json.message || `Request failed (${res.status})`);
  }
  return json.data;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(path, { ...init, headers });
  const skipAuthRedirect =
    path.includes("/landing/") ||
    path.includes("/api/v1/public/") ||
    /\/intake\/[^/]+$/.test(path) ||
    path.includes("/api/v1/sign/");
  if (res.status === 401 && !path.includes("/auth/login") && !skipAuthRedirect) {
    const refresh = localStorage.getItem(REFRESH_KEY);
    if (refresh) {
      const r = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh }),
      });
      if (r.ok) {
        const body = (await r.json()) as ApiEnvelope<{ accessToken: string; refreshToken: string; user: User; tenant: Tenant }>;
        setSession(body.data);
        headers.set("Authorization", `Bearer ${body.data.accessToken}`);
        const retry = await fetch(path, { ...init, headers });
        return parse<T>(retry);
      }
    }
    clearSession();
    if (typeof window !== "undefined") window.location.href = "/login";
  }
  return parse<T>(res);
}

export const apiGet = <T>(path: string) => api<T>(path);
export const apiPost = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "POST", body: body instanceof FormData ? body : JSON.stringify(body ?? {}) });
export const apiPut = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "PUT", body: JSON.stringify(body ?? {}) });
export const apiPatch = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "PATCH", body: JSON.stringify(body ?? {}) });
