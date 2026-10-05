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
        subtitle="The toggle is a preference. Stripe and Twilio go live only when keys are in the environment. Do not paste secrets here."
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
              <p className="mb-4 text-xs text-slate-400">
                {row.statusNote}
                {(row.provider === "stripe" || row.provider === "twilio") && (
                  <span className="mt-1 block">
                    {row.liveCredentialsPresent
                      ? "Live keys are present on this process. Values are not shown."
                      : "Live keys are not on this process."}
                  </span>
                )}
              </p>
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
