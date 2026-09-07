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
      const hit = await apiPost<ConflictHit>("/api/v1/conflicts/check", { name });
      setLast(hit);
      setName("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Check failed.");
    }
  }

  return (
    <div>
      <PageHeader title="Conflict check" subtitle="Search clients, opposing parties, and contacts before you take the matter." />
      <ErrorBanner error={error} />
      <form onSubmit={run} className="mb-6 flex gap-2">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name or company" required />
        <Button type="submit">Run check</Button>
      </form>
      {last && (
        <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm">
          <p className="font-semibold">
            Latest: {last.searchName ?? name} — {last.matchCount} hit(s) · {last.status}
          </p>
          <ul className="mt-2 list-disc pl-5">
            {(last.matches || []).map((m, i) => (
              <li key={i}>
                {m.type}: {m.name} {m.detail ? `· ${m.detail}` : ""} ({Math.round(m.confidence * 100)}%)
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
