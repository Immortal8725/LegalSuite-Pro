"use client";

import { useRef, useState } from "react";
import { Paperclip } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";
import { MAX_UPLOAD_BYTES, sizeLabel, when, type InboxMessage } from "@/lib/inbox";

/** Message list and reply box. `me` is the side reading: staff or client. */
export function ThreadView({ messages, me, onSend, onOpenFile, disabled, placeholder }: {
  messages: InboxMessage[];
  me: "staff" | "client";
  onSend: (body: string, file: File | null) => Promise<void>;
  onOpenFile: (m: InboxMessage) => void;
  disabled?: string | null;
  placeholder?: string;
}) {
  const [body, setBody] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  async function send() {
    if (!body.trim() && !file) return;
    if (file && file.size > MAX_UPLOAD_BYTES) { setErr("Files can be up to 50 MB."); return; }
    setBusy(true); setErr(null);
    try {
      await onSend(body.trim(), file);
      setBody(""); setFile(null);
      if (input.current) input.current.value = "";
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  return (
    <div className="space-y-3">
      {messages.length === 0 && <p className="text-sm text-slate-500">No messages yet.</p>}
      <ul className="max-h-[28rem] space-y-2 overflow-y-auto">
        {messages.map((m) => {
          const mine = m.senderType === me || (me === "staff" && m.senderType === "system");
          const seen = me === "staff" ? m.readByClientAt : m.readByStaffAt;
          return (
            <li key={m.id} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
              <div className={`max-w-[85%] rounded-xl px-3 py-2 text-sm ${mine ? "bg-navy-dark text-white" : "bg-slate-100 text-slate-900"}`}>
                <div className={`mb-1 text-[11px] ${mine ? "text-white/60" : "text-slate-500"}`}>
                  {m.kind === "stage_update" ? "Matter update" : m.senderName} · {when(m.createdAt)}
                </div>
                {m.body && <p className="whitespace-pre-line">{m.body}</p>}
                {m.attachment && (
                  <button type="button" onClick={() => onOpenFile(m)}
                    className={`mt-1 inline-flex items-center gap-1 underline ${mine ? "text-gold" : "text-navy-dark"}`}>
                    <Paperclip className="h-3 w-3" /> {m.attachment.name} {sizeLabel(m.attachment.size)}
                  </button>
                )}
                {mine && <div className="mt-1 text-right text-[10px] text-white/60">{seen ? `Read ${when(seen)}` : "Not read yet"}</div>}
              </div>
            </li>
          );
        })}
      </ul>
      {disabled ? (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">{disabled}</p>
      ) : (
        <div className="space-y-2">
          {err && <p className="text-xs text-red-700">{err}</p>}
          <Textarea rows={3} maxLength={4000} value={body} placeholder={placeholder ?? "Write a message"} onChange={(e) => setBody(e.target.value)} />
          <div className="flex flex-wrap items-center gap-2">
            <input ref={input} type="file" className="text-xs" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            <Button size="sm" className="ml-auto" disabled={busy || (!body.trim() && !file)} onClick={send}>
              {busy ? "Sending..." : "Send"}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
