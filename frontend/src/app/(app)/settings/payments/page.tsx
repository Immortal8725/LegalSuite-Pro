"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPut } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";

type S = { featureEnabled: boolean; enabled: boolean; mode: string; merchantId?: string; merchantKeySet: boolean; passphraseSet: boolean; canStoreLive: boolean };

export default function PaymentSettingsPage() {
  const [s, setS] = useState<S | null>(null);
  const [f, setF] = useState({ enabled: true, mode: "sandbox", merchantId: "", merchantKey: "", passphrase: "" });
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    apiGet<S>("/api/v1/payments/settings").then((x) => {
      setS(x);
      setF((p) => ({ ...p, enabled: x.enabled, mode: x.mode, merchantId: x.merchantId ?? "" }));
    }).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
  }, []);

  return (
    <div className="max-w-xl">
      <PageHeader title="Online payments" subtitle="Let clients pay invoices by card or instant EFT through your own PayFast account." />
      <ErrorBanner error={error} />
      {s && !s.featureEnabled && <p className="mb-4 rounded bg-amber-50 p-3 text-sm">Online payments are switched off on this server.</p>}
      <div className="grid gap-4 rounded border bg-white p-4 text-sm">
        <p>Money goes straight into your firm&apos;s PayFast account, never through Owlaw. PayFast charges its own fees. Until you add live details, links run in PayFast test mode.</p>
        <label className="flex items-center gap-2"><input type="checkbox" checked={f.enabled} onChange={(e) => setF({ ...f, enabled: e.target.checked })} /> Allow payment links on invoices</label>
        <div><Label>Mode</Label><Select value={f.mode} onChange={(e) => setF({ ...f, mode: e.target.value })}>
          <option value="sandbox">Test (PayFast sandbox)</option><option value="live">Live</option>
        </Select></div>
        <div><Label>PayFast merchant ID</Label><Input value={f.merchantId} onChange={(e) => setF({ ...f, merchantId: e.target.value })} /></div>
        <div><Label>Merchant key {s?.merchantKeySet ? "(saved)" : ""}</Label><Input type="password" value={f.merchantKey} placeholder={s?.merchantKeySet ? "Leave blank to keep" : ""} onChange={(e) => setF({ ...f, merchantKey: e.target.value })} /></div>
        <div><Label>Passphrase {s?.passphraseSet ? "(saved)" : ""}</Label><Input type="password" value={f.passphrase} placeholder={s?.passphraseSet ? "Leave blank to keep" : ""} onChange={(e) => setF({ ...f, passphrase: e.target.value })} /></div>
        <p className="text-xs text-slate-500">Find these in PayFast under Settings, Developer settings. Keys are encrypted before they are stored.</p>
        <div className="flex items-center gap-3">
          <Button onClick={async () => {
            setError(null);
            try {
              setS(await apiPut<S>("/api/v1/payments/settings", f));
              setF((p) => ({ ...p, merchantKey: "", passphrase: "" }));
              setSaved(true);
            } catch (e) { setError(e instanceof Error ? e.message : "Could not save"); }
          }}>Save</Button>
          {saved && <span className="text-emerald-700">Saved.</span>}
        </div>
      </div>
    </div>
  );
}
