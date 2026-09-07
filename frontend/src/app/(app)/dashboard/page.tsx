"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { apiGet } from "@/lib/api";
import { money } from "@/lib/utils";
import { Loading, PageHeader, StatusBadge } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate, formatDateTime } from "@/lib/utils";
import { PrivilegeStrip } from "@/components/privilege-strip";
import type { Dashboard, DocketItem } from "@/lib/types";

const URGENCY: Record<string, string> = {
  overdue: "bg-red-50 text-red-800 border-red-200",
  soon: "bg-amber-50 text-amber-900 border-amber-200",
  watch: "bg-sky-50 text-sky-800 border-sky-200",
  ok: "bg-slate-50 text-slate-600 border-slate-200",
};

export default function DashboardPage() {
  const { user, tenant } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Dashboard>("/api/v1/dashboard")
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return <Loading />;

  const docket: DocketItem[] = data.docket || [];

  return (
    <div>
      <PageHeader
        title="Docket"
        subtitle={`${user?.firstName}, the clock is the product. SOL, hearings, and consults that have not been retained yet.`}
        actions={
          <div className="flex gap-2">
            <Link href="/leads" className="rounded-lg border px-4 py-2 text-sm font-bold text-navy">
              Hire queue {data.newLeads ? `(${data.newLeads})` : ""}
            </Link>
            <Link href="/usage" className="rounded-lg bg-gold px-4 py-2 text-sm font-bold text-navy-dark">
              Month-end bill
            </Link>
          </div>
        }
      />
      <PrivilegeStrip />
      <div className="mt-4 mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="p-5">
          <p className="text-xs text-slate-500">Active matters</p>
          <p className="mt-1 text-2xl font-extrabold text-navy">{data.activeCases}</p>
        </Card>
        <Card className="p-5">
          <p className="text-xs text-slate-500">Outstanding</p>
          <p className="mt-1 text-2xl font-extrabold text-amber-700">{money(data.outstanding)}</p>
        </Card>
        <Card className="p-5">
          <p className="text-xs text-slate-500">Website consults waiting</p>
          <p className="mt-1 text-2xl font-extrabold text-navy">{data.newLeads ?? 0}</p>
        </Card>
        <Card className="p-5">
          <p className="text-xs text-slate-500">Calls (in-app free)</p>
          <p className="mt-1 text-2xl font-extrabold text-navy">{data.callCount}</p>
        </Card>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>What blows up if you ignore it</CardTitle>
          <Link href="/calendar" className="text-sm text-navy">
            Full calendar
          </Link>
        </CardHeader>
        <CardBody className="space-y-3">
          {docket.length === 0 && <p className="text-sm text-slate-500">No statute or filing in the next 45 days. Stay that way.</p>}
          {docket.map((item, i) => (
            <Link
              key={`${item.kind}-${item.caseId}-${i}`}
              href={item.caseId ? `/cases/${item.caseId}` : "/calendar"}
              className={`block rounded-xl border p-4 ${URGENCY[item.urgency] || URGENCY.ok}`}
            >
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="text-[11px] font-bold uppercase tracking-wide">{item.kind === "sol" ? "Statute of limitations" : item.label}</p>
                <p className="font-mono text-sm font-bold">
                  {item.daysLeft < 0 ? `${Math.abs(item.daysLeft)} days overdue` : `${item.daysLeft} days`}
                </p>
              </div>
              <p className="mt-1 font-semibold">
                {item.caseNumber ? `${item.caseNumber} · ` : ""}
                {item.title}
              </p>
              <p className="text-xs opacity-80">{item.kind === "sol" ? formatDate(item.date) : formatDateTime(item.date)}</p>
            </Link>
          ))}
        </CardBody>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Recent matters</CardTitle>
            <Link href="/cases" className="text-sm text-navy">
              All
            </Link>
          </CardHeader>
          <CardBody className="space-y-3">
            {data.recentCases.map((c) => (
              <Link key={c.id} href={`/cases/${c.id}`} className="flex items-center justify-between gap-3 text-sm">
                <span>
                  <span className="font-mono text-navy">{c.caseNumber}</span> {c.title}
                </span>
                <StatusBadge status={c.status} />
              </Link>
            ))}
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>How work enters</CardTitle>
          </CardHeader>
          <CardBody className="text-sm text-slate-600">
            <ol className="list-decimal space-y-2 pl-4">
              <li>Public site {tenant?.slug ? <Link className="font-semibold text-navy" href={`/firm/${tenant.slug}`}>/{tenant.slug}</Link> : null} takes the consult.</li>
              <li>Conflicts run before a file opens.</li>
              <li>Engagement merges and goes out for signature.</li>
              <li>Retainer hits IOLTA. The call is on the docket; hangup writes time.</li>
            </ol>
            <Link href="/leads" className="mt-4 inline-block font-semibold text-navy">
              Open the hire pipeline →
            </Link>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
