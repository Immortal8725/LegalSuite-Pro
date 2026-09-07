"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label, Select } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

const AREAS = [
  "Family Law",
  "Criminal Defense",
  "Personal Injury",
  "Corporate",
  "Real Estate",
  "Estate Planning",
  "Immigration",
  "Bankruptcy",
  "Employment",
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
    practiceAreas: [] as string[],
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const set = (k: string, v: string) => setForm((p) => ({ ...p, [k]: v }));

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
        <p className="text-sm text-slate-500">
          In under a minute you get a tenant, an admin seat, and a public site at /firm/your-slug.
        </p>
        <ErrorBanner error={error} />
        <div>
          <Label>Firm name</Label>
          <Input required value={form.firmName} onChange={(e) => set("firmName", e.target.value)} placeholder="Chen & Patel LLP" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Label>First name</Label>
            <Input required value={form.firstName} onChange={(e) => set("firstName", e.target.value)} />
          </div>
          <div>
            <Label>Last name</Label>
            <Input required value={form.lastName} onChange={(e) => set("lastName", e.target.value)} />
          </div>
          <div>
            <Label>Email</Label>
            <Input type="email" required value={form.email} onChange={(e) => set("email", e.target.value)} />
          </div>
          <div>
            <Label>Password (8+)</Label>
            <Input type="password" required minLength={8} value={form.password} onChange={(e) => set("password", e.target.value)} />
          </div>
        </div>
        <div>
          <Label>Firm size</Label>
          <Select value={form.firmSize} onChange={(e) => set("firmSize", e.target.value)}>
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
          {loading ? "Creating tenant…" : "Create firm & start trial"}
        </Button>
        <p className="text-center text-sm text-slate-500">
          Already registered?{" "}
          <Link href="/login" className="font-semibold text-navy">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
