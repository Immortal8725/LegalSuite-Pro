"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import {
  User,
  Tenant,
  apiGet,
  apiPost,
  clearSession,
  getToken,
  readTenant,
  readUser,
  setSession,
} from "@/lib/api";

type AuthCtx = {
  user: User | null;
  tenant: Tenant | null;
  ready: boolean;
  isClient: boolean;
  login: (firmSlug: string, email: string, password: string, totpCode?: string) => Promise<{ requiresTotp: boolean }>;
  registerFirm: (payload: Record<string, unknown>) => Promise<void>;
  portalLogin: (firmSlug: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshTenant: (tenant: Tenant) => void;
  reloadMe: () => Promise<void>;
};

const Ctx = createContext<AuthCtx | null>(null);

const PUBLIC = new Set(["/", "/login", "/register", "/portal/login"]);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [ready, setReady] = useState(false);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    setUser(readUser());
    setTenant(readTenant());
    setReady(true);
  }, []);

  const apply = useCallback((data: { accessToken: string; refreshToken?: string; user: User; tenant: Tenant }) => {
    setSession(data);
    setUser(data.user);
    setTenant(data.tenant);
  }, []);

  const login = useCallback(
    async (firmSlug: string, email: string, password: string, totpCode?: string) => {
      const data = await apiPost<{
        requiresTotp?: boolean;
        accessToken?: string;
        refreshToken?: string;
        user?: User;
        tenant?: Tenant;
      }>("/api/v1/auth/login", { firmSlug, email, password, totpCode: totpCode || undefined });
      if (data.requiresTotp || !data.accessToken || !data.user || !data.tenant) {
        return { requiresTotp: true };
      }
      apply({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user,
        tenant: data.tenant,
      });
      const dest =
        data.user.role === "superadmin"
          ? "/platform/sites"
          : data.tenant.onboardingCompleted === false
            ? "/onboarding"
            : "/dashboard";
      router.push(dest);
      return { requiresTotp: false };
    },
    [apply, router]
  );

  const registerFirm = useCallback(
    async (payload: Record<string, unknown>) => {
      const data = await apiPost<{ accessToken: string; refreshToken: string; user: User; tenant: Tenant }>(
        "/api/v1/auth/register",
        payload
      );
      apply(data);
      router.push("/onboarding");
    },
    [apply, router]
  );

  const portalLogin = useCallback(
    async (firmSlug: string, email: string, password: string) => {
      const data = await apiPost<{
        accessToken: string;
        refreshToken?: string;
        user: User;
        tenant: Tenant;
      }>("/api/v1/portal/login", { firmSlug, email, password });
      apply(data);
      router.push("/portal");
    },
    [apply, router]
  );

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
    setTenant(null);
    router.push("/login");
  }, [router]);

  const refreshTenant = useCallback((next: Tenant) => {
    setTenant(next);
    if (typeof window !== "undefined") localStorage.setItem("ls_tenant", JSON.stringify(next));
  }, []);

  const reloadMe = useCallback(async () => {
    const data = await apiGet<{ user: User; tenant: Tenant }>("/api/v1/auth/me");
    setUser(data.user);
    if (typeof window !== "undefined") localStorage.setItem("ls_user", JSON.stringify(data.user));
    refreshTenant(data.tenant);
  }, [refreshTenant]);

  useEffect(() => {
    if (!ready) return;
    const isPublic =
      PUBLIC.has(pathname) ||
      pathname.startsWith("/firm/") ||
      pathname.startsWith("/sign/") ||
      pathname.startsWith("/legal");
    if (isPublic) return;
    if (!getToken()) {
      router.replace(pathname.startsWith("/portal") ? "/portal/login" : "/login");
    }
  }, [pathname, ready, router]);

  const value = useMemo(
    () => ({
      user,
      tenant,
      ready,
      isClient: user?.role === "client",
      login,
      registerFirm,
      portalLogin,
      logout,
      refreshTenant,
      reloadMe,
    }),
    [user, tenant, ready, login, registerFirm, portalLogin, logout, refreshTenant, reloadMe]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
