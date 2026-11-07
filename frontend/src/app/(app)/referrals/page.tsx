"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Card, CardBody } from "@/components/ui/card";

type Referral = { firmName: string; createdAt?: string };
type Page = { code: string; referredFirms: Referral[]; reward: string };

export default function ReferralsPage() {
  const [data, setData] = useState<Page | null>(null);
  const [error, setError] = useState<string | null>(null);
  function load() {
    apiGet<Page>("/api/v1/referrals")
      .then((next) => {
        setData(next);
        setError(null);
      })
      .catch((e) => setError(e.message));
  }
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Refer a firm" subtitle="Share your code. A firm that uses it is listed here." />
      <ErrorBanner error={error} onRetry={load} />
      {data && (
        <>
          <Card className="mb-4">
            <CardBody>
              <p className="text-sm text-slate-600">Your invite code</p>
              <p className="mt-1 font-mono text-2xl font-bold text-navy">{data.code}</p>
              <p className="mt-3 text-sm text-slate-600">{data.reward}</p>
            </CardBody>
          </Card>
          <h2 className="mb-2 text-sm font-bold text-navy">Firms that used your code</h2>
          {data.referredFirms.length === 0 ? (
            <p className="text-sm text-slate-600">No firms yet.</p>
          ) : (
            <ul className="space-y-2">
              {data.referredFirms.map((firm) => (
                <li key={firm.firmName + firm.createdAt} className="rounded-lg bg-white px-4 py-3 text-sm shadow-card">
                  {firm.firmName}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
