"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";
import { PLANS, PRICE_NOTE, type PlanSlug } from "@/lib/pricing";

const AREAS = [
  "RAF",
  "Personal Injury",
  "Labour",
  "Deceased Estates",
  "Commercial",
  "Family Law",
  "Criminal Defence",
  "Medical Negligence",
  "Property",
  "General Practice",
];

export default function RegisterPage() {
  const { registerFirm } = useAuth();
  const [form, setForm] = useState({
    firmName: "",
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    phone: "",
    firmSize: "solo",
    planSlug: "firm" as PlanSlug,
    practiceAreas: [] as string[],
    country: "ZA",
    state: "GP",
    city: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const set = (k: string, v: string) => setForm((p) => ({ ...p, [k]: v }));
  useEffect(() => {
    const wanted = new URLSearchParams(window.location.search).get("plan");
    if (wanted && PLANS.some((p) => p.slug === wanted)) setForm((p) => ({ ...p, planSlug: wanted as PlanSlug }));
  }, []);
  const chosen = PLANS.find((p) => p.slug === form.planSlug) ?? PLANS[1];

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-navy-dark via-navy to-navy-light p-6">
      <form
        className="w-full max-w-2xl space-y-4 rounded-2xl bg-white p-8 shadow-lift"
        onSubmit={async (e) => {
          e.preventDefault();
          setLoading(true);
          setError(null);
          try {
            await registerFirm(form);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Registration failed");
          } finally {
            setLoading(false);
          }
        }}
      >
        <h2 className="text-2xl font-bold text-navy">Register your firm</h2>
        <p className="text-sm text-slate-600">
          You get a 14-day trial, your own Owlaw workspace and section 86 trust on every plan. {PRICE_NOTE} New firms default to South Africa.
        </p>
        <fieldset>
          <legend className="text-sm font-semibold text-navy">Plan</legend>
          <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-3">
            {PLANS.map((p) => (
              <label
                key={p.slug}
                className={`cursor-pointer rounded-xl border p-3 text-sm ${form.planSlug === p.slug ? "border-gold bg-amber-50" : "border-slate-200"}`}
              >
                <input
                  type="radio"
                  name="plan"
                  value={p.slug}
                  checked={form.planSlug === p.slug}
                  onChange={() => set("planSlug", p.slug)}
                  className="mr-2"
                />
                <span className="font-bold text-navy">{p.name}</span>
                <span className="block text-slate-600">
                  {p.price} {p.unit}
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <ErrorBanner error={error} />
        <div>
          <Label>Firm name</Label>
              <Input aria-label="Firm name" required value={form.firmName} onChange={(e) => set("firmName", e.target.value)} placeholder="Ndlovu & Partners" />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <Label>First name</Label>
            <Input aria-label="First name" required value={form.firstName} onChange={(e) => set("firstName", e.target.value)} />
          </div>
          <div>
            <Label>Last name</Label>
            <Input aria-label="Last name" required value={form.lastName} onChange={(e) => set("lastName", e.target.value)} />
          </div>
          <div>
            <Label>Email</Label>
            <Input aria-label="Email" type="email" required value={form.email} onChange={(e) => set("email", e.target.value)} />
          </div>
          <div>
            <Label>Password (8+)</Label>
            <Input aria-label="Password (8+)" type="password" required minLength={8} value={form.password} onChange={(e) => set("password", e.target.value)} />
          </div>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <Label>Country</Label>
            <Select aria-label="Country"
              value={form.country}
              onChange={(e) => {
                const country = e.target.value;
                setForm((p) => ({ ...p, country, state: country === "ZA" ? "GP" : "TX" }));
              }}
            >
              <option value="ZA">South Africa</option>
              <option value="US">United States</option>
            </Select>
          </div>
          <div>
            <Label>{form.country === "ZA" ? "Province" : "State"}</Label>
            <Input aria-label={form.country === "ZA" ? "Province" : "State"} value={form.state} onChange={(e) => set("state", e.target.value)} />
          </div>
        </div>
        <div>
          <Label>Firm size</Label>
          <Select aria-label="Firm size" value={form.firmSize} onChange={(e) => set("firmSize", e.target.value)}>
            <option value="solo">Solo</option>
            <option value="small">Small (2–5)</option>
            <option value="medium">Medium (6–25)</option>
            <option value="large">Large (25+)</option>
          </Select>
        </div>
        <div>
          <Label>Practice areas</Label>
          <div className="mt-2 flex flex-wrap gap-2">
            {AREAS.map((a) => {
              const on = form.practiceAreas.includes(a);
              return (
                <button
                  type="button"
                  key={a}
                  onClick={() =>
                    setForm((p) => ({
                      ...p,
                      practiceAreas: on ? p.practiceAreas.filter((x) => x !== a) : [...p.practiceAreas, a],
                    }))
                  }
                  className={`rounded-full border px-3 py-1 text-xs font-semibold ${on ? "border-navy bg-navy text-white" : "border-slate-200"}`}
                >
                  {a}
                </button>
              );
            })}
          </div>
        </div>
        <Button className="w-full" variant="gold" disabled={loading}>
          {loading ? "Creating your firm…" : `Create firm on ${chosen.name}`}
        </Button>
        <p className="text-center text-sm text-slate-600">
          Already registered?{" "}
          <Link href="/login" className="font-semibold text-navy">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
