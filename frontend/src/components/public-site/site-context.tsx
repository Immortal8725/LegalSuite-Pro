"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { apiGet } from "@/lib/api";
import type { PublicSite } from "@/lib/types";

type State = {
  slug: string;
  site: PublicSite | null;
  error: string | null;
  loading: boolean;
};

const Ctx = createContext<State | null>(null);

export function PublicSiteRoot({
  children,
  initialSite = null,
  seeded = false,
}: {
  children: React.ReactNode;
  /** Server render of the public site. When seeded, the browser does not fetch the same JSON again. */
  initialSite?: PublicSite | null;
  seeded?: boolean;
}) {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;
  const [site, setSite] = useState<PublicSite | null>(initialSite);
  const [error, setError] = useState<string | null>(seeded && !initialSite ? "This address does not match a firm." : null);
  const [loading, setLoading] = useState(!seeded);

  useEffect(() => {
    if (seeded) {
      setSite(initialSite);
      setLoading(false);
      setError(initialSite ? null : "This address does not match a firm.");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    apiGet<PublicSite>(`/api/v1/public/sites/${slug}`)
      .then((next) => {
        if (!cancelled) setSite(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not load this site.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [slug, seeded, initialSite]);

  useEffect(() => {
    if (site?.firmName) document.title = site.firmName;
  }, [site]);

  const theme = site?.theme === "dark" ? "dark" : "light";

  return (
    <div data-theme={theme} className="ps-site min-h-screen bg-[var(--ps-paper)] text-[var(--ps-ink)] antialiased">
      <Ctx.Provider value={{ slug, site, error, loading }}>{children}</Ctx.Provider>
    </div>
  );
}

export function usePublicSite() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("Public site context is missing");
  return ctx;
}
