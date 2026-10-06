"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import type { PublicSiteAdmin, PublicSiteFeatureState } from "@/lib/types";

function publishLabel(status: string) {
  if (status === "published") return "Published";
  if (status === "pending_approval") return "Waiting for approval";
  if (status === "rejected") return "Rejected";
  return "Draft";
}

function featureLabel(feature: PublicSiteFeatureState, requestedNow: boolean) {
  if (requestedNow && feature.approvalStatus === "approved" && feature.live) return "On the public site";
  if (requestedNow && feature.approvalStatus === "pending") return "Waiting for approval";
  if (requestedNow && feature.approvalStatus === "rejected") return "Rejected. Submit again to ask for another review.";
  if (requestedNow && feature.approvalStatus === "approved") return "Approved. It appears when the site is published.";
  if (requestedNow) return "Selected. Submit for approval to ask for it.";
  if (feature.approvalStatus === "approved") return "Approved earlier. Save to keep it off the public site.";
  return "Off";
}

export default function WebsiteSettingsPage() {
  const { user } = useAuth();
  const [site, setSite] = useState<PublicSiteAdmin | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [firmName, setFirmName] = useState("");
  const [tagline, setTagline] = useState("");
  const [about, setAbout] = useState("");
  const [accent, setAccent] = useState("navy");
  const [requested, setRequested] = useState<Record<string, boolean>>({});

  useEffect(() => {
    apiGet<PublicSiteAdmin>("/api/v1/public-site")
      .then((next) => {
        setSite(next);
        setFirmName(next.firmName || "");
        setTagline(next.tagline || "");
        setAbout(next.about || "");
        setAccent(next.accent || "navy");
        const flags: Record<string, boolean> = {};
        next.features.forEach((feature) => {
          flags[feature.key] = feature.requested;
        });
        setRequested(flags);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not load the public site."));
  }, []);

  function payload() {
    return { firmName, tagline, about, accent, features: requested };
  }

  async function save() {
    setError(null);
    setSaved(null);
    const next = await apiPut<PublicSiteAdmin>("/api/v1/public-site", payload());
    setSite(next);
    setSaved("Saved. Nothing new goes live until it is approved.");
  }

  async function submit() {
    setError(null);
    setSaved(null);
    await apiPut<PublicSiteAdmin>("/api/v1/public-site", payload());
    const next = await apiPost<PublicSiteAdmin>("/api/v1/public-site/submit");
    setSite(next);
    setSaved("Submitted. Approved features stay as they are. New ones wait.");
  }

  const allowed = user && ["owner", "partner", "director"].includes(user.role);

  return (
    <div>
      <PageHeader
        title="Public site"
        subtitle="Choose branding and which pages to ask for. A LegalSuite operator approves an enable before it appears on the public site."
        actions={
          site?.publishStatus === "published" ? (
            <Link href={`/firm/${site.slug}`} className="text-sm font-semibold text-navy underline">
              View public site
            </Link>
          ) : null
        }
      />
      <ErrorBanner error={error} />
      {saved ? <p className="mb-4 text-sm text-emerald-700">{saved}</p> : null}
      {!allowed ? <p className="text-sm text-slate-600">Only a firm owner can change the public site.</p> : null}
      {site && allowed ? (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Status: {publishLabel(site.publishStatus)}</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2 text-sm text-slate-600">
              {site.publishNote ? <p>Publish note: {site.publishNote}</p> : null}
              {site.brandingStatus === "pending" ? <p>A branding change is waiting for approval. The live site still shows the last approved name and accent.</p> : null}
              {site.brandingStatus === "rejected" && site.brandingNote ? <p>Branding note: {site.brandingNote}</p> : null}
              {site.liveFirmName ? (
                <p>
                  On the site now: {site.liveFirmName}
                  {site.liveAccent ? ` (${site.liveAccent})` : ""}.
                </p>
              ) : (
                <p>Nothing from this screen is on the public site yet.</p>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Branding</CardTitle>
            </CardHeader>
            <CardBody className="grid gap-4">
              <div>
                <Label>Public name</Label>
                <Input value={firmName} onChange={(e) => setFirmName(e.target.value)} />
              </div>
              <div>
                <Label>Tagline</Label>
                <Input value={tagline} onChange={(e) => setTagline(e.target.value)} />
              </div>
              <div>
                <Label>About</Label>
                <Textarea rows={4} value={about} onChange={(e) => setAbout(e.target.value)} />
              </div>
              <div>
                <Label>Accent</Label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {site.accents.map((choice) => (
                    <button
                      key={choice.key}
                      type="button"
                      aria-pressed={accent === choice.key}
                      onClick={() => setAccent(choice.key)}
                      className={`flex items-center gap-2 border px-3 py-2 text-sm ${accent === choice.key ? "border-navy" : "border-slate-200"}`}
                    >
                      <span className="h-4 w-4" style={{ background: choice.hex }} />
                      {choice.label}
                    </button>
                  ))}
                </div>
              </div>
              <p className="text-xs text-slate-500">Invoices and letterhead still use Firm settings. This name is only for the public site, and only after approval.</p>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Features</CardTitle>
            </CardHeader>
            <CardBody className="space-y-4">
              {site.features.map((feature) => (
                <label key={feature.key} className="flex items-start gap-3 border-b border-slate-100 pb-4 text-sm">
                  <input
                    type="checkbox"
                    className="mt-1"
                    checked={Boolean(requested[feature.key])}
                    onChange={(e) => setRequested({ ...requested, [feature.key]: e.target.checked })}
                  />
                  <span>
                    <span className="font-semibold text-slate-900">{feature.label}</span>
                    <span className="mt-1 block text-slate-600">{feature.description}</span>
                    <span className="mt-1 block text-xs text-slate-500">{featureLabel(feature, Boolean(requested[feature.key]))}</span>
                    {feature.note ? <span className="mt-1 block text-slate-600">Note: {feature.note}</span> : null}
                  </span>
                </label>
              ))}
              <p className="text-xs text-slate-500">Turning a feature on does not publish it. You can turn an approved feature off immediately.</p>
            </CardBody>
          </Card>

          <div className="flex flex-wrap gap-3">
            <Button
              variant="outline"
              onClick={() => {
                save().catch((err: unknown) => setError(err instanceof Error ? err.message : "Save failed"));
              }}
            >
              Save draft
            </Button>
            <Button
              onClick={() => {
                submit().catch((err: unknown) => setError(err instanceof Error ? err.message : "Submit failed"));
              }}
            >
              Submit for approval
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
