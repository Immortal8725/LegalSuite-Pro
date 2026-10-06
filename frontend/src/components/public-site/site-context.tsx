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

export function PublicSiteRoot({ children }: { children: React.ReactNode }) {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;
  const [site, setSite] = useState<PublicSite | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
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
  }, [slug]);

  useEffect(() => {
    if (site?.firmName) document.title = site.firmName;
  }, [site]);

  return <Ctx.Provider value={{ slug, site, error, loading }}>{children}</Ctx.Provider>;
}

export function usePublicSite() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("Public site context is missing");
  return ctx;
}
