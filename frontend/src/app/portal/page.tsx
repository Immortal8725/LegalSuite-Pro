"use client";

import { Fragment, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { InvoiceLines } from "@/components/invoice-lines";
import { StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import type { Invoice, Matter } from "@/lib/types";
import { Input, Label } from "@/components/ui/input";
import { money, moneyExact } from "@/lib/utils";

export default function PortalHome() {
  const { user, tenant, logout } = useAuth();
  const [cases, setCases] = useState<Matter[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [proofFor, setProofFor] = useState<string | null>(null);
  const [proofAmount, setProofAmount] = useState("");
  const [proofRef, setProofRef] = useState("");
  const [proofNote, setProofNote] = useState("");
  const [proofFile, setProofFile] = useState<File | null>(null);
  const [proofMessage, setProofMessage] = useState<string | null>(null);

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
                  <Th>Lines</Th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((i) => (
                  <Fragment key={i.id}>
                    <tr>
                      <Td>{i.invoiceNumber}</Td>
                      <Td>
                        <StatusBadge status={i.status} />
                      </Td>
                      <Td>{money(i.total)}</Td>
                      <Td>{money(i.balanceDue)}</Td>
                      <Td>
                        <button className="text-sm font-semibold text-navy" onClick={() => setExpanded(expanded === i.id ? null : i.id)}>
                          {expanded === i.id ? "Hide lines" : "Lines"}
                        </button>
                      </Td>
                    </tr>
                    {expanded === i.id && (
                      <tr>
                        <td colSpan={5} className="border-t border-slate-100 p-0">
                          <InvoiceLines raw={i.rawLineItems} />
                          <div className="flex flex-wrap justify-end gap-6 px-4 py-3 text-sm">
                            <span>Subtotal {moneyExact(i.subtotal)}</span>
                            {Number(i.taxAmount) > 0 && <span>VAT {moneyExact(i.taxAmount)}</span>}
                            <span className="font-semibold">Total {moneyExact(i.total)}</span>
                          </div>
                          {i.notes && <p className="px-4 pb-3 text-xs text-slate-500">{i.notes}</p>}
                          {Number(i.balanceDue) > 0 && (
                            <div className="border-t border-slate-100 px-4 py-3">
                              <p className="text-sm font-semibold text-navy">Proof of payment</p>
                              <p className="mt-1 text-xs text-slate-500">
                                Upload a bank deposit slip or EFT confirmation (PDF, JPEG, or PNG, up to 10 MB). The firm reviews it before the invoice is marked paid.
                              </p>
                              {(i.proofs || []).length > 0 && (
                                <ul className="mt-2 space-y-1 text-sm">
                                  {(i.proofs || []).map((p) => (
                                    <li key={p.id}>
                                      {p.fileName || "Slip"} · {moneyExact(p.amountClaimed)} · {p.status.replaceAll("_", " ")}
                                      {p.reviewNote ? ` · ${p.reviewNote}` : ""}
                                    </li>
                                  ))}
                                </ul>
                              )}
                              {proofFor === i.id ? (
                                <form
                                  className="mt-3 space-y-2"
                                  onSubmit={async (e) => {
                                    e.preventDefault();
                                    if (!proofFile) {
                                      setError("Choose a PDF, JPEG, or PNG.");
                                      return;
                                    }
                                    try {
                                      setError(null);
                                      setProofMessage(null);
                                      const fd = new FormData();
                                      fd.append("file", proofFile);
                                      if (proofAmount) fd.append("amount", proofAmount);
                                      if (proofRef) fd.append("reference", proofRef);
                                      if (proofNote) fd.append("note", proofNote);
                                      await apiPost(`/api/v1/portal/invoices/${i.id}/proofs`, fd);
                                      setProofMessage("Proof submitted. The firm will review it.");
                                      setProofFor(null);
                                      const next = await apiGet<Invoice[]>("/api/v1/portal/invoices");
                                      setInvoices(next);
                                    } catch (err) {
                                      setError(err instanceof Error ? err.message : "Could not submit the proof");
                                    }
                                  }}
                                >
                                  <Label>File</Label>
                                  <Input
                                    type="file"
                                    accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png"
                                    onChange={(e) => setProofFile(e.target.files?.[0] || null)}
                                  />
                                  <Label className="mt-2">Amount claimed</Label>
                                  <Input value={proofAmount} onChange={(e) => setProofAmount(e.target.value)} />
                                  <Label className="mt-2">Reference</Label>
                                  <Input value={proofRef} onChange={(e) => setProofRef(e.target.value)} />
                                  <Label className="mt-2">Note</Label>
                                  <Input value={proofNote} onChange={(e) => setProofNote(e.target.value)} />
                                  <div className="flex gap-2 pt-2">
                                    <Button type="submit" size="sm">
                                      Submit proof
                                    </Button>
                                    <Button type="button" size="sm" variant="outline" onClick={() => setProofFor(null)}>
                                      Cancel
                                    </Button>
                                  </div>
                                </form>
                              ) : (
                                <Button
                                  className="mt-3"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => {
                                    setProofFor(i.id);
                                    setProofAmount(Number(i.balanceDue ?? 0).toFixed(2));
                                    setProofRef("");
                                    setProofNote("");
                                    setProofFile(null);
                                    setProofMessage(null);
                                  }}
                                >
                                  Submit proof of payment
                                </Button>
                              )}
                              {proofMessage && <p className="mt-2 text-sm text-emerald-700">{proofMessage}</p>}
                            </div>
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </TableWrap>
          </CardBody>
        </Card>
      </main>
    </div>
  );
}
