"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

type Invite = { email: string; firstName?: string; firmName: string; firmSlug: string };

function SetPasswordForm() {
  const token = useSearchParams().get("token") || "";
  const { acceptInvite } = useAuth();
  const [invite, setInvite] = useState<Invite | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!token) {
      setLinkError("This link is missing its code. Open the link from your invite email again.");
      return;
    }
    apiPost<Invite>("/api/v1/auth/invite/lookup", { token })
      .then(setInvite)
      .catch((e) => setLinkError(e instanceof Error ? e.message : "This link cannot be used."));
  }, [token]);

  if (linkError) {
    return (
      <div className="w-full max-w-sm space-y-4 rounded-xl border bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-bold text-navy">Link not valid</h1>
        <ErrorBanner error={linkError} />
        <Link href="/login" className="block text-center text-sm font-semibold text-navy underline">
          Go to sign in
        </Link>
      </div>
    );
  }
  if (!invite) return <p className="text-sm text-slate-600">Checking your link…</p>;

  return (
    <form
      className="w-full max-w-sm space-y-4 rounded-xl border bg-white p-6 shadow-sm"
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        if (next.length < 10) {
          setError("Choose a password of at least 10 characters.");
          return;
        }
        if (next !== confirm) {
          setError("The passwords do not match.");
          return;
        }
        setSaving(true);
        try {
          await acceptInvite(token, next);
        } catch (err) {
          setError(err instanceof Error ? err.message : "Could not set the password");
        } finally {
          setSaving(false);
        }
      }}
    >
      <h1 className="text-2xl font-bold text-navy">Welcome{invite.firstName ? `, ${invite.firstName}` : ""}</h1>
      <p className="text-sm text-slate-600">
        Choose a password for <strong>{invite.email}</strong> at {invite.firmName}. Your firm code is{" "}
        <strong>{invite.firmSlug}</strong>.
      </p>
      <ErrorBanner error={error} />
      <div>
        <Label htmlFor="new-password">New password</Label>
        <Input
          id="new-password"
          type="password"
          autoComplete="new-password"
          minLength={10}
          value={next}
          onChange={(e) => setNext(e.target.value)}
          required
        />
        <p className="mt-1 text-xs text-slate-600">At least 10 characters.</p>
      </div>
      <div>
        <Label htmlFor="confirm-password">Confirm password</Label>
        <Input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
      </div>
      <Button className="w-full" disabled={saving}>
        {saving ? "Saving…" : "Set password and sign in"}
      </Button>
    </form>
  );
}

export default function SetPasswordPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <Suspense fallback={<p className="text-sm text-slate-600">Loading…</p>}>
        <SetPasswordForm />
      </Suspense>
    </div>
  );
}
