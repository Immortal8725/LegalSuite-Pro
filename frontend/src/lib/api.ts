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
  mfaRequired?: boolean;
  recoveryCodesLeft?: number;
  mustChangePassword?: boolean;
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
  let json: ApiEnvelope<T>;
  try {
    json = (await res.json()) as ApiEnvelope<T>;
  } catch {
    // A proxy or gateway error page, not the API's JSON.
    throw new Error(
      res.status === 429
        ? "Too many attempts. Wait a few minutes and try again."
        : res.status >= 500
          ? "The server is not responding. Try again in a moment."
          : `Request failed (${res.status})`
    );
  }
  if (!res.ok || json.success === false) {
    const code = (json as { code?: string }).code;
    if (typeof window !== "undefined" && code === "firm_suspended") {
      clearSession();
      sessionStorage.setItem("ls_notice", json.message || "This firm's Owlaw account is suspended.");
      if (!window.location.pathname.startsWith("/login")) window.location.assign("/login");
    }
    if (typeof window !== "undefined" && code === "mfa_required" && !window.location.pathname.startsWith("/two-factor")) {
      window.location.assign("/two-factor");
    }
    throw new Error(json.message || `Request failed (${res.status})`);
  }
  return json.data;
}

let refreshing: Promise<string | null> | null = null;

/**
 * Exchange the refresh token for a new session. Refresh tokens are single use, so concurrent
 * 401s share one refresh instead of racing (the losers would otherwise sign the user out).
 */
function refreshAccess(): Promise<string | null> {
  if (!refreshing) {
    refreshing = (async () => {
      const refresh = localStorage.getItem(REFRESH_KEY);
      if (!refresh) return null;
      try {
        const r = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: refresh }),
        });
        if (r.ok) {
          const body = (await r.json()) as ApiEnvelope<{ accessToken: string; refreshToken: string; user: User; tenant: Tenant }>;
          setSession(body.data);
          return body.data.accessToken;
        }
      } catch {
        /* network error: treat as not refreshed */
      }
      // Another tab may have rotated the token while this request was in flight.
      const now = localStorage.getItem(REFRESH_KEY);
      return now && now !== refresh ? getToken() : null;
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** fetch with the access token; on a 401 it refreshes the session once and retries. */
async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(path, { ...init, headers });
  const skipAuthRedirect =
    path.includes("/landing/") ||
    path.includes("/api/v1/public/") ||
    /\/intake\/[^/]+$/.test(path) ||
    path.includes("/api/v1/sign/") ||
    path.includes("/api/v1/auth/invite/");
  if (res.status === 401 && !path.includes("/auth/login") && !skipAuthRedirect) {
    // If another request already refreshed, just retry with the new token.
    const current = getToken();
    const fresh = current && current !== token ? current : await refreshAccess();
    if (fresh) {
      headers.set("Authorization", `Bearer ${fresh}`);
      return fetch(path, { ...init, headers });
    }
    clearSession();
    if (typeof window !== "undefined") {
      window.location.href = window.location.pathname.startsWith("/portal") ? "/portal/login" : "/login";
    }
  }
  return res;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  return parse<T>(await authedFetch(path, { ...init, headers }));
}

/** Download a protected file (documents, payment proofs). Throws with the server's message on failure. */
export async function apiBlob(path: string): Promise<Blob> {
  const res = await authedFetch(path);
  if (!res.ok) {
    let detail = res.status === 404 ? "That file is no longer available." : "Could not open the file.";
    try {
      const body = (await res.json()) as { message?: string };
      if (body.message) detail = body.message;
    } catch {
      /* not JSON */
    }
    throw new Error(detail);
  }
  return res.blob();
}

/** POST JSON and receive a file (document previews). */
export async function apiPostBlob(path: string, body: unknown): Promise<Blob> {
  const res = await authedFetch(path, { method: "POST", body: JSON.stringify(body ?? {}) });
  if (!res.ok) {
    let detail = "Could not build the preview.";
    try {
      const j = (await res.json()) as { message?: string };
      if (j.message) detail = j.message;
    } catch {
      /* not JSON */
    }
    throw new Error(detail);
  }
  return res.blob();
}

/** Save a blob under a file name, then release the object URL. */
export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export const apiGet = <T>(path: string) => api<T>(path);
export const apiPost = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "POST", body: body instanceof FormData ? body : JSON.stringify(body ?? {}) });
export const apiPut = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "PUT", body: JSON.stringify(body ?? {}) });
export const apiPatch = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: "PATCH", body: JSON.stringify(body ?? {}) });
