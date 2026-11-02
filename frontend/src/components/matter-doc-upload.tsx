"use client";

import { useRef, useState } from "react";
import { apiPost } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { ScanToPdf } from "@/components/scan-to-pdf";
import { MAX_UPLOAD_BYTES } from "@/lib/inbox";

/** Upload a file to a matter, or scan pages with the phone camera into one PDF. Same 50 MB limit and type checks as the server. */
export function MatterDocUpload({ caseId, onDone }: { caseId?: string; onDone: () => void | Promise<unknown> }) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const pick = useRef<HTMLInputElement>(null);

  async function upload(file: File) {
    if (file.size > MAX_UPLOAD_BYTES) { setErr("Files can be up to 50 MB."); return; }
    setBusy(true); setErr(null); setOk(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      if (caseId) fd.append("caseId", caseId);
      fd.append("category", "correspondence");
      await apiPost("/api/v1/documents/upload", fd);
      setOk(`Uploaded ${file.name}.`);
      await onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Could not upload the file");
    } finally {
      setBusy(false);
      if (pick.current) pick.current.value = "";
    }
  }

  return (
    <div className="space-y-2 rounded-lg border border-dashed p-3">
      <div className="flex flex-wrap items-center gap-2">
        <input ref={pick} type="file" className="hidden" aria-label="Choose a file to upload"
          accept="image/*,application/pdf,.doc,.docx,.xls,.xlsx,.odt,.ods,.rtf,.txt,.csv,.heic,.heif,.eml,.msg,.zip"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f); }} />
        <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => pick.current?.click()}>
          {busy ? "Uploading..." : "Upload a file"}
        </Button>
        <span className="text-xs text-slate-500">Up to 50 MB.</span>
      </div>
      <ScanToPdf label="Scan pages with camera" onReady={upload} />
      {ok && <p className="text-xs text-emerald-700">{ok}</p>}
      {err && <p className="text-xs text-red-700">{err}</p>}
    </div>
  );
}
