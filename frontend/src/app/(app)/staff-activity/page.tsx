"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { fmtDate } from "@/components/platform";

type Row = { id: string; createdAt: string; actorEmail?: string; action: string; label: string; detail?: string };

export default function StaffActivityPage() {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Row[]>("/api/v1/staff-activity")
      .then(setRows)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load activity."));
  }, []);

  return (
    <div>
      <PageHeader
        title="Owlaw staff activity"
        subtitle="Everything an Owlaw operator did on your firm's account: plan or trial changes, suspensions, listing checks and every view made under support access you granted."
      />
      <ErrorBanner error={error} />
      {rows && rows.length === 0 ? <p className="text-sm text-slate-600">Owlaw staff have not taken any action on your account.</p> : null}
      {rows && rows.length > 0 ? (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="p-3">When (SAST)</th>
                <th className="p-3">Owlaw staff member</th>
                <th className="p-3">What happened</th>
                <th className="p-3">Detail</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-t align-top" data-testid="staff-activity-row">
                  <td className="whitespace-nowrap p-3">{fmtDate(r.createdAt, true)}</td>
                  <td className="p-3">{r.actorEmail || "Owlaw"}</td>
                  <td className="p-3 font-semibold">{r.label}</td>
                  <td className="p-3 text-slate-700">{r.detail || ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
