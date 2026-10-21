"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { fmtDate } from "@/components/platform";

type View = { id: string; createdAt: string; actorEmail?: string; detail?: string };
type Grant = {
  id: string;
  reason: string;
  grantedByEmail?: string;
  createdAt: string;
  expiresAt: string;
  revokedAt?: string | null;
  revokedByEmail?: string | null;
  active: boolean;
  views: View[];
};
type Data = { grants: Grant[]; maxDays: number; active: boolean };

export default function SupportAccessPage() {
  const [data, setData] = useState<Data | null>(null);
  const [days, setDays] = useState("3");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiGet<Data>("/api/v1/support-access")
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load support access."));
  }, []);
  useEffect(load, [load]);

  async function grant(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      setData(await apiPost<Data>("/api/v1/support-access", { days: Number(days), reason }));
      setReason("");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not grant access.");
    }
  }

  async function revoke(id: string) {
    setError(null);
    try {
      setData(await apiPost<Data>(`/api/v1/support-access/${id}/revoke`, {}));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not revoke access.");
    }
  }

  return (
    <div>
      <PageHeader
        title="Owlaw support access"
        subtitle="Owlaw staff cannot see your clients or matters. If you need help, you can let Owlaw support look, read-only, for up to 14 days. Every view is listed here and you can revoke access at any time."
      />
      <ErrorBanner error={error} />
      {data && !data.active ? (
        <Card className="mb-4">
          <CardHeader>
            <CardTitle>Grant read-only access</CardTitle>
          </CardHeader>
          <CardBody>
            <form onSubmit={grant} className="grid gap-3 sm:grid-cols-[140px_1fr_auto] sm:items-end">
              <div className="flex flex-col gap-1">
                <Label htmlFor="days">How long</Label>
                <Select id="days" value={days} onChange={(e) => setDays(e.target.value)}>
                  {[1, 2, 3, 5, 7, 10, 14].map((d) => (
                    <option key={d} value={d}>
                      {d} {d === 1 ? "day" : "days"}
                    </option>
                  ))}
                </Select>
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="reason">Why Owlaw support needs to look</Label>
                <Input id="reason" required minLength={5} placeholder="For example: invoice totals on matter C-2001 look wrong" value={reason} onChange={(e) => setReason(e.target.value)} />
              </div>
              <Button type="submit">Grant access</Button>
            </form>
            <p className="mt-2 text-xs text-slate-500">Support can see your matter list, client list and team. They cannot change anything.</p>
          </CardBody>
        </Card>
      ) : null}
      <div className="space-y-4">
        {data?.grants.map((g) => (
          <Card key={g.id} data-testid="grant">
            <CardBody className="space-y-2 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={g.active ? "green" : "slate"}>{g.active ? "Active" : g.revokedAt ? "Revoked" : "Ended"}</Badge>
                <span className="font-semibold">{g.reason}</span>
              </div>
              <p className="text-slate-600">
                Granted by {g.grantedByEmail} on {fmtDate(g.createdAt, true)}. {g.active ? "Ends" : "Ended"} {fmtDate(g.revokedAt || g.expiresAt, true)}
                {g.revokedByEmail ? `, revoked by ${g.revokedByEmail}` : ""}.
              </p>
              {g.active ? (
                <Button variant="outline" onClick={() => revoke(g.id)}>
                  Revoke now
                </Button>
              ) : null}
              <div>
                <p className="font-semibold">Views by Owlaw support ({g.views.length})</p>
                {g.views.length === 0 ? <p className="text-slate-500">None yet.</p> : null}
                <ul className="mt-1 space-y-1">
                  {g.views.map((v) => (
                    <li key={v.id} className="text-slate-700">
                      {fmtDate(v.createdAt, true)} · {v.actorEmail} · {v.detail}
                    </li>
                  ))}
                </ul>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
