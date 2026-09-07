"use client";

import { AppShell } from "@/components/app-shell";
import { useAuth } from "@/components/auth-provider";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { ready } = useAuth();
  if (!ready) return <div className="p-10 text-sm text-slate-500">Loading workspace…</div>;
  return <AppShell>{children}</AppShell>;
}
