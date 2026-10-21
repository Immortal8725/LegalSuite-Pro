"use client";

import { use, useEffect, useRef, useState } from "react";

type View = { firm: string; invoiceNumber: string; amount: number; status: string; mode: string; action?: string; fields?: Record<string, string> };

export default function PayPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params);
  const [v, setV] = useState<View | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [flag, setFlag] = useState<string | null>(null);
  const form = useRef<HTMLFormElement>(null);

  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    setFlag(q.get("done") ? "done" : q.get("cancelled") ? "cancelled" : null);
    fetch(`/api/v1/public/pay/${encodeURIComponent(token)}`)
      .then(async (r) => {
        const j = await r.json();
        if (!r.ok) throw new Error(j.message || "This payment link is not valid.");
        setV(j.data);
      })
      .catch((e) => setErr(e instanceof Error ? e.message : "This payment link is not valid."));
  }, [token]);

  const rand = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n);

  return (
    <main className="mx-auto max-w-md px-4 py-12 text-slate-800">
      <h1 className="text-2xl font-semibold">Pay your invoice</h1>
      {err && <p className="mt-4 rounded bg-red-50 p-3 text-red-800">{err}</p>}
      {v && (
        <div className="mt-6 rounded border bg-white p-5">
          <p className="text-sm text-slate-500">{v.firm}</p>
          <p className="mt-1 text-lg font-semibold">Invoice {v.invoiceNumber}</p>
          <p className="mt-2 text-3xl font-bold">{rand(v.amount)}</p>
          {v.mode === "sandbox" && <p className="mt-3 rounded bg-amber-50 p-2 text-xs text-amber-800">Test mode. No real money will move.</p>}
          {flag === "done" && v.status === "open" && <p className="mt-4 text-sm text-emerald-700">Thank you. PayFast is confirming your payment; this can take a minute.</p>}
          {flag === "cancelled" && v.status === "open" && <p className="mt-4 text-sm text-amber-700">Payment cancelled. You can try again below.</p>}
          {v.status === "paid" && <p className="mt-4 font-semibold text-emerald-700">Paid. Thank you.</p>}
          {v.status === "expired" && <p className="mt-4 text-sm">This link has expired. Ask your attorney for a new one.</p>}
          {v.status === "cancelled" && <p className="mt-4 text-sm">This link was replaced by a newer one. Ask your attorney for the latest link.</p>}
          {v.status === "open" && v.action && v.fields && (
            <form ref={form} action={v.action} method="post" className="mt-5">
              {Object.entries(v.fields).map(([k, val]) => <input key={k} type="hidden" name={k} value={val} />)}
              <button type="submit" className="w-full rounded bg-navy px-4 py-3 font-semibold text-white">Pay securely with PayFast</button>
              <p className="mt-2 text-center text-xs text-slate-500">Card, instant EFT and other PayFast methods. Owlaw never sees your card details.</p>
            </form>
          )}
        </div>
      )}
    </main>
  );
}
