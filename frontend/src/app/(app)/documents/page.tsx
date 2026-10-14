"use client";

import { useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { EmptyState, ErrorBanner } from "@/components/ui/dialog";
import type { Doc, Matter } from "@/lib/types";

export default function DocumentsPage() {
  const [rows, setRows] = useState<Doc[]>([]);
  const [cases, setCases] = useState<Matter[]>([]);
  const [caseId, setCaseId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    apiGet<Doc[]>("/api/v1/documents")
      .then(setRows)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load documents"));
  useEffect(() => {
    load();
    apiGet<Matter[]>("/api/v1/cases")
      .then(setCases)
      .catch(() => setCases([]));
  }, []);

  return (
    <div>
      <PageHeader title="Documents" subtitle="Privileged work product stays on this firm's workspace." />
      <ErrorBanner error={error} />
      <form
        className="mb-6 flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow-card"
        onSubmit={async (e) => {
          e.preventDefault();
          const input = (e.currentTarget.elements.namedItem("file") as HTMLInputElement).files?.[0];
          if (!input) return;
          const form = e.currentTarget;
          setBusy(true);
          setError(null);
          try {
            const fd = new FormData();
            fd.append("file", input);
            if (caseId) fd.append("caseId", caseId);
            fd.append("category", "correspondence");
            await apiPost("/api/v1/documents/upload", fd);
            form.reset();
            await load();
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not upload the file");
          } finally {
            setBusy(false);
          }
        }}
      >
        <input name="file" type="file" className="text-sm" aria-label="File to upload" required />
        <select className="h-10 rounded-lg border px-3 text-sm" aria-label="Matter for the upload" value={caseId} onChange={(e) => setCaseId(e.target.value)}>
          <option value="">No matter</option>
          {cases.map((c) => (
            <option key={c.id} value={c.id}>
              {c.caseNumber}
            </option>
          ))}
        </select>
        <Button disabled={busy}>{busy ? "Uploading…" : "Upload"}</Button>
      </form>
      {rows.length === 0 ? (
        <EmptyState title="Empty cabinet" body="Drop a pleading, demand letter, or signed fee agreement." />
      ) : (
        <TableWrap>
          <thead>
            <tr>
              <Th>Name</Th>
              <Th>Category</Th>
              <Th>Size</Th>
              <Th>Added</Th>
              <Th></Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => (
              <tr key={d.id}>
                <Td className="font-semibold">{d.name}</Td>
                <Td>{d.category}</Td>
                <Td>{Math.round((d.sizeBytes || 0) / 1024)} KB</Td>
                <Td>{formatDateTime(d.createdAt)}</Td>
                <Td>
                  <button
                    className="text-sm font-semibold text-navy"
                    type="button"
                    onClick={async () => {
                      setError(null);
                      try {
                        saveBlob(await apiBlob(`/api/v1/documents/${d.id}/download`), d.name);
                      } catch (err) {
                        setError(err instanceof Error ? err.message : "Could not download this file");
                      }
                    }}
                  >
                    Download
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      )}
    </div>
  );
}
