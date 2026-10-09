"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

export default function ChangePasswordPage() {
  const { ready, user, changePassword, logout } = useAuth();
  const router = useRouter();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (ready && !user) router.replace("/login");
  }, [ready, user, router]);

  if (!ready || !user) return <div className="p-10 text-sm text-slate-500">Loading…</div>;

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <form
        className="w-full max-w-sm space-y-4 rounded-xl border bg-white p-6 shadow-sm"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          if (next !== confirm) {
            setError("The new passwords do not match.");
            return;
          }
          setSaving(true);
          try {
            await changePassword(current, next);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not change the password");
          } finally {
            setSaving(false);
          }
        }}
      >
        <h1 className="text-2xl font-bold text-navy">Set your password</h1>
        <p className="text-sm text-slate-500">
          {user.mustChangePassword
            ? "You signed in with a one-time password. Choose your own before you carry on."
            : "Choose a new password for your account."}
        </p>
        <ErrorBanner error={error} />
        <div>
          <Label>Current password</Label>
          <Input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
        </div>
        <div>
          <Label>New password</Label>
          <Input type="password" autoComplete="new-password" minLength={10} value={next} onChange={(e) => setNext(e.target.value)} required />
          <p className="mt-1 text-xs text-slate-500">At least 10 characters.</p>
        </div>
        <div>
          <Label>Confirm new password</Label>
          <Input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </div>
        <Button className="w-full" disabled={saving}>
          {saving ? "Saving…" : "Save password"}
        </Button>
        <button type="button" className="w-full text-center text-sm text-slate-500" onClick={logout}>
          Sign out
        </button>
      </form>
    </div>
  );
}
