"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";

type QueueItem = {
  tenantId: string;
  slug: string;
  firmName: string;
  city?: string;
  country?: string;
  kind: "publish" | "branding" | "feature";
  featureKey?: string;
  featureLabel?: string;
  draftFirmName?: string;
  draftTagline?: string;
  draftAccent?: string;
  draftTheme?: string;
  liveFirmName?: string;
  liveTagline?: string;
  liveAccent?: string;
  liveTheme?: string;
  publishStatus?: string;
  note?: string | null;
};

function title(item: QueueItem) {
  if (item.kind === "publish") return "Publish the public site";
  if (item.kind === "branding") return "Branding change";
  return `Enable ${item.featureLabel || item.featureKey}`;
}

export default function PlatformSitesPage() {
  const { user } = useAuth();
  const [items, setItems] = useState<QueueItem[]>([]);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    apiGet<QueueItem[]>("/api/v1/platform/public-sites/queue")
      .then(setItems)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not load the queue."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    if (user?.role === "superadmin") load();
    else setLoading(false);
  }, [user]);

  async function decide(item: QueueItem, decision: "approved" | "rejected") {
    setError(null);
    const key = `${item.tenantId}:${item.kind}:${item.featureKey || ""}`;
    const path =
      item.kind === "publish"
        ? `/api/v1/platform/public-sites/${item.tenantId}/publish`
        : item.kind === "branding"
          ? `/api/v1/platform/public-sites/${item.tenantId}/branding`
          : `/api/v1/platform/public-sites/${item.tenantId}/features/${item.featureKey}`;
    await apiPost(path, { decision, note: notes[key] || "" });
    load();
  }

  if (user?.role !== "superadmin") {
    return (
      <div>
        <PageHeader title="Site approvals" subtitle="This queue is for the LegalSuite operator." />
        <p className="text-sm text-slate-600">Sign in as the platform operator to approve public sites.</p>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Site approvals"
        subtitle="Approve or reject a publish request, a branding change, or a feature the firm asked to turn on."
      />
      <ErrorBanner error={error} />
      {loading ? <p className="text-sm text-slate-500">Loading queue…</p> : null}
      {!loading && items.length === 0 ? <p className="text-sm text-slate-600">No requests waiting.</p> : null}
      <div className="space-y-4">
        {items.map((item) => {
          const key = `${item.tenantId}:${item.kind}:${item.featureKey || ""}`;
          return (
            <Card key={key}>
              <CardHeader>
                <CardTitle>{title(item)}</CardTitle>
              </CardHeader>
              <CardBody className="space-y-3 text-sm text-slate-700">
                <p>
                  <span className="font-semibold">{item.firmName}</span>
                  {item.city ? `, ${item.city}` : ""} ({item.slug})
                </p>
                {item.kind === "branding" ? (
                  <p>
                    Draft: {item.draftFirmName} / {item.draftAccent} / {item.draftTheme || "light"}. Live: {item.liveFirmName || "not published"} / {item.liveAccent || "none"} / {item.liveTheme || "light"}.
                    {item.draftTagline ? ` Tagline: ${item.draftTagline}` : ""}
                  </p>
                ) : null}
                {item.kind === "publish" ? (
                  <p>
                    Draft name {item.draftFirmName}, accent {item.draftAccent}, appearance {item.draftTheme || "light"}.
                    {item.publishStatus === "published" ? (
                      <Link href={`/firm/${item.slug}`} className="ml-2 underline">View site</Link>
                    ) : null}
                  </p>
                ) : null}
                <Textarea
                  rows={2}
                  placeholder="Optional note to the firm"
                  value={notes[key] || ""}
                  onChange={(e) => setNotes({ ...notes, [key]: e.target.value })}
                />
                <div className="flex gap-2">
                  <Button
                    onClick={() => {
                      decide(item, "approved").catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not approve"));
                    }}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => {
                      decide(item, "rejected").catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not reject"));
                    }}
                  >
                    Reject
                  </Button>
                </div>
              </CardBody>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
