"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { fmtDate, OperatorOnly } from "@/components/platform";

type Grant = { id: string; tenantId: string; firmName: string; reason: string; grantedByEmail?: string; expiresAt: string };
type Look = { section: string; firmName: string; rows?: Record<string, unknown>[]; [k: string]: unknown };

export default function PlatformSupportPage() {
  const { user } = useAuth();
  const [grants, setGrants] = useState<Grant[]>([]);
  const [look, setLook] = useState<Look | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    apiGet<Grant[]>("/api/v1/platform/support")
      .then(setGrants)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load support access."));
  }, []);
  useEffect(() => {
    if (user?.role === "superadmin") load();
  }, [user, load]);

  async function open(g: Grant, section: string) {
    setError(null);
    try {
      setLook(await apiGet<Look>(`/api/v1/platform/support/${g.tenantId}/${section}`));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not open.");
      load();
    }
  }

  if (user?.role !== "superadmin") return <OperatorOnly title="Support access" />;

  const rows = look?.rows || [];
  const cols = rows.length ? Object.keys(rows[0]) : [];

  return (
    <div>
      <PageHeader
        title="Support access"
        subtitle="Firms that have granted Owlaw read-only access. You can look only while a grant is live. Every view is logged and shown to the firm."
      />
      <ErrorBanner error={error} />
      {grants.length === 0 ? <p className="text-sm text-slate-600">No firm has granted support access right now.</p> : null}
      <div className="space-y-3">
        {grants.map((g) => (
          <Card key={g.id} data-testid="support-grant">
            <CardHeader>
              <CardTitle>{g.firmName}</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2 text-sm">
              <p>
                {g.reason} · granted by {g.grantedByEmail} · ends {fmtDate(g.expiresAt, true)}
              </p>
              <div className="flex gap-2">
                {["overview", "matters", "clients"].map((s) => (
                  <Button key={s} variant="outline" onClick={() => open(g, s)}>
                    View {s}
                  </Button>
                ))}
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
      {look ? (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>
              {look.firmName}: {look.section} (read-only, this view was logged)
            </CardTitle>
          </CardHeader>
          <CardBody className="text-sm">
            {look.section === "overview" ? (
              <div className="space-y-1">
                <p>
                  {String(look.matters)} matters ({String(look.openMatters)} open), {String(look.clients)} clients
                </p>
                <ul className="list-disc pl-5">
                  {((look.users as { name: string; role: string; status: string }[]) || []).map((u) => (
                    <li key={u.name + u.role}>
                      {u.name}, {u.role} ({u.status})
                    </li>
                  ))}
                </ul>
              </div>
            ) : rows.length === 0 ? (
              <p className="text-slate-600">Nothing here.</p>
            ) : (
              <table className="w-full">
                <thead className="text-left text-xs uppercase text-slate-500">
                  <tr>
                    {cols.map((c) => (
                      <th key={c} className="p-2">
                        {c}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={i} className="border-t">
                      {cols.map((c) => (
                        <td key={c} className="p-2">
                          {String(r[c] ?? "")}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>
      ) : null}
    </div>
  );
}
