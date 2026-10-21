"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { OperatorOnly } from "@/components/platform";

type Inbox = {
  counts: { publicSites: number; practitioners: number; listings: number; hiddenListings: number; total: number };
  publicSites: { tenantId: string; firmName: string; kind?: string; label?: string }[];
  practitioners: { id: string; fullName: string; lpcNumber?: string; firmName?: string }[];
};

export default function ApprovalsPage() {
  const { user } = useAuth();
  const [d, setD] = useState<Inbox | null>(null);
  const [error, setError] = useState<string | null>(null);
  const allowed = user?.role === "superadmin" || user?.role === "support";
  useEffect(() => {
    if (allowed) apiGet<Inbox>("/api/v1/platform/approvals").then(setD).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
  }, [allowed]);
  if (!allowed) return <OperatorOnly title="Approvals" />;
  const tiles = d
    ? [
        { href: "/platform/sites", label: "Public site approvals", n: d.counts.publicSites },
        { href: "/platform/directory", label: "LPC practitioner verification", n: d.counts.practitioners },
        { href: "/platform/directory/listings", label: `Directory listings (${d.counts.hiddenListings} hidden)`, n: d.counts.listings },
      ]
    : [];
  return (
    <div>
      <PageHeader title="Approvals" subtitle={d ? `${d.counts.total} waiting. Every decision is written to the platform log and the firm's log.` : "Loading..."} />
      <ErrorBanner error={error} />
      <div className="grid gap-3 sm:grid-cols-3">
        {tiles.map((t) => (
          <Link key={t.href} href={t.href} className="rounded border bg-white p-4 hover:border-navy">
            <p className="text-3xl font-semibold">{t.n}</p>
            <p className="text-sm text-slate-600">{t.label}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
