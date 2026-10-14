"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth-provider";
import { apiPut } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";
import type { Tenant } from "@/lib/api";

const STEPS = ["Firm profile", "Landing page", "Modules", "Billing defaults"];
const TEMPLATES = [
  { id: "classic", name: "Classic", desc: "Navy, gold, traditional" },
  { id: "modern", name: "Modern", desc: "Clean editorial" },
  { id: "boutique", name: "Boutique", desc: "Warm counsel" },
  { id: "corporate", name: "Corporate", desc: "Boardroom tone" },
];

export default function OnboardingPage() {
  const { tenant, refreshTenant } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [profile, setProfile] = useState({
    phone: tenant?.phone || "",
    addressLine1: "",
    city: "",
    state: "",
    zip: "",
    website: "",
    tagline: tenant?.tagline || "Trusted counsel for every chapter of your case.",
    template: "classic",
    defaultRate: "350",
  });

  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async (extra: Record<string, unknown> = {}) => {
    setError(null);
    setSaving(true);
    try {
      const next = await apiPut<Tenant>("/api/v1/tenants/me", { ...profile, ...extra });
      refreshTenant(next);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save your settings");
      return false;
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="border-b bg-white px-8 py-4 font-bold text-navy">Owlaw setup · {tenant?.firmName}</div>
      <div className="mx-auto max-w-2xl px-6 py-10">
        <div className="mb-8 flex gap-2">
          {STEPS.map((s, i) => (
            <div key={s} className={`flex-1 rounded-full py-1 text-center text-xs font-bold ${i <= step ? "bg-navy text-white" : "bg-slate-200 text-slate-600"}`}>
              {s}
            </div>
          ))}
        </div>
        <div className="rounded-2xl bg-white p-8 shadow-card">
          {step === 0 && (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <h2 className="sm:col-span-2 text-xl font-bold">Where should clients find you?</h2>
              <div>
                <Label>Phone</Label>
                <Input aria-label="Phone" value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
              </div>
              <div>
                <Label>Website</Label>
                <Input aria-label="Website" value={profile.website} onChange={(e) => setProfile({ ...profile, website: e.target.value })} />
              </div>
              <div className="sm:col-span-2">
                <Label>Street</Label>
                <Input aria-label="Street" value={profile.addressLine1} onChange={(e) => setProfile({ ...profile, addressLine1: e.target.value })} />
              </div>
              <Input placeholder="City" value={profile.city} onChange={(e) => setProfile({ ...profile, city: e.target.value })} />
              <Input placeholder="State" value={profile.state} onChange={(e) => setProfile({ ...profile, state: e.target.value })} />
              <Input placeholder="ZIP" value={profile.zip} onChange={(e) => setProfile({ ...profile, zip: e.target.value })} />
            </div>
          )}
          {step === 1 && (
            <div className="space-y-4">
              <h2 className="text-xl font-bold">Public site template</h2>
              <div>
                <Label>Tagline</Label>
                <Input aria-label="Tagline" value={profile.tagline} onChange={(e) => setProfile({ ...profile, tagline: e.target.value })} />
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {TEMPLATES.map((t) => (
                  <button
                    type="button"
                    key={t.id}
                    onClick={() => setProfile({ ...profile, template: t.id })}
                    className={`rounded-xl border-2 p-4 text-left ${profile.template === t.id ? "border-gold bg-amber-50" : "border-slate-200"}`}
                  >
                    <p className="font-bold">{t.name}</p>
                    <p className="text-xs text-slate-600">{t.desc}</p>
                  </button>
                ))}
              </div>
              <p className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">
                Live at <strong>/firm/{tenant?.slug}</strong> as soon as you launch.
              </p>
            </div>
          )}
          {step === 2 && (
            <div>
              <h2 className="text-xl font-bold">Core modules stay on</h2>
              <p className="mt-2 text-sm text-slate-600">
                Cases, clients, calendar, tasks, documents, time, billing, messaging, conflict check, reports, and section 86 trust are in every plan. Trust is not a separate charge.
              </p>
            </div>
          )}
          {step === 3 && (
            <div className="space-y-4">
              <h2 className="text-xl font-bold">Default billing</h2>
              <div>
                <Label>Hourly rate</Label>
                <Input aria-label="Hourly rate" type="number" value={profile.defaultRate} onChange={(e) => setProfile({ ...profile, defaultRate: e.target.value })} />
              </div>
              <div>
                <Label>Increment</Label>
                <Select aria-label="Increment" defaultValue="6">
                  <option value="6">6 minutes (1/10 hour)</option>
                  <option value="15">15 minutes</option>
                </Select>
              </div>
              <p className="text-sm text-slate-600">Solo is R899 a month for one attorney. Firm is R1,199 and Practice R1,599 per attorney a month. Support staff are R349 a seat. Calls are R1.49 a minute, or R149 a month plus R8.90 a minute with your own landline as caller ID. Prices exclude VAT. Recording stays off until you opt in on a call.</p>
            </div>
          )}
          <div className="mt-6">
            <ErrorBanner error={error} />
          </div>
          <div className="mt-8 flex justify-between border-t pt-6">
            <Button variant="outline" disabled={step === 0} onClick={() => setStep(step - 1)}>
              Back
            </Button>
            {step < 3 ? (
              <Button
                disabled={saving}
                onClick={async () => {
                  if (await save()) setStep(step + 1);
                }}
              >
                {saving ? "Saving…" : "Next"}
              </Button>
            ) : (
              <Button
                variant="gold"
                disabled={saving}
                onClick={async () => {
                  if (await save({ onboardingCompleted: true, heroSubtitle: profile.tagline, template: profile.template })) router.push("/dashboard");
                }}
              >
                {saving ? "Saving…" : "Launch my firm"}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
