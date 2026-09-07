"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import type { Dashboard } from "@/lib/types";
import { money } from "@/lib/utils";

type Report = {
  overview: Dashboard;
  revenueByPracticeArea: Record<string, number>;
  aging: { current: number; days30: number; days60: number; days90: number };
};

export default function ReportsPage() {
  const [data, setData] = useState<Report | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Report>("/api/v1/reports")
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  const o = data?.overview;
  const maxArea = Math.max(1, ...Object.values(data?.revenueByPracticeArea || { x: 1 }));

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle="Realization, collections, and practice-area mix — enough to run the firm this month."
      />
      <ErrorBanner error={error} />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          ["Active matters", o?.activeCases ?? "—"],
          ["Hours billed", o?.hoursBilled ?? "—"],
          ["Collected", money(o?.revenueMtd)],
          ["AR outstanding", money(o?.outstanding)],
          ["Open tasks", o?.pendingTasks ?? "—"],
          ["Clients", o?.totalClients ?? "—"],
        ].map(([k, v]) => (
          <Card key={String(k)}>
            <CardHeader>
              <CardTitle>{k}</CardTitle>
            </CardHeader>
            <CardBody className="text-2xl font-semibold text-navy">{String(v)}</CardBody>
          </Card>
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Revenue by practice area (rate mix)</CardTitle>
          </CardHeader>
          <CardBody className="space-y-3">
            {Object.entries(data?.revenueByPracticeArea || {}).map(([area, amt]) => (
              <div key={area} className="flex items-center gap-3">
                <span className="w-28 text-sm text-slate-600">{area}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-navy" style={{ width: `${(Number(amt) / maxArea) * 100}%` }} />
                </div>
                <span className="w-20 text-right text-sm font-semibold">{money(amt)}</span>
              </div>
            ))}
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Aging (placeholder until LEDES export)</CardTitle>
          </CardHeader>
          <CardBody className="space-y-2 text-sm">
            <p>Current: {money(data?.aging.current)}</p>
            <p>30 days: {money(data?.aging.days30)}</p>
            <p>60 days: {money(data?.aging.days60)}</p>
            <p>90 days: {money(data?.aging.days90)}</p>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
