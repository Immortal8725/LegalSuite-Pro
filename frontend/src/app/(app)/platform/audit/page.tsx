"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { fmtDate, OperatorOnly } from "@/components/platform";

type Row = { id: string; createdAt: string; actorEmail?: string; action: string; label: string; detail?: string; firmName?: string | null };

export default function PlatformAuditPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [action, setAction] = useState("");
  const [firm, setFirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback((a: string) => {
    setLoading(true);
    apiGet<Row[]>(`/api/v1/platform/audit${a ? `?action=${encodeURIComponent(a)}` : ""}`)
      .then(setRows)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load the log."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (user?.role === "superadmin") load("");
    else setLoading(false);
  }, [user, load]);

  if (user?.role !== "superadmin") return <OperatorOnly title="Platform audit log" />;
  const f = firm.trim().toLowerCase();
  const shown = rows.filter((r) => !f || (r.firmName || "").toLowerCase().includes(f));

  return (
    <div>
      <PageHeader title="Platform audit log" subtitle="Every action taken by Owlaw operator accounts, newest first. Actions on a firm also appear on that firm's Owlaw staff activity page." />
      <ErrorBanner error={error} />
      <form
        className="mb-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          load(action);
        }}
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="action">Action contains</Label>
          <Input id="action" placeholder="for example suspended, support, mfa" value={action} onChange={(e) => setAction(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="firm">Firm</Label>
          <Input id="firm" placeholder="Firm name" value={firm} onChange={(e) => setFirm(e.target.value)} />
        </div>
        <Button type="submit">Filter</Button>
        <span className="text-sm text-slate-600">{shown.length} entries (latest 500)</span>
      </form>
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      <div className="overflow-x-auto rounded-xl border bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th className="p-3">When (SAST)</th>
              <th className="p-3">Operator</th>
              <th className="p-3">Firm</th>
              <th className="p-3">Action</th>
              <th className="p-3">Detail</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((r) => (
              <tr key={r.id} className="border-t align-top" data-testid="audit-row">
                <td className="whitespace-nowrap p-3">{fmtDate(r.createdAt, true)}</td>
                <td className="p-3">{r.actorEmail || "-"}</td>
                <td className="p-3">{r.firmName || "Platform"}</td>
                <td className="p-3">
                  <div className="font-semibold">{r.label}</div>
                  <div className="font-mono text-xs text-slate-500">{r.action}</div>
                </td>
                <td className="p-3 text-slate-700">{r.detail || ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
