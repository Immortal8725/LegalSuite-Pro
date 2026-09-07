"use client";

import { useState } from "react";
import Link from "next/link";
import { apiPut } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import type { Tenant } from "@/lib/api";

export default function SettingsPage() {
  const { tenant, refreshTenant } = useAuth();
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
  });

  return (
    <div>
      <PageHeader
        title="Firm settings"
        subtitle="These fields feed invoices, the public website, and caller ID later."
        actions={
          tenant?.slug ? (
            <Link href={`/firm/${tenant.slug}`} className="text-sm font-semibold text-navy underline">
              View public site
            </Link>
          ) : null
        }
      />
      <ErrorBanner error={error} />
      {saved && <p className="mb-4 text-sm text-emerald-700">Saved. Your landing page updates immediately.</p>}
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
          <Input placeholder="State" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} />
          <Input placeholder="ZIP" value={form.zip} onChange={(e) => setForm({ ...form, zip: e.target.value })} />
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
                setError(null);
                setSaved(false);
                try {
                  const next = await apiPut<Tenant>("/api/v1/tenants/me", form);
                  refreshTenant(next);
                  setSaved(true);
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
    </div>
  );
}
