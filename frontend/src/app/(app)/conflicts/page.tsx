"use client";

import { FormEvent, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import type { ConflictHit } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";

export default function ConflictsPage() {
  const [name, setName] = useState("");
  const [opposing, setOpposing] = useState("");
  const [rows, setRows] = useState<ConflictHit[]>([]);
  const [last, setLast] = useState<ConflictHit | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => apiGet<ConflictHit[]>("/api/v1/conflicts").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  async function run(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const hit = await apiPost<ConflictHit>("/api/v1/conflicts/check", { name, opposingParty: opposing });
      setLast(hit);
      setName("");
      setOpposing("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Check failed.");
    }
  }

  return (
    <div>
      <PageHeader title="Conflict check" subtitle="Clients, related last names, adverse entities, and opposing counsel — not a substring on the file title." />
      <ErrorBanner error={error} />
      <form onSubmit={run} className="mb-6 grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Prospective client" required />
        <Input value={opposing} onChange={(e) => setOpposing(e.target.value)} placeholder="Adverse party (optional)" />
        <Button type="submit">Run check</Button>
      </form>
      {last && (
        <div className={`mb-6 rounded-xl border p-4 text-sm ${last.matchCount ? "border-red-200 bg-red-50" : "border-emerald-200 bg-emerald-50"}`}>
          <p className="font-semibold">
            Latest: {last.searchName ?? name} — {last.matchCount} hit(s) · {last.status}
          </p>
          <ul className="mt-2 list-disc pl-5">
            {(last.matches || []).map((m, i) => (
              <li key={i}>
                <span className="font-semibold uppercase">{m.role || m.type}</span>: {m.name} {m.detail ? `· ${m.detail}` : ""} ({Math.round(m.confidence * 100)}%)
              </li>
            ))}
          </ul>
        </div>
      )}
      <TableWrap>
        <thead>
          <tr>
            <Th>Search</Th>
            <Th>Status</Th>
            <Th>Hits</Th>
            <Th>When</Th>
            <Th></Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <Td className="font-medium">{r.searchName}</Td>
              <Td>
                <StatusBadge status={r.status} />
              </Td>
              <Td>{r.matchCount}</Td>
              <Td>{formatDateTime(r.createdAt)}</Td>
              <Td>
                {r.status !== "cleared" && r.status !== "clear" && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      await apiPost(`/api/v1/conflicts/${r.id}/clear`);
                      await load();
                    }}
                  >
                    Clear
                  </Button>
                )}
              </Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
    </div>
  );
}
