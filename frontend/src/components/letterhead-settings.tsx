"use client";

import { useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost, apiPut } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import type { FirmBranding } from "@/lib/types";

const TEXT_FIELDS: { key: keyof FirmBranding; label: string; placeholder?: string }[] = [
  { key: "firmName", label: "Firm name" },
  { key: "addressLine1", label: "Street address" },
  { key: "city", label: "City or town" },
  { key: "province", label: "Province" },
  { key: "postalCode", label: "Postal code" },
  { key: "phone", label: "Telephone" },
  { key: "email", label: "Email" },
  { key: "refPrefix", label: "Reference prefix (Our Ref)", placeholder: "e.g. TN" },
  { key: "informationOfficer", label: "POPIA information officer" },
  { key: "trustAccountName", label: "Trust account name" },
  { key: "trustAccountNumber", label: "Trust account number" },
  { key: "bankName", label: "Bank" },
  { key: "branchName", label: "Branch name" },
  { key: "branchCode", label: "Branch code" },
];

/** Logo, contact details, trust banking and footer used on every generated document. */
export function LetterheadSettings({ canEdit }: { canEdit: boolean }) {
  const [b, setB] = useState<FirmBranding | null>(null);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const showLogo = (row: FirmBranding) => {
    if (!row.hasLogo) return setLogoUrl(null);
    apiBlob("/api/v1/firm/branding/logo")
      .then((blob) => setLogoUrl(URL.createObjectURL(blob)))
      .catch(() => setLogoUrl(null));
  };

  useEffect(() => {
    apiGet<FirmBranding>("/api/v1/firm/branding")
      .then((row) => {
        setB(row);
        showLogo(row);
      })
      .catch((e) => setError(e.message));
  }, []);

  if (!b) return null;
  const set = (k: keyof FirmBranding, v: string) => setB({ ...b, [k]: v });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Letterhead for generated documents</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4 text-sm">
        <ErrorBanner error={error} />
        {saved && (
          <p role="status" className="text-emerald-700">
            {saved}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex h-16 w-48 items-center justify-center rounded-lg border bg-white">
            {logoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={logoUrl} alt={`${b.firmName} logo`} className="max-h-14 max-w-[11rem] object-contain" />
            ) : (
              <span className="text-xs text-slate-600">No logo yet</span>
            )}
          </div>
          {canEdit && (
            <div>
              <Label htmlFor="logo-file">Upload logo (PNG or JPEG, up to 2 MB)</Label>
              <input
                id="logo-file"
                type="file"
                accept="image/png,image/jpeg"
                className="text-sm"
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setError(null);
                  try {
                    const fd = new FormData();
                    fd.append("file", file);
                    const row = await apiPost<FirmBranding>("/api/v1/firm/branding/logo", fd);
                    setB(row);
                    showLogo(row);
                    setSaved("Logo uploaded.");
                  } catch (err) {
                    setError(err instanceof Error ? err.message : "Could not upload the logo");
                  }
                }}
              />
            </div>
          )}
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {TEXT_FIELDS.map((f) => (
            <div key={f.key}>
              <Label htmlFor={`lh-${f.key}`}>{f.label}</Label>
              <Input
                id={`lh-${f.key}`}
                disabled={!canEdit}
                placeholder={f.placeholder}
                value={(b[f.key] as string) || ""}
                onChange={(e) => set(f.key, e.target.value)}
              />
            </div>
          ))}
          <div>
            <Label htmlFor="lh-colour">Letterhead colour</Label>
            <input
              id="lh-colour"
              type="color"
              disabled={!canEdit}
              className="h-10 w-20 rounded border"
              value={b.letterheadColour || "#1f2a44"}
              onChange={(e) => set("letterheadColour", e.target.value)}
            />
          </div>
          <div className="sm:col-span-2">
            <Label htmlFor="lh-footer">Footer text</Label>
            <Input
              id="lh-footer"
              disabled={!canEdit}
              placeholder={b.defaultFooterText}
              value={b.footerText || ""}
              onChange={(e) => set("footerText", e.target.value)}
            />
            <p className="mt-1 text-xs text-slate-600">
              Left blank, each attorney&apos;s documents use their own name and title from their profile, for example &quot;{b.defaultFooterText}&quot;.
            </p>
          </div>
        </div>
        {canEdit && (
          <Button
            onClick={async () => {
              setError(null);
              try {
                const { hasLogo: _h, logoUrl: _l, defaultFooterText: _d, attorneyName: _a, attorneyTitle: _t, ...body } = b;
                void _h; void _l; void _d; void _a; void _t;
                setB(await apiPut<FirmBranding>("/api/v1/firm/branding", body));
                setSaved("Letterhead saved.");
              } catch (err) {
                setError(err instanceof Error ? err.message : "Could not save the letterhead");
              }
            }}
          >
            Save letterhead
          </Button>
        )}
      </CardBody>
    </Card>
  );
}
