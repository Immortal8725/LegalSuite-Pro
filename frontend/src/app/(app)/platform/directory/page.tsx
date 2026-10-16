"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label, Select, Textarea } from "@/components/ui/input";

type Row = {
  id: string;
  fullName: string;
  lpcNumber: string;
  fidelityExpiresOn?: string;
  fidelityCurrent: boolean;
  status: "pending" | "verified" | "rejected";
  verificationNote?: string | null;
  verifiedAt?: string | null;
  firmName: string;
  firmSlug: string;
  firmCity?: string;
  firmListedSwitch: boolean;
};

const LPC_SEARCH = "https://lpc.org.za";

export default function DirectoryVerificationPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [filter, setFilter] = useState("pending");
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    apiGet<Row[]>(`/api/v1/platform/directory/practitioners?status=${filter}`)
      .then(setRows)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not load the list."))
      .finally(() => setLoading(false));
  }, [filter]);

  useEffect(() => {
    if (user?.role === "superadmin") load();
    else setLoading(false);
  }, [user, load]);

  async function decide(row: Row, decision: "verified" | "rejected") {
    setError(null);
    try {
      await apiPost(`/api/v1/platform/directory/practitioners/${row.id}/verify`, { decision, note: notes[row.id] || "" });
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the decision.");
    }
  }

  if (user?.role !== "superadmin") {
    return (
      <div>
        <PageHeader title="LPC verification" subtitle="This screen is for the Owlaw operator." />
        <p className="text-sm text-slate-600">Sign in as the platform operator to check practitioners.</p>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="LPC verification"
        subtitle="Check each practitioner on the Legal Practice Council register by hand, then mark them verified. Only firms with a verified practitioner and a current Fidelity Fund certificate are listed in Find a Lawyer."
      />
      <ErrorBanner error={error} />
      <div className="mb-4 flex max-w-xs flex-col gap-1">
        <Label htmlFor="lpc-filter">Show</Label>
        <Select id="lpc-filter" value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="pending">Waiting for a check</option>
          <option value="verified">Verified</option>
          <option value="rejected">Rejected</option>
          <option value="all">All</option>
        </Select>
      </div>
      <p className="mb-4 text-sm text-slate-600">
        Open the{" "}
        <a href={LPC_SEARCH} target="_blank" rel="noreferrer" className="underline">
          LPC practitioner search
        </a>{" "}
        in another tab and compare the name and LPC number.
      </p>
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      {!loading && rows.length === 0 ? <p className="text-sm text-slate-600">Nothing here.</p> : null}
      <div className="space-y-4">
        {rows.map((row) => (
          <Card key={row.id} data-testid="lpc-row">
            <CardHeader>
              <CardTitle>
                {row.fullName} <span className="font-normal text-slate-500">· {row.lpcNumber}</span>
              </CardTitle>
            </CardHeader>
            <CardBody className="space-y-3 text-sm text-slate-700">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={row.status === "verified" ? "green" : row.status === "rejected" ? "red" : "gold"}>{row.status}</Badge>
                <Badge tone={row.fidelityCurrent ? "green" : "red"}>
                  Fidelity Fund {row.fidelityCurrent ? "current" : "expired"} {row.fidelityExpiresOn ? `(to ${row.fidelityExpiresOn})` : ""}
                </Badge>
                {/test/i.test(row.lpcNumber) ? <Badge tone="purple">Test entry</Badge> : null}
              </div>
              <p>
                <span className="font-semibold">{row.firmName}</span>
                {row.firmCity ? `, ${row.firmCity}` : ""} ({row.firmSlug}) · listing switch {row.firmListedSwitch ? "on" : "off"}
              </p>
              {row.verificationNote ? <p className="text-slate-500">Note: {row.verificationNote}</p> : null}
              <Textarea
                aria-label={`Note for ${row.fullName}`}
                rows={2}
                placeholder="Optional note, for example where you checked"
                value={notes[row.id] || ""}
                onChange={(e) => setNotes({ ...notes, [row.id]: e.target.value })}
              />
              <div className="flex gap-2">
                <Button onClick={() => decide(row, "verified")}>Mark LPC-verified</Button>
                <Button variant="outline" onClick={() => decide(row, "rejected")}>
                  Reject
                </Button>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
