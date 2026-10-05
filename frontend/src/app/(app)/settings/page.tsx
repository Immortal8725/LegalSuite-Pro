"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiPost, apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import type { Tenant } from "@/lib/api";

export default function SettingsPage() {
  const { tenant, user, refreshTenant, reloadMe } = useAuth();
  const za = tenant?.country === "ZA" || tenant?.jurisdiction === "ZA";
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [form, setForm] = useState({
    firmName: tenant?.firmName || "",
    phone: tenant?.phone || "",
    email: tenant?.email || "",
    website: tenant?.website || "",
    addressLine1: tenant?.addressLine1 || "",
    city: tenant?.city || "",
    state: tenant?.state || "",
    zip: tenant?.zip || "",
    tagline: tenant?.tagline || "",
    template: "classic",
    ffcNumber: tenant?.ffcNumber || "",
    ffcExpiresOn: tenant?.ffcExpiresOn ? String(tenant.ffcExpiresOn).slice(0, 10) : "",
    ffcHolderName: tenant?.ffcHolderName || "",
    informationOfficerName: tenant?.informationOfficerName || "",
    informationOfficerEmail: tenant?.informationOfficerEmail || "",
    popiaOperatorAcknowledged: Boolean(tenant?.popiaOperatorAcknowledged),
  });
  const [totp, setTotp] = useState<{ secret?: string; otpauthUrl?: string } | null>(null);
  const [totpCode, setTotpCode] = useState("");
  const [totpPassword, setTotpPassword] = useState("");

  useEffect(() => {
    if (!tenant) return;
    setForm((prev) => ({
      ...prev,
      firmName: tenant.firmName || "",
      phone: tenant.phone || "",
      email: tenant.email || "",
      website: tenant.website || "",
      addressLine1: tenant.addressLine1 || "",
      city: tenant.city || "",
      state: tenant.state || "",
      zip: tenant.zip || "",
      tagline: tenant.tagline || "",
      ffcNumber: tenant.ffcNumber || "",
      ffcExpiresOn: tenant.ffcExpiresOn ? String(tenant.ffcExpiresOn).slice(0, 10) : "",
      ffcHolderName: tenant.ffcHolderName || "",
      informationOfficerName: tenant.informationOfficerName || "",
      informationOfficerEmail: tenant.informationOfficerEmail || "",
      popiaOperatorAcknowledged: Boolean(tenant.popiaOperatorAcknowledged),
    }));
  }, [tenant]);

  async function save(extra: Record<string, unknown> = {}) {
    setError(null);
    setSaved(false);
    const next = await apiPut<Tenant>("/api/v1/tenants/me", { ...form, ...extra });
    refreshTenant(next);
    setSaved(true);
    return next;
  }

  return (
    <div>
      <PageHeader
        title="Firm settings"
        subtitle="FFC, information officer, and the fields that feed invoices and the public site."
        actions={
          tenant?.slug ? (
            <Link href={`/firm/${tenant.slug}`} className="text-sm font-semibold text-navy underline">
              View public site
            </Link>
          ) : null
        }
      />
      <ErrorBanner error={error} />
      {saved && <p className="mb-4 text-sm text-emerald-700">Saved.</p>}
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Label>Firm name</Label>
              <Input value={form.firmName} onChange={(e) => setForm({ ...form, firmName: e.target.value })} />
            </div>
            <div>
              <Label>Phone</Label>
              <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div>
              <Label>Email</Label>
              <Input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="sm:col-span-2">
              <Label>Tagline</Label>
              <Input value={form.tagline} onChange={(e) => setForm({ ...form, tagline: e.target.value })} />
            </div>
            <div className="sm:col-span-2">
              <Label>Address</Label>
              <Input value={form.addressLine1} onChange={(e) => setForm({ ...form, addressLine1: e.target.value })} />
            </div>
            <Input placeholder="City" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
            <Input placeholder="Province / state" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} />
            <Input placeholder="Postal code" value={form.zip} onChange={(e) => setForm({ ...form, zip: e.target.value })} />
            <div>
              <Label>Landing template</Label>
              <Select value={form.template} onChange={(e) => setForm({ ...form, template: e.target.value })}>
                <option value="classic">Classic</option>
                <option value="modern">Modern</option>
                <option value="boutique">Boutique</option>
                <option value="corporate">Corporate</option>
              </Select>
            </div>
            <div className="sm:col-span-2">
              <Button
                onClick={async () => {
                  try {
                    await save();
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Save failed");
                  }
                }}
              >
                Save settings
              </Button>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{za ? "Fidelity Fund Certificate (LPA s 84)" : "Bar / IOLTA enrolment"}</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <p className="sm:col-span-2 text-sm text-slate-600">
              {za
                ? "Trust money cannot move on a South African tenant without a current FFC on this record."
                : "Store the firm IOLTA or bar enrolment reference."}
              {tenant?.ffcCurrent ? " Certificate on file is current." : " No current certificate."}
            </p>
            <div>
              <Label>Certificate number</Label>
              <Input value={form.ffcNumber} onChange={(e) => setForm({ ...form, ffcNumber: e.target.value })} />
            </div>
            <div>
              <Label>Expires</Label>
              <Input type="date" value={form.ffcExpiresOn} onChange={(e) => setForm({ ...form, ffcExpiresOn: e.target.value })} />
            </div>
            <div className="sm:col-span-2">
              <Label>Holder</Label>
              <Input value={form.ffcHolderName} onChange={(e) => setForm({ ...form, ffcHolderName: e.target.value })} />
            </div>
            <div className="sm:col-span-2">
              <Button
                onClick={async () => {
                  try {
                    await save();
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Save failed");
                  }
                }}
              >
                Save certificate
              </Button>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{za ? "POPIA information officer and PAIA manual" : "Privacy officer"}</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label>Information officer</Label>
              <Input
                value={form.informationOfficerName}
                onChange={(e) => setForm({ ...form, informationOfficerName: e.target.value })}
              />
            </div>
            <div>
              <Label>Officer email</Label>
              <Input
                type="email"
                value={form.informationOfficerEmail}
                onChange={(e) => setForm({ ...form, informationOfficerEmail: e.target.value })}
              />
            </div>
            <label className="sm:col-span-2 flex items-start gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.popiaOperatorAcknowledged}
                onChange={(e) => setForm({ ...form, popiaOperatorAcknowledged: e.target.checked })}
              />
              Hosting this practice system is an operator relationship (POPIA ss 20–22). Client files stay on the tenant
              row.
            </label>
            <div className="sm:col-span-2 flex flex-wrap gap-2">
              <Button
                onClick={async () => {
                  try {
                    await save({ popiaOperatorAcknowledged: form.popiaOperatorAcknowledged });
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Save failed");
                  }
                }}
              >
                Save officer
              </Button>
              <Button
                variant="outline"
                onClick={async () => {
                  try {
                    setError(null);
                    await save({ popiaOperatorAcknowledged: form.popiaOperatorAcknowledged });
                    const next = await apiPost<Tenant>("/api/v1/tenants/me/paia", {});
                    refreshTenant(next);
                    setSaved(true);
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Could not generate PAIA manual");
                  }
                }}
              >
                Generate PAIA manual
              </Button>
            </div>
            {tenant?.paiaManualBody && (
              <Textarea className="sm:col-span-2 min-h-[220px] font-mono text-xs" readOnly value={tenant.paiaManualBody} />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Authenticator (TOTP)</CardTitle>
          </CardHeader>
          <CardBody className="space-y-3 text-sm">
            <p className="text-slate-600">
              Demo logins stay without 2FA so the seeded password still works. Enrol here on your own user if you want
              a second factor.
            </p>
            <p className="font-semibold">{user?.totpEnabled ? "Authenticator is on for this user." : "Authenticator is off."}</p>
            {!user?.totpEnabled && (
              <>
                <Button
                  variant="outline"
                  onClick={async () => {
                    try {
                      setError(null);
                      const started = await apiPost<{ secret: string; otpauthUrl: string }>("/api/v1/auth/totp/start");
                      setTotp(started);
                    } catch (e) {
                      setError(e instanceof Error ? e.message : "Could not start enrolment");
                    }
                  }}
                >
                  Start enrolment
                </Button>
                {totp && (
                  <div className="space-y-2 rounded-lg border bg-slate-50 p-3">
                    <p className="font-mono text-xs break-all">Secret {totp.secret}</p>
                    <p className="font-mono text-[11px] break-all text-slate-500">{totp.otpauthUrl}</p>
                    <Label>Code from the app</Label>
                    <Input value={totpCode} onChange={(e) => setTotpCode(e.target.value)} placeholder="123456" />
                    <Button
                      onClick={async () => {
                        try {
                          setError(null);
                          await apiPost("/api/v1/auth/totp/confirm", { code: totpCode });
                          setTotp(null);
                          setTotpCode("");
                          await reloadMe();
                        } catch (e) {
                          setError(e instanceof Error ? e.message : "Could not confirm");
                        }
                      }}
                    >
                      Confirm and enable
                    </Button>
                  </div>
                )}
              </>
            )}
            {user?.totpEnabled && (
              <div className="grid gap-2 sm:grid-cols-2">
                <div>
                  <Label>Current code or password</Label>
                  <Input value={totpCode} onChange={(e) => setTotpCode(e.target.value)} placeholder="Authenticator code" />
                </div>
                <div>
                  <Label>Password</Label>
                  <Input type="password" value={totpPassword} onChange={(e) => setTotpPassword(e.target.value)} />
                </div>
                <Button
                  variant="outline"
                  onClick={async () => {
                    try {
                      setError(null);
                      await apiPost("/api/v1/auth/totp/disable", { code: totpCode, password: totpPassword });
                      setTotpCode("");
                      setTotpPassword("");
                      await reloadMe();
                    } catch (e) {
                      setError(e instanceof Error ? e.message : "Could not disable");
                    }
                  }}
                >
                  Disable authenticator
                </Button>
              </div>
            )}
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>LegalSuite product billing</CardTitle>
          </CardHeader>
          <CardBody>
            <p className="mb-3 text-sm text-slate-500">
              The Light seat and phone minutes are billed to the firm through PayFast. That is separate from client invoices and from trust.
            </p>
            <Link href="/product-billing" className="text-sm font-semibold text-navy underline">
              Open product billing
            </Link>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
