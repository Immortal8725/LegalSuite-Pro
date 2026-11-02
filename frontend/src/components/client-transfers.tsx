"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { formatDateTime, moneyExact } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { ADMIN_ROLES } from "@/lib/litigation";

type Transfer = {
  id: string;
  fromClientName: string;
  toClientName: string;
  amount: number;
  status: string;
  reason: string;
  requestedBy?: string;
  createdAt: string;
};

/** Trust transfers between client ledgers after a client correction. A second firm admin must approve. */
export function ClientTransfers() {
  const { user } = useAuth();
  const isAdmin = ADMIN_ROLES.includes(String(user?.role ?? ""));
  const [rows, setRows] = useState<Transfer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(() => apiGet<Transfer[]>("/api/v1/trust/client-transfers").then(setRows).catch(() => setRows([])), []);
  useEffect(() => {
    load();
  }, [load]);
  const pending = rows.filter((r) => r.status === "pending");
  if (pending.length === 0) return null;

  async function act(id: string, what: "approve" | "reject") {
    setBusy(id);
    setError(null);
    try {
      await apiPost(`/api/v1/trust/client-transfers/${id}/${what}`, {});
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update the transfer");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>Client ledger transfers waiting for approval</CardTitle>
      </CardHeader>
      <CardBody>
        <ErrorBanner error={error} />
        <p className="mb-3 text-sm text-slate-700">
          A matter was moved to the right client. The money stays in trust and moves from one client ledger to the other once a second firm
          admin approves. The person who asked cannot approve it.
        </p>
        <ul className="space-y-3">
          {pending.map((t) => {
            const mine = !!user?.id && user.id === t.requestedBy;
            return (
              <li key={t.id} className="rounded-lg border p-3 text-sm">
                <p className="font-semibold">
                  {moneyExact(t.amount)} from {t.fromClientName} to {t.toClientName}
                </p>
                <p className="text-slate-600">
                  {t.reason} · {formatDateTime(t.createdAt)}
                </p>
                <div className="mt-2 flex gap-2">
                  {isAdmin && !mine && (
                    <Button size="sm" disabled={busy === t.id} onClick={() => act(t.id, "approve")}>
                      Approve
                    </Button>
                  )}
                  {(isAdmin || mine) && (
                    <Button size="sm" variant="outline" disabled={busy === t.id} onClick={() => act(t.id, "reject")}>
                      Reject
                    </Button>
                  )}
                  {mine && <span className="self-center text-xs text-slate-600">You asked for this, so another admin must approve.</span>}
                </div>
              </li>
            );
          })}
        </ul>
      </CardBody>
    </Card>
  );
}
