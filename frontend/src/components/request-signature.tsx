"use client";

import { useState } from "react";
import { apiPost } from "@/lib/api";
import type { SignReq } from "@/lib/types";

/** Staff action: send a matter document to the client for an ECTA electronic signature. */
export function RequestSignature({ documentId, name, onDone }: { documentId: string | number; name: string; onDone?: () => void }) {
  const [open, setOpen] = useState(false);
  const [mandate, setMandate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  if (!open) {
    return (
      <button type="button" aria-label={`Request signature on ${name}`} className="shrink-0 text-xs font-semibold text-navy underline" onClick={() => setOpen(true)}>
        Request signature
      </button>
    );
  }
  return (
    <div className="mt-2 w-full space-y-2 rounded-lg bg-slate-50 p-2 text-xs">
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={mandate} onChange={(e) => setMandate(e.target.checked)} />
        This is the mandate or engagement letter (signing opens the matter)
      </label>
      <div className="flex gap-3">
        <button type="button" disabled={busy} className="font-semibold text-navy underline" onClick={async () => {
          setBusy(true); setMsg(null);
          try {
            const r = await apiPost<SignReq>(`/api/v1/documents/${documentId}/signature-request`, { mandate });
            setMsg(r.signUrl ? `Sent. Signing link: ${r.signUrl}` : "Sent to the client.");
            onDone?.();
          } catch (e) {
            setMsg(e instanceof Error ? e.message : "Could not send");
          } finally { setBusy(false); }
        }}>{busy ? "Sending…" : "Send to client"}</button>
        <button type="button" className="text-slate-500 underline" onClick={() => setOpen(false)}>Cancel</button>
      </div>
      {msg && <p className="break-all">{msg}</p>}
    </div>
  );
}
