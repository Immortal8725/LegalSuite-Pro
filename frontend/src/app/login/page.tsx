"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

const DEMOS = [
  {
    label: "South Africa — Ndlovu & Partners",
    slug: "ndlovu-partners",
    email: "thabo@ndlovulaw.co.za",
    note: "RAF, Act 40, LPA s 86 recon",
  },
  {
    label: "Texas — Smith & Associates",
    slug: "smith-associates",
    email: "john@smithlaw.com",
    note: "SOL, TTCA, IOLTA",
  },
];

export default function LoginPage() {
  const { login } = useAuth();
  const [firmSlug, setFirmSlug] = useState("ndlovu-partners");
  const [email, setEmail] = useState("thabo@ndlovulaw.co.za");
  const [password, setPassword] = useState("password");
  const [totpCode, setTotpCode] = useState("");
  const [needTotp, setNeedTotp] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  return (
    <div className="flex min-h-screen">
      <div className="hidden flex-1 flex-col justify-center bg-gradient-to-br from-navy-dark via-navy to-navy-light px-16 text-white lg:flex">
        <h1 className="text-5xl font-extrabold">
          Legal<span className="text-gold">Suite</span> Pro
        </h1>
        <p className="mt-3 text-xl text-white/80">Built for South African firms. Texas still runs on the other tenant.</p>
        <ul className="mt-10 space-y-3 text-white/85">
          {[
            "RAF, Act 40, CCMA, and Prescription Act clocks on the home screen",
            "LPA s 86 three-way recon — bank, cashbook, client ledgers",
            "RICA recording is opt-in even though the statute is one-party",
            "Hire is a gate: conflict, signed waiver, limited file, signed mandate",
          ].map((t) => (
            <li key={t}>— {t}</li>
          ))}
        </ul>
      </div>
      <div className="flex w-full items-center justify-center bg-white p-8 lg:w-[480px]">
        <form
          className="w-full max-w-sm space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            setLoading(true);
            setError(null);
            try {
              const result = await login(firmSlug, email, password, totpCode || undefined);
              if (result.requiresTotp) {
                setNeedTotp(true);
              }
            } catch (err) {
              setError(err instanceof Error ? err.message : "Sign-in failed");
            } finally {
              setLoading(false);
            }
          }}
        >
          <h2 className="text-2xl font-bold text-navy">Welcome back</h2>
          <p className="text-sm text-slate-500">Sign in with your firm slug. Demo password is password.</p>
          <div className="flex flex-col gap-2">
            {DEMOS.map((d) => (
              <button
                key={d.slug}
                type="button"
                onClick={() => {
                  setFirmSlug(d.slug);
                  setEmail(d.email);
                  setNeedTotp(false);
                  setTotpCode("");
                }}
                className={`rounded-lg border px-3 py-2 text-left text-xs ${
                  firmSlug === d.slug ? "border-navy bg-navy/5 font-semibold text-navy" : "border-slate-200 text-slate-600"
                }`}
              >
                {d.label}
                <span className="mt-0.5 block font-normal text-slate-400">{d.note}</span>
              </button>
            ))}
          </div>
          <ErrorBanner error={error} />
          <div>
            <Label>Firm ID</Label>
            <Input value={firmSlug} onChange={(e) => setFirmSlug(e.target.value)} required />
          </div>
          <div>
            <Label>Email</Label>
            <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div>
            <Label>Password</Label>
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {needTotp && (
            <div>
              <Label>Authenticator code</Label>
              <Input
                inputMode="numeric"
                autoComplete="one-time-code"
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value)}
                placeholder="6-digit code"
                required
              />
              <p className="mt-1 text-xs text-slate-500">This account has 2FA on. Demo users do not — enrol from Settings if you want to try it.</p>
            </div>
          )}
          <Button className="w-full" disabled={loading}>
            {loading ? "Signing in…" : "Sign in"}
          </Button>
          <p className="text-center text-sm text-slate-500">
            New firm?{" "}
            <Link href="/register" className="font-semibold text-navy">
              Register
            </Link>
            {" · "}
            <Link href="/portal/login" className="font-semibold text-navy">
              Client portal
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
