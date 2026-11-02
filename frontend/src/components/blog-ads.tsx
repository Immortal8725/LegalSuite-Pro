"use client";

import Link from "next/link";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import {
  ADS_CONSENT_COOKIE,
  type AdSlotName,
  type AdsConsent,
  adsenseClient,
  adsenseSlot,
  isBlogPath,
  isMainSiteHost,
  parseConsent,
} from "@/lib/adsense";

type AdsWindow = Window & { adsbygoogle?: unknown[] & { requestNonPersonalizedAds?: number; pauseAdRequests?: number } };

const AdsContext = createContext<{ ready: boolean; consent: AdsConsent }>({ ready: false, consent: "none" });

function readCookie(): AdsConsent | null {
  const m = document.cookie.match(new RegExp(`(?:^|; )${ADS_CONSENT_COOKIE}=([^;]*)`));
  return parseConsent(m ? decodeURIComponent(m[1]) : null);
}

function writeCookie(v: AdsConsent) {
  const secure = location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${ADS_CONSENT_COOKIE}=${v}; Path=/blog; Max-Age=${60 * 60 * 24 * 180}; SameSite=Lax${secure}`;
}

/** Loads the AdSense script on blog pages of the main site only, after reading the reader's ad choice. */
export function BlogAdsProvider({ children }: { children: React.ReactNode }) {
  const client = adsenseClient();
  const pathname = usePathname() || "";
  const [consent, setConsent] = useState<AdsConsent | null>(null);
  const [asked, setAsked] = useState(false);
  const [allowedHost, setAllowedHost] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!client) return;
    setAllowedHost(isMainSiteHost(location.hostname));
    const c = readCookie();
    setConsent(c ?? "npa");
    setAsked(c !== null);
  }, [client]);

  const active = !!client && allowedHost && isBlogPath(pathname) && consent !== null && consent !== "none";

  useEffect(() => {
    if (!active || !client) return;
    const w = window as AdsWindow;
    w.adsbygoogle = w.adsbygoogle || ([] as unknown as AdsWindow["adsbygoogle"]);
    // Non-personalised unless the reader chose otherwise. Must be set before the first push.
    w.adsbygoogle!.requestNonPersonalizedAds = consent === "personalised" ? 0 : 1;
    if (!document.getElementById("owlaw-adsense")) {
      const s = document.createElement("script");
      s.id = "owlaw-adsense";
      s.async = true;
      s.crossOrigin = "anonymous";
      s.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(client)}`;
      document.head.appendChild(s);
    }
    setReady(true);
  }, [active, client, consent]);

  const choose = (v: AdsConsent) => {
    writeCookie(v);
    setAsked(true);
    if (ready && v !== consent) {
      // The ad script has already started with the old setting; reload so the new choice applies cleanly.
      location.reload();
      return;
    }
    setConsent(v);
  };

  return (
    <AdsContext.Provider value={{ ready: active && ready, consent: consent ?? "none" }}>
      {children}
      {client && allowedHost && isBlogPath(pathname) && !asked && (
        <div role="dialog" aria-label="Cookies and ads" className="fixed inset-x-0 bottom-0 z-50 border-t bg-white p-4 shadow-lg" data-testid="ads-consent">
          <div className="mx-auto flex max-w-4xl flex-col gap-3 text-sm text-slate-700 sm:flex-row sm:items-center sm:justify-between">
            <p>
              The Owlaw blog shows Google ads to cover its costs. By default they are not based on your interests, but Google may still
              use cookies to count and limit ads. You can allow ads based on your interests or turn ads off.{" "}
              <Link href="/legal/privacy" className="font-semibold text-sky-700 underline">Privacy policy</Link>
            </p>
            <div className="flex shrink-0 flex-wrap gap-2 font-semibold">
              <button type="button" onClick={() => choose("npa")} className="rounded-lg bg-navy px-3 py-2 text-white">OK</button>
              <button type="button" onClick={() => choose("personalised")} className="rounded-lg border px-3 py-2 text-navy">Allow interest-based ads</button>
              <button type="button" onClick={() => choose("none")} className="rounded-lg border px-3 py-2 text-navy">No ads</button>
            </div>
          </div>
        </div>
      )}
    </AdsContext.Provider>
  );
}

/** One labelled ad unit. Renders nothing unless AdSense is configured, the slot has an id and the reader has not said no. */
export function BlogAd({ slot }: { slot: AdSlotName }) {
  const { ready } = useContext(AdsContext);
  const client = adsenseClient();
  const id = adsenseSlot(slot);
  const pushed = useRef(false);

  useEffect(() => {
    if (!ready || !id || pushed.current) return;
    pushed.current = true;
    try {
      ((window as AdsWindow).adsbygoogle = (window as AdsWindow).adsbygoogle || ([] as unknown as AdsWindow["adsbygoogle"]))!.push({});
    } catch {
      /* ad blockers and slow loads: show nothing */
    }
  }, [ready, id]);

  if (!client || !id || !ready) return null;
  return (
    <aside className="my-8 rounded-xl border border-dashed bg-white p-3" aria-label="Advertisement" data-testid={`blog-ad-${slot}`}>
      <p className="mb-2 text-center text-[11px] font-semibold uppercase tracking-wide text-slate-400">Advertisement</p>
      <ins
        className="adsbygoogle"
        style={{ display: "block" }}
        data-ad-client={client}
        data-ad-slot={id}
        data-ad-format="auto"
        data-full-width-responsive="true"
      />
    </aside>
  );
}
