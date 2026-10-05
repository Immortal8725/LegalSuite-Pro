"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { PageHeader, TableWrap, Td, Th } from "@/components/page";
import { EmptyState, ErrorBanner } from "@/components/ui/dialog";
import type { AuditRow } from "@/lib/types";

export default function AuditPage() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<AuditRow[]>("/api/v1/audit").then(setRows).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <PageHeader title="Audit log" subtitle="Template changes, Stripe connections, and draft-help questions, on this tenant only." />
      <ErrorBanner error={error} />
      {rows.length === 0 ? (
        <EmptyState title="No rows yet" body="Merges, signatures, draft-help questions, and integration toggles appear here." />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>When</Th>
              <Th>Actor</Th>
              <Th>Action</Th>
              <Th>Record</Th>
              <Th>Detail</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <Td className="whitespace-nowrap text-xs">{formatDateTime(r.createdAt)}</Td>
                <Td>{r.actorEmail || "system"}</Td>
                <Td className="font-mono text-xs">{r.action}</Td>
                <Td className="text-xs">
                  {r.entityType}
                  {r.entityId ? ` · ${r.entityId.slice(0, 8)}` : ""}
                </Td>
                <Td className="text-slate-600">{r.detail}</Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
    </div>
  );
}
