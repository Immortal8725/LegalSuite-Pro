"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

export default function LoginPage() {
  const { login } = useAuth();
  const [firmSlug, setFirmSlug] = useState("smith-associates");
  const [email, setEmail] = useState("john@smithlaw.com");
  const [password, setPassword] = useState("password");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  return (
    <div className="flex min-h-screen">
      <div className="hidden flex-1 flex-col justify-center bg-gradient-to-br from-navy-dark via-navy to-navy-light px-16 text-white lg:flex">
        <h1 className="text-5xl font-extrabold">
          Legal<span className="text-gold">Suite</span> Pro
        </h1>
        <p className="mt-3 text-xl text-white/80">The operating desk for firms that still answer the phone.</p>
        <ul className="mt-10 space-y-3 text-white/85">
          {[
            "Case, client, and document files in one tenant",
            "Time, invoices, and IOLTA ledgers",
            "Free in-app calling · PSTN billed at month end",
            "A public website generated the moment you register",
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
              await login(firmSlug, email, password);
            } catch (err) {
              setError(err instanceof Error ? err.message : "Sign-in failed");
            } finally {
              setLoading(false);
            }
          }}
        >
          <h2 className="text-2xl font-bold text-navy">Welcome back</h2>
          <p className="text-sm text-slate-500">Sign in with your firm slug. Demo: smith-associates / john@smithlaw.com / password</p>
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
