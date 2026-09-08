"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

export default function PortalLoginPage() {
  const { portalLogin } = useAuth();
  const [firmSlug, setFirmSlug] = useState("ndlovu-partners");
  const [email, setEmail] = useState("nomsa@example.com");
  const [password, setPassword] = useState("portal123");
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
        <p className="text-sm text-slate-500">View your matters, invoices, and message the firm. Demo: ndlovu-partners / nomsa@example.com / portal123</p>
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
          {loading ? "Signing in…" : "Enter portal"}
        </Button>
        <p className="text-center text-sm text-slate-500">
          Attorney?{" "}
          <Link href="/login" className="font-semibold text-navy">
            Firm sign-in
          </Link>
        </p>
      </form>
    </div>
  );
}
