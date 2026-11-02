"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Scale } from "lucide-react";
import { apiBlob, apiGet, apiPost, saveBlob } from "@/lib/api";
import { Button, StatusBadge } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import type { SignReq } from "@/lib/types";
import Image from "next/image";

export default function PublicSignPage() {
  const params = useParams<{ id: string }>();
  const [doc, setDoc] = useState<SignReq | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [identity, setIdentity] = useState("");
  const [busy, setBusy] = useState(false);
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState<string | null>(null);
  const [agree, setAgree] = useState(false);
  const [drawn, setDrawn] = useState(false);
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
          OwLaw
        </div>
        <ErrorBanner error={error} />
        {!doc && !error && <p className="text-sm text-slate-600">Loading the document…</p>}
        {doc && (
          <>
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h1 className="text-2xl font-bold text-navy">{doc.title}</h1>
                <p className="text-sm text-slate-600">Requested for {doc.signerName}</p>
              </div>
              <StatusBadge status={doc.status} />
            </div>
            {doc.hasFile && (
              <Button variant="outline" size="sm" className="mb-4"
                onClick={() => apiBlob(`/api/v1/sign/${params.id}/document`).then((b) => saveBlob(b, doc.title)).catch((e) => setError(e.message))}>
                Open the document to read it
              </Button>
            )}
            <pre className="mb-6 whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm leading-relaxed">{doc.documentBody}</pre>
            {doc.documentHash && (
              <p className="mb-4 font-mono text-[11px] text-slate-600">Document hash {doc.documentHash}</p>
            )}
            {doc.status === "signed" ? (
              <div>
                <p className="mb-2 text-sm font-semibold text-emerald-700">
                  Signed {doc.signedAt ? new Date(doc.signedAt).toLocaleString() : ""}
                </p>
                {doc.unlocked && <p className="mb-2 text-sm">The limited file is now open. The pledged retainer posts to the trust account.</p>}
                {doc.signatureHash && <p className="mb-2 font-mono text-[11px] text-slate-600">Signature hash {doc.signatureHash}</p>}
                {doc.signatureStandard && (
                  <p className="mb-2 text-xs text-slate-600">
                    Standard {doc.signatureStandard}
                    {doc.identityCaptured ? " · identity number included in the hash" : ""}
                  </p>
                )}
                {doc.signedDocumentId && (
                  <Button size="sm" variant="outline" className="mb-3"
                    onClick={() => apiBlob(`/api/v1/sign/${params.id}/pdf`).then((b) => saveBlob(b, `${doc.title} (signed).pdf`)).catch((e) => setError(e.message))}>
                    Download the signed PDF
                  </Button>
                )}
                {doc.signatureDataUrl && (
                  <Image
                    alt="Signature"
                    src={doc.signatureDataUrl}
                    width={288}
                    height={96}
                    unoptimized
                    className="h-24 w-auto rounded border bg-white"
                  />
                )}
              </div>
            ) : doc.status === "void" ? (
              <p className="text-sm text-slate-600">This request was voided by the firm.</p>
            ) : (
              <div className="space-y-3">
                <Label>Type your name</Label>
                <Input aria-label="Type your name" value={name} onChange={(e) => setName(e.target.value)} />
                <Label>Identity number (SA ID, passport, or equivalent)</Label>
                <Input aria-label="Identity number (SA ID, passport, or equivalent)"
                  value={identity}
                  onChange={(e) => setIdentity(e.target.value)}
                  placeholder="Required. Hashed into the instrument"
                  required
                />
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
                    setDrawn(true);
                  }}
                />
                <button type="button" className="text-xs text-slate-500 underline" onClick={() => {
                  const c = canvasRef.current; c?.getContext("2d")?.clearRect(0, 0, c.width, c.height); setDrawn(false);
                }}>Clear signature</button>
                {doc.otpRequired && (
                  <div className="space-y-2 rounded-lg border p-3">
                    <p className="text-sm">We email a 6-digit code to confirm it is you.</p>
                    <div className="flex flex-wrap items-center gap-2">
                      <Button type="button" size="sm" variant="outline" disabled={busy} onClick={async () => {
                        setError(null);
                        try { const r = await apiPost<{ to: string }>(`/api/v1/sign/${params.id}/otp`); setOtpSent(r.to); }
                        catch (e) { setError(e instanceof Error ? e.message : "Could not send the code"); }
                      }}>{otpSent ? "Send a new code" : "Email me a code"}</Button>
                      {otpSent && <span className="text-xs text-slate-600">Sent to {otpSent}. It expires in 10 minutes.</span>}
                    </div>
                    <Input aria-label="Email code" inputMode="numeric" maxLength={6} placeholder="6-digit code" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))} />
                  </div>
                )}
                <label className="flex items-start gap-2 text-sm">
                  <input type="checkbox" className="mt-1" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
                  <span>I have read the document and I intend my typed name and drawn signature to be my electronic signature on it (ECT Act 25 of 2002, s 13). My IP address, device and the time are recorded.</span>
                </label>
                <Button
                  disabled={busy || !agree || !drawn || !name.trim() || (Boolean(doc.otpRequired) && otp.length !== 6)}
                  onClick={async () => {
                    const dataUrl = canvasRef.current?.toDataURL("image/png");
                    if (!dataUrl) return;
                    setBusy(true);
                    setError(null);
                    try {
                      const next = await apiPost<SignReq>(`/api/v1/sign/${params.id}`, {
                        signerName: name,
                        typedName: name,
                        signerIdentityNumber: identity,
                        signatureDataUrl: dataUrl,
                        otp: otp || undefined,
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
