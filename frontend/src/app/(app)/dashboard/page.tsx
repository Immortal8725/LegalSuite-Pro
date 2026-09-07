"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { apiGet } from "@/lib/api";
import { money } from "@/lib/utils";
import { Loading, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime } from "@/lib/utils";
import type { Dashboard } from "@/lib/types";

const ACTIONS = [
  ["New case", "/cases"],
  ["New client", "/clients"],
  ["Log time", "/time"],
  ["Invoice", "/billing"],
  ["Upload", "/documents"],
  ["Task", "/tasks"],
  ["Event", "/calendar"],
  ["Conflict check", "/conflicts"],
];

export default function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Dashboard>("/api/v1/dashboard")
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return <Loading />;

  const stats = [
    ["Active cases", data.activeCases, "⚖️"],
    ["Revenue collected", money(data.revenueMtd), "💰"],
    ["Hours billed", data.hoursBilled, "⏱️"],
    ["Open tasks", data.pendingTasks, "📋"],
    ["Clients", data.totalClients, "👥"],
  ];

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle={`Welcome back, ${user?.firstName}. Matters, money, and the next court date — in one glance.`}
        actions={
          <Link href="/cases" className="rounded-lg bg-gold px-4 py-2 text-sm font-bold text-navy-dark">
            New case
          </Link>
        }
      />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {stats.map(([label, value, icon]) => (
          <Card key={String(label)} className="p-5">
            <p className="text-xs text-slate-500">
              {icon} {label}
            </p>
            <p className="mt-1 text-2xl font-extrabold text-navy">{value}</p>
          </Card>
        ))}
      </div>
      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Quick actions</CardTitle>
        </CardHeader>
        <CardBody className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
          {ACTIONS.map(([label, href]) => (
            <Link key={href} href={href} className="rounded-xl border p-3 text-center text-xs font-semibold hover:border-gold">
              {label}
            </Link>
          ))}
        </CardBody>
      </Card>
      <div className="mb-6 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Upcoming events</CardTitle>
            <Link href="/calendar" className="text-sm text-navy">
              Calendar
            </Link>
          </CardHeader>
          <CardBody className="space-y-4">
            {data.upcomingEvents.length === 0 && <p className="text-sm text-slate-500">No hearings on the near calendar.</p>}
            {data.upcomingEvents.map((e) => (
              <div key={e.id}>
                <p className="text-xs text-slate-400">{formatDateTime(e.startTime)}</p>
                <p className="font-semibold">{e.title}</p>
                <p className="text-xs text-slate-500">{e.location}</p>
              </div>
            ))}
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Collections</CardTitle>
          </CardHeader>
          <CardBody>
            <p className="text-sm text-slate-500">Outstanding invoices</p>
            <p className="text-2xl font-extrabold text-amber-700">{money(data.outstanding)}</p>
            <p className="mt-4 text-sm text-slate-500">Voice calls logged</p>
            <p className="text-2xl font-extrabold text-navy">{data.callCount}</p>
          </CardBody>
        </Card>
      </div>
      <TableWrap>
        <thead>
          <tr>
            <Th>Case</Th>
            <Th>Title</Th>
            <Th>Area</Th>
            <Th>Client</Th>
            <Th>Status</Th>
          </tr>
        </thead>
        <tbody>
          {data.recentCases.map((c) => (
            <tr key={c.id} className="hover:bg-slate-50">
              <Td className="font-mono text-navy">
                <Link href={`/cases/${c.id}`}>{c.caseNumber}</Link>
              </Td>
              <Td>{c.title}</Td>
              <Td>{c.practiceArea}</Td>
              <Td>{c.clientName}</Td>
              <Td>
                <StatusBadge status={c.status} />
              </Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
    </div>
  );
}
