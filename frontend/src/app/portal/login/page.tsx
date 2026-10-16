"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";
import { DEMO_MODE } from "@/lib/demo";

export default function PortalLoginPage() {
  const { portalLogin } = useAuth();
  const [firmSlug, setFirmSlug] = useState(DEMO_MODE ? "ndlovu-partners" : "");
  const [email, setEmail] = useState(DEMO_MODE ? "nomsa@example.com" : "");
  const [password, setPassword] = useState(DEMO_MODE ? "portal123" : "");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
      <form
        className="w-full max-w-sm space-y-4 rounded-2xl bg-white p-8 shadow-card"
        onSubmit={async (e) => {
          e.preventDefault();
          setLoading(true);
          setError(null);
          try {
            await portalLogin(firmSlug, email, password);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Portal sign-in failed");
          } finally {
            setLoading(false);
          }
        }}
      >
        <h1 className="text-2xl font-bold text-navy">Client portal</h1>
        <p className="text-sm text-slate-600">View your matters, invoices, and message the firm.{DEMO_MODE && " Demo: ndlovu-partners / nomsa@example.com / portal123"}</p>
        <ErrorBanner error={error} />
        <div>
          <Label>Firm ID</Label>
          <Input aria-label="Firm ID" value={firmSlug} onChange={(e) => setFirmSlug(e.target.value)} required />
        </div>
        <div>
          <Label>Email</Label>
          <Input aria-label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div>
          <Label>Password</Label>
          <Input aria-label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        <Button className="w-full" disabled={loading}>
          {loading ? "Signing in…" : "Enter portal"}
        </Button>
        <p className="text-center text-sm text-slate-600">
          Attorney?{" "}
          <Link href="/login" className="font-semibold text-navy">
            Firm sign-in
          </Link>
        </p>
      </form>
    </div>
  );
}
