"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import QRCode from "qrcode";
import { apiPost, setSession, type Tenant, type User } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/dialog";

type Confirmed = { accessToken: string; refreshToken: string; user: User; tenant: Tenant; recoveryCodes: string[] };

/** First sign-in for an Owlaw operator: two-factor sign-in is compulsory before any platform screen. */
export default function TwoFactorSetupPage() {
  const { ready, user, reloadMe, logout } = useAuth();
  const router = useRouter();
  const [secret, setSecret] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [codes, setCodes] = useState<string[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (ready && !user) router.replace("/login");
    if (ready && user && user.totpEnabled && !codes) router.replace(user.role === "superadmin" ? "/platform/firms" : "/settings");
  }, [ready, user, codes, router]);

  async function start() {
    setError(null);
    setBusy(true);
    try {
      const r = await apiPost<{ secret: string; otpauthUrl: string }>("/api/v1/auth/totp/start");
      setSecret(r.secret);
      setQr(await QRCode.toDataURL(r.otpauthUrl, { margin: 1, width: 220 }));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not start set-up.");
    } finally {
      setBusy(false);
    }
  }

  async function confirm(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const r = await apiPost<Confirmed>("/api/v1/auth/totp/confirm", { code: code.trim() });
      setSession(r);
      setCodes(r.recoveryCodes);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "That code did not work. Wait for a new code and try again.");
    } finally {
      setBusy(false);
    }
  }

  function download() {
    if (!codes) return;
    const text = `Owlaw recovery codes for ${user?.email}\nEach code works once. Keep them somewhere safe and offline.\n\n${codes.join("\n")}\n`;
    const url = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "owlaw-recovery-codes.txt";
    a.click();
    URL.revokeObjectURL(url);
  }

  if (!ready || !user) return <div className="p-10 text-sm text-slate-600">Loading…</div>;

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <div className="w-full max-w-md space-y-4 rounded-xl border bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-bold text-navy">Set up two-factor sign-in</h1>
        <ErrorBanner error={error} />
        {codes ? (
          <div className="space-y-3" data-testid="recovery-codes">
            <p className="text-sm text-slate-700">
              Two-factor sign-in is on. Save these recovery codes now. Each one works once if you lose your phone. They will not be shown again.
            </p>
            <ul className="grid grid-cols-2 gap-2 rounded-lg bg-slate-50 p-3 font-mono text-sm">
              {codes.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={download}>
                Download as text
              </Button>
              <Button type="button" variant="outline" onClick={() => navigator.clipboard?.writeText(codes.join("\n"))}>
                Copy
              </Button>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} /> I have saved my recovery codes
            </label>
            <Button
              className="w-full"
              disabled={!saved}
              onClick={async () => {
                await reloadMe();
                router.replace(user.role === "superadmin" ? "/platform/firms" : "/settings");
              }}
            >
              Continue
            </Button>
          </div>
        ) : (
          <>
            <p className="text-sm text-slate-700">
              {user.mfaRequired
                ? "Owlaw operator accounts must use an authenticator app. Platform screens stay closed until this is done."
                : "Add an authenticator app to your account."}{" "}
              Use Google Authenticator, Microsoft Authenticator or any app that supports time-based codes.
            </p>
            {!secret ? (
              <Button className="w-full" onClick={start} disabled={busy}>
                Show my QR code
              </Button>
            ) : (
              <form onSubmit={confirm} className="space-y-3">
                <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-700">
                  <li>Open your authenticator app and scan this code.</li>
                  <li>Type the 6-digit code it shows.</li>
                </ol>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {qr ? <img src={qr} alt="QR code for your authenticator app" className="mx-auto h-[220px] w-[220px]" data-testid="totp-qr" /> : null}
                <p className="text-center text-xs text-slate-500">
                  Cannot scan? Enter this key by hand: <span className="font-mono">{secret}</span>
                </p>
                <div className="flex flex-col gap-1">
                  <Label htmlFor="code">Code from the app</Label>
                  <Input id="code" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} required />
                </div>
                <Button type="submit" className="w-full" disabled={busy}>
                  Turn on two-factor sign-in
                </Button>
              </form>
            )}
            <button type="button" className="w-full text-center text-sm text-slate-500 underline" onClick={() => logout()}>
              Sign out
            </button>
          </>
        )}
      </div>
    </div>
  );
}
