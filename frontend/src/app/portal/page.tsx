"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import type { Invoice, Matter } from "@/lib/types";
import { money } from "@/lib/utils";

export default function PortalHome() {
  const { user, tenant, logout } = useAuth();
  const [cases, setCases] = useState<Matter[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      apiGet<{ cases: Matter[] }>("/api/v1/portal/cases"),
      apiGet<Invoice[]>("/api/v1/portal/invoices"),
    ])
      .then(([c, inv]) => {
        setCases(c.cases || []);
        setInvoices(inv);
      })
      .catch((e) => setError(e.message));
  }, []);

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b bg-white px-6 py-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-gold">Client portal</p>
          <h1 className="text-lg font-bold text-navy">{tenant?.firmName}</h1>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span>{user?.fullName}</span>
          <Button variant="outline" size="sm" onClick={logout}>
            Sign out
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <ErrorBanner error={error} />
        <Card>
          <CardHeader>
            <CardTitle>Your matters</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            <TableWrap>
              <thead>
                <tr>
                  <Th>#</Th>
                  <Th>Title</Th>
                  <Th>Status</Th>
                  <Th>Area</Th>
                </tr>
              </thead>
              <tbody>
                {cases.map((c) => (
                  <tr key={c.id}>
                    <Td className="font-mono">{c.caseNumber}</Td>
                    <Td>{c.title}</Td>
                    <Td>
                      <StatusBadge status={c.status} />
                    </Td>
                    <Td>{c.practiceArea}</Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          </CardBody>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Invoices</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            <TableWrap>
              <thead>
                <tr>
                  <Th>Number</Th>
                  <Th>Status</Th>
                  <Th>Total</Th>
                  <Th>Balance</Th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((i) => (
                  <tr key={i.id}>
                    <Td>{i.invoiceNumber}</Td>
                    <Td>
                      <StatusBadge status={i.status} />
                    </Td>
                    <Td>{money(i.total)}</Td>
                    <Td>{money(i.balanceDue)}</Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          </CardBody>
        </Card>
      </main>
    </div>
  );
}
