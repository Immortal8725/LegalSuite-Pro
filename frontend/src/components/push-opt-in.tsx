"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button } from "@/components/ui/button";

type Props = {
  /** "staff" for firm users, "portal" for clients. */
  audience: "staff" | "portal";
};

function urlB64ToUint8Array(b64: string) {
  const padding = "=".repeat((4 - (b64.length % 4)) % 4);
  const raw = atob((b64 + padding).replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

/** Turn phone or browser notifications on or off for this device. Free Web Push, no app store needed. */
export function PushOptIn({ audience }: Props) {
  const base = audience === "portal" ? "/api/v1/portal/push/subscriptions" : "/api/v1/push/subscriptions";
  const [supported, setSupported] = useState(true);
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [key, setKey] = useState<string | null>(null);
  const [on, setOn] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window === "undefined" || !("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
      setSupported(false);
      return;
    }
    apiGet<{ enabled: boolean; publicKey: string | null }>("/api/v1/public/push/key")
      .then((k) => { setEnabled(k.enabled); setKey(k.publicKey); })
      .catch(() => setEnabled(false));
    navigator.serviceWorker.getRegistration("/").then(async (reg) => {
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      setOn(Boolean(sub));
    }).catch(() => undefined);
  }, []);

  async function turnOn() {
    if (!key) return;
    setBusy(true);
    setMsg(null);
    try {
      const perm = await Notification.requestPermission();
      if (perm !== "granted") {
        setMsg("Notifications are blocked for this site. Allow them in your browser settings, then try again.");
        return;
      }
      const reg = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
      await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlB64ToUint8Array(key) });
      await apiPost(base, sub.toJSON());
      setOn(true);
      setMsg("Notifications are on for this device.");
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "Could not turn on notifications.");
    } finally {
      setBusy(false);
    }
  }

  async function turnOff() {
    setBusy(true);
    setMsg(null);
    try {
      const reg = await navigator.serviceWorker.getRegistration("/");
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      if (sub) {
        await apiPost(`${base}/remove`, { endpoint: sub.endpoint }).catch(() => undefined);
        await sub.unsubscribe();
      }
      setOn(false);
      setMsg("Notifications are off for this device.");
    } finally {
      setBusy(false);
    }
  }

  if (!supported) {
    return <p className="text-sm text-slate-500">This browser cannot show notifications. On iPhone, add Owlaw to your Home Screen first (Share, then Add to Home Screen).</p>;
  }
  if (enabled === false) return <p className="text-sm text-slate-500">Phone notifications are not switched on for Owlaw yet.</p>;
  const what = audience === "portal"
    ? "Get a notification on this device when your attorney sends a message or your matter moves to a new stage."
    : "Get a notification on this device for court-day deadlines (7, 3, 1 and 0 days before) and new client messages.";
  return (
    <div className="space-y-2">
      <p className="text-sm text-slate-600">{what}</p>
      {on ? (
        <Button variant="outline" size="sm" disabled={busy} onClick={turnOff}>Turn off notifications</Button>
      ) : (
        <Button size="sm" disabled={busy || enabled === null} onClick={turnOn}>Turn on notifications</Button>
      )}
      {msg && <p className="text-xs text-slate-500">{msg}</p>}
    </div>
  );
}
