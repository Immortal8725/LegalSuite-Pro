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

function clockKind(kind?: string, za?: boolean) {
  if (kind === "sol") return za ? "Prescription" : "Statute of limitations";
  if (kind === "notice") return za ? "Organ-of-state notice" : "Governmental notice";
  if (kind === "repose") return "Outer limit";
  if (kind === "raf_lodge") return "Lodge RAF 1";
  if (kind === "raf_summons") return "RAF summons";
  if (kind === "ccma") return "CCMA referral";
  if (kind === "inspection") return "L&D inspection";
  return kind || "";
}

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
  const za = data.jurisdiction === "ZA" || tenant?.country === "ZA";
  const recon = data.trustRecon;
  const unbalanced = recon?.worstStatus === "unbalanced";
  const trustName = data.trustLabel || tenant?.trustLabel || (za ? "section 86 trust" : "IOLTA");

  return (
    <div>
      <PageHeader
        title="Docket"
        subtitle={
          za
            ? `${user?.firstName}, South African clocks first. RAF, Act 40 notice, CCMA, prescription, and consults not yet mandated. The attorney remains responsible.`
            : `${user?.firstName}, Texas clocks first. Limitations, governmental notice, and consults that have not been retained yet. The attorney remains responsible.`
        }
        actions={
          <div className="flex gap-2">
            <Link href="/fitness" className="rounded-lg border px-4 py-2 text-sm font-bold text-navy">
              Practice fitness
            </Link>
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
      {unbalanced && (
        <Link
          href="/trust"
          className="mb-4 mt-4 block rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-950"
        >
          <p className="font-semibold">
            {za ? "LPA s 86 three-way is unbalanced." : "IOLTA three-way is unbalanced."} Do not certify.
          </p>
          <p className="mt-1">
            Bank vs cashbook vs client ledgers do not agree. Open the {trustName} ledger and enter the statement
            balance.
          </p>
        </Link>
      )}
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
                <p className="text-[11px] font-bold uppercase tracking-wide">{clockKind(item.kind, za) || item.label}</p>
                <p className="font-mono text-sm font-bold">
                  {item.daysLeft < 0 ? `${Math.abs(item.daysLeft)} days overdue` : `${item.daysLeft} days`}
                </p>
              </div>
              <p className="mt-1 font-semibold">
                {item.caseNumber ? `${item.caseNumber} · ` : ""}
                {item.title}
              </p>
              {item.citation && <p className="mt-1 text-[11px] font-semibold opacity-90">{item.citation}</p>}
              {item.reason && <p className="mt-1 text-xs opacity-80">{item.reason}</p>}
              <p className="text-xs opacity-80">
                {item.kind === "sol" ||
                item.kind === "notice" ||
                item.kind === "repose" ||
                item.kind === "raf_lodge" ||
                item.kind === "raf_summons" ||
                item.kind === "ccma" ||
                item.kind === "inspection"
                  ? formatDate(item.date)
                  : formatDateTime(item.date)}
              </p>
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
              <li>
                Public site{" "}
                {tenant?.slug ? (
                  <Link className="font-semibold text-navy" href={`/firm/${tenant.slug}`}>
                    /{tenant.slug}
                  </Link>
                ) : null}{" "}
                takes the consult.
              </li>
              <li>Conflicts run before a file opens.</li>
              <li>The mandate merges and goes out for signature.</li>
              <li>Retainer hits the {trustName}. The call is on the docket; hangup writes time.</li>
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
