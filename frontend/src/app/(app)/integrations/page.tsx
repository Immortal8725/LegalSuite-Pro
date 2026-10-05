"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import type { Integration } from "@/lib/types";

export default function IntegrationsPage() {
  const [rows, setRows] = useState<Integration[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () => apiGet<Integration[]>("/api/v1/integrations").then(setRows).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Integrations"
        subtitle="Connect or disconnect without pasting production secrets. Local demo stores the toggle only."
      />
      <ErrorBanner error={error} />
      <div className="grid gap-4 sm:grid-cols-2">
        {rows.map((row) => (
          <Card key={row.provider}>
            <CardHeader>
              <CardTitle>
                {row.name}{" "}
                <span className="ml-2 text-[11px] font-bold uppercase text-slate-400">{row.category}</span>
              </CardTitle>
            </CardHeader>
            <CardBody>
              <p className="mb-3 text-sm text-slate-500">{row.description}</p>
              <p className="mb-4 text-xs text-slate-400">{row.statusNote}</p>
              {row.provider === "twilio" && (
                <p className="mb-4 text-xs text-slate-500">
                  {row.credentialsPresent
                    ? "Server credentials are present. They are not shown here."
                    : "No Twilio credentials on this server yet. Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in the environment."}
                  {row.publicBaseUrlSet ? " Public callback address is set." : " TWILIO_PUBLIC_BASE_URL is still empty."}
                </p>
              )}
              {row.connected ? (
                <Button
                  variant="outline"
                  onClick={async () => {
                    await apiPost(`/api/v1/integrations/${row.provider}/disconnect`, {});
                    load();
                  }}
                >
                  Disconnect
                </Button>
              ) : (
                <Button
                  onClick={async () => {
                    await apiPost(`/api/v1/integrations/${row.provider}/connect`, {});
                    load();
                  }}
                >
                  Connect
                </Button>
              )}
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}
