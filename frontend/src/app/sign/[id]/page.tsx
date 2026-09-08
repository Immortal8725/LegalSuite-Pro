"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Scale } from "lucide-react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, StatusBadge } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import type { SignReq } from "@/lib/types";

export default function PublicSignPage() {
  const params = useParams<{ id: string }>();
  const [doc, setDoc] = useState<SignReq | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);

  useEffect(() => {
    apiGet<SignReq>(`/api/v1/sign/${params.id}`)
      .then((d) => {
        setDoc(d);
        setName(d.signerName);
      })
      .catch((e) => setError(e.message));
  }, [params.id]);

  function pos(e: React.PointerEvent<HTMLCanvasElement>) {
    const c = canvasRef.current!;
    const r = c.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-10">
      <div className="mx-auto max-w-2xl rounded-2xl bg-white p-6 shadow-card sm:p-10">
        <div className="mb-6 flex items-center gap-2 font-extrabold text-navy">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold text-navy-dark">
            <Scale className="h-4 w-4" />
          </span>
          LegalSuite Pro
        </div>
        <ErrorBanner error={error} />
        {!doc && !error && <p className="text-sm text-slate-500">Loading the document…</p>}
        {doc && (
          <>
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h1 className="text-2xl font-bold text-navy">{doc.title}</h1>
                <p className="text-sm text-slate-500">Requested for {doc.signerName}</p>
              </div>
              <StatusBadge status={doc.status} />
            </div>
            <pre className="mb-6 whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm leading-relaxed">{doc.documentBody}</pre>
            {doc.documentHash && (
              <p className="mb-4 font-mono text-[11px] text-slate-500">Document hash {doc.documentHash}</p>
            )}
            {doc.status === "signed" ? (
              <div>
                <p className="mb-2 text-sm font-semibold text-emerald-700">
                  Signed {doc.signedAt ? new Date(doc.signedAt).toLocaleString() : ""}
                </p>
                {doc.unlocked && <p className="mb-2 text-sm">The limited file is now open. The pledged retainer posts to the trust account.</p>}
                {doc.signatureHash && <p className="mb-2 font-mono text-[11px] text-slate-500">Signature hash {doc.signatureHash}</p>}
                {doc.signatureDataUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img alt="Signature" src={doc.signatureDataUrl} className="h-24 rounded border bg-white" />
                )}
              </div>
            ) : doc.status === "void" ? (
              <p className="text-sm text-slate-500">This request was voided by the firm.</p>
            ) : (
              <div className="space-y-3">
                <Label>Type your name</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} />
                <Label>Draw your signature</Label>
                <canvas
                  ref={canvasRef}
                  width={640}
                  height={160}
                  className="w-full touch-none rounded-lg border bg-white"
                  onPointerDown={(e) => {
                    drawing.current = true;
                    const ctx = canvasRef.current?.getContext("2d");
                    if (!ctx) return;
                    const p = pos(e);
                    ctx.beginPath();
                    ctx.moveTo(p.x, p.y);
                  }}
                  onPointerMove={(e) => {
                    if (!drawing.current) return;
                    const ctx = canvasRef.current?.getContext("2d");
                    if (!ctx) return;
                    const p = pos(e);
                    ctx.lineWidth = 2;
                    ctx.lineCap = "round";
                    ctx.strokeStyle = "#1a365d";
                    ctx.lineTo(p.x, p.y);
                    ctx.stroke();
                  }}
                  onPointerUp={() => {
                    drawing.current = false;
                  }}
                />
                <Button
                  disabled={busy}
                  onClick={async () => {
                    const dataUrl = canvasRef.current?.toDataURL("image/png");
                    if (!dataUrl) return;
                    setBusy(true);
                    setError(null);
                    try {
                      const next = await apiPost<SignReq>(`/api/v1/sign/${params.id}`, {
                        signerName: name,
                        signatureDataUrl: dataUrl,
                      });
                      setDoc(next);
                    } catch (e) {
                      setError(e instanceof Error ? e.message : "Could not sign");
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  {busy ? "Saving…" : "Sign and finish"}
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
