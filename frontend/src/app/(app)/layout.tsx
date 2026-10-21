"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";
import { getToken } from "@/lib/api";

/**
 * Staff workspace guard. Signed-out visitors go to the sign-in page, portal clients go to the
 * portal, and invited users must set their own password first. Nothing in the workspace renders
 * until the session has been checked.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { ready, user } = useAuth();
  const router = useRouter();
  const signedIn = ready && !!user && !!getToken();
  const target = !ready
    ? null
    : !signedIn
      ? "/login"
      : user?.role === "client"
        ? "/portal"
        : user?.mustChangePassword
          ? "/change-password"
          : user?.mfaRequired && !user?.totpEnabled
            ? "/two-factor"
            : null;

  useEffect(() => {
    if (target) router.replace(target);
  }, [target, router]);

  if (!ready || target) return <div className="p-10 text-sm text-slate-600">Loading workspace…</div>;
  return <AppShell>{children}</AppShell>;
}
