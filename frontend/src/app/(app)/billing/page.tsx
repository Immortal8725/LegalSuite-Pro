"use client";

import { Fragment, useEffect, useState } from "react";
import { apiBlob, apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { InvoiceLines } from "@/components/invoice-lines";
import { PayLinkButton } from "@/components/pay-link";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import type { Invoice, Matter, Party, PaymentProof } from "@/lib/types";

function today() {
  return new Date().toISOString().slice(0, 10);
}

function asMoney(n?: number | null) {
  const v = Number(n ?? 0);
  return Number.isFinite(v) ? v.toFixed(2) : "0.00";
}

function message(e: unknown, fallback: string) {
  return e instanceof Error ? e.message : fallback;
}

async function openProofFile(id: string) {
  const blob = await apiBlob(`/api/v1/payment-proofs/${id}/file`);
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export default function BillingPage() {
  const [rows, setRows] = useState<Invoice[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [pending, setPending] = useState<PaymentProof[]>([]);
  const [open, setOpen] = useState(false);
  const [clientId, setClientId] = useState("");
  const [invoiceCaseId, setInvoiceCaseId] = useState("");
  const [invoiceMatters, setInvoiceMatters] = useState<Matter[]>([]);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [payFor, setPayFor] = useState<Invoice | null>(null);
  const [payForm, setPayForm] = useState({ amount: "", method: "eft", paidAt: today(), reference: "", note: "" });
  const [writeFor, setWriteFor] = useState<Invoice | null>(null);
  const [writeForm, setWriteForm] = useState({ amount: "", reason: "" });
  const [trustFor, setTrustFor] = useState<Invoice | null>(null);
  const [trustForm, setTrustForm] = useState({ accountId: "", amount: "", note: "" });
  const [proofFor, setProofFor] = useState<Invoice | null>(null);
  const [proofForm, setProofForm] = useState({ amount: "", reference: "", note: "" });
  const [proofFile, setProofFile] = useState<File | null>(null);
  const [acceptFor, setAcceptFor] = useState<PaymentProof | null>(null);
  const [acceptForm, setAcceptForm] = useState({ amount: "", method: "eft", note: "" });
  const [rejectFor, setRejectFor] = useState<PaymentProof | null>(null);
  const [rejectNote, setRejectNote] = useState("");

  const load = async () => {
    const [invoices, proofs] = await Promise.all([
      apiGet<Invoice[]>("/api/v1/invoices"),
      apiGet<PaymentProof[]>("/api/v1/payment-proofs?status=pending_review"),
    ]);
    setRows(invoices);
    setPending(proofs);
  };

  useEffect(() => {
    load().catch((e) => setError(message(e, "Could not load invoices")));
    apiGet<Party[]>("/api/v1/clients").then(setClients).catch(() => undefined);
    apiGet<Matter[]>("/api/v1/cases").then(setInvoiceMatters).catch(() => setInvoiceMatters([]));
  }, []);

  const clientName = (id?: string) => clients.find((c) => c.id === id)?.displayName || "Client";
  const openBalance = (i: Invoice) => Number(i.balanceDue ?? 0) > 0 && i.status !== "void";

  const suggestedTrust = (invoice: Invoice, accountId: string) => {
    const row = invoice.trustAccounts?.find((a) => a.accountId === accountId);
    const ledger = row ? Number(row.clientLedger) : Number(invoice.clientTrustAvailable ?? 0);
    return Math.min(Number(invoice.balanceDue ?? 0), Number.isFinite(ledger) ? ledger : 0);
  };

  return (
    <div>
      <PageHeader
        title="Billing & invoices"
        subtitle="Draft from unbilled time and expenses. Record payments, write-offs, trust applied to fees, and proof of payment. This is not the Owlaw seat or the phone-minute charge."
        actions={
          <Button
            onClick={() => {
              setGenerateError(null);
              setOpen(true);
            }}
          >
            Generate from unbilled time and expenses
          </Button>
        }
      />
      {error && (
        <div className="fixed left-1/2 top-4 z-[90] w-[min(32rem,calc(100%-2rem))] -translate-x-1/2">
          <ErrorBanner error={error} />
        </div>
      )}

      {pending.length > 0 && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>Proof of payment waiting for review</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            <TableWrap>
              <thead>
                <tr>
                  <Th>Invoice</Th>
                  <Th>Claimed</Th>
                  <Th>File</Th>
                  <Th>Reference</Th>
                  <Th></Th>
                </tr>
              </thead>
              <tbody>
                {pending.map((p) => (
                  <tr key={p.id}>
                    <Td className="font-mono">{p.invoiceNumber}</Td>
                    <Td>{moneyExact(p.amountClaimed)}</Td>
                    <Td>{p.fileName || "Slip"}</Td>
                    <Td>{p.reference || ""}</Td>
                    <Td className="space-x-3">
                      <button
                        className="text-sm font-semibold text-navy"
                        onClick={() => openProofFile(p.id).catch((e) => setError(message(e, "Could not open the file")))}
                      >
                        Open file
                      </button>
                      <button
                        className="text-sm font-semibold text-emerald-700"
                        onClick={() => {
                          setError(null);
                          setAcceptFor(p);
                          setAcceptForm({ amount: asMoney(p.amountClaimed), method: "eft", note: "" });
                        }}
                      >
                        Accept
                      </button>
                      <button
                        className="text-sm font-semibold text-red-700"
                        onClick={() => {
                          setError(null);
                          setRejectFor(p);
                          setRejectNote("");
                        }}
                      >
                        Reject
                      </button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          </CardBody>
        </Card>
      )}

      <TableWrap>
        <thead>
          <tr>
            <Th>Invoice</Th>
            <Th>Client</Th>
            <Th>Issued</Th>
            <Th>Due</Th>
            <Th>Total</Th>
            <Th>Balance</Th>
            <Th>Status</Th>
            <Th></Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((i) => (
            <Fragment key={i.id}>
              <tr>
                <Td className="font-mono font-semibold">{i.invoiceNumber}</Td>
                <Td>{clientName(i.clientId)}</Td>
                <Td>{i.dateIssued}</Td>
                <Td>{i.dateDue}</Td>
                <Td>{moneyExact(i.total)}</Td>
                <Td>{moneyExact(i.balanceDue)}</Td>
                <Td>
                  <StatusBadge status={i.status} />
                </Td>
                <Td className="space-x-2 whitespace-nowrap">
                  <button className="text-sm font-semibold text-navy" aria-expanded={expanded === i.id} onClick={() => setExpanded(expanded === i.id ? null : i.id)}>
                    {expanded === i.id ? "Hide" : "Detail"}
                  </button>
                  {i.status === "draft" && (
                    <button className="text-sm font-semibold text-navy" onClick={() => apiPost(`/api/v1/invoices/${i.id}/send`, {}).then(load).catch((e) => setError(message(e, "Could not send")))}>
                      Send
                    </button>
                  )}
                  {openBalance(i) && (
                    <>
                      <button
                        className="text-sm font-semibold text-emerald-700"
                        onClick={() => {
                          setError(null);
                          setPayFor(i);
                          setPayForm({ amount: asMoney(i.balanceDue), method: "eft", paidAt: today(), reference: "", note: "" });
                        }}
                      >
                        Record payment
                      </button>
                      <button
                        className="text-sm font-semibold text-navy"
                        onClick={() => {
                          setError(null);
                          setWriteFor(i);
                          setWriteForm({ amount: asMoney(i.balanceDue), reason: "" });
                        }}
                      >
                        Write off
                      </button>
                      <PayLinkButton invoiceId={i.id} clientId={i.clientId} />
                      <button
                        className="text-sm font-semibold text-navy"
                        onClick={() => {
                          setError(null);
                          const accountId = i.trustAccounts?.[0]?.accountId || "";
                          setTrustFor(i);
                          setTrustForm({ accountId, amount: asMoney(suggestedTrust(i, accountId)), note: "" });
                        }}
                      >
                        Apply trust
                      </button>
                      <button
                        className="text-sm font-semibold text-navy"
                        onClick={() => {
                          setError(null);
                          setProofFor(i);
                          setProofFile(null);
                          setProofForm({ amount: asMoney(i.balanceDue), reference: "", note: "" });
                        }}
                      >
                        Add proof
                      </button>
                    </>
                  )}
                </Td>
              </tr>
              {expanded === i.id && (
                <tr>
                  <td colSpan={8} className="border-t border-slate-100 bg-slate-50/60 p-0">
                    <InvoiceLines raw={i.rawLineItems} />
                    <div className="flex flex-wrap justify-end gap-6 px-4 py-3 text-sm">
                      <span>Subtotal {moneyExact(i.subtotal)}</span>
                      {Number(i.taxAmount) > 0 && <span>VAT {moneyExact(i.taxAmount)}</span>}
                      <span>Total {moneyExact(i.total)}</span>
                      <span>Paid {moneyExact(i.amountPaid)}</span>
                      {Number(i.writeOffAmount) > 0 && <span>Written off {moneyExact(i.writeOffAmount)}</span>}
                      <span className="font-semibold">Balance {moneyExact(i.balanceDue)}</span>
                    </div>
                    {i.notes && <p className="px-4 pb-3 text-xs text-slate-600">{i.notes}</p>}
                    <div className="grid grid-cols-1 gap-4 px-4 pb-4 md:grid-cols-2">
                      <div>
                        <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-600">Payments</p>
                        {(i.payments || []).length === 0 ? (
                          <p className="text-sm text-slate-600">No payments recorded.</p>
                        ) : (
                          <ul className="space-y-1 text-sm">
                            {(i.payments || []).map((p) => (
                              <li key={p.id} className="flex justify-between gap-3">
                                <span>
                                  {p.paidAt} · {p.method}
                                  {p.reference ? ` · ${p.reference}` : ""}
                                  {p.note ? ` · ${p.note}` : ""}
                                </span>
                                <span className="font-semibold">{moneyExact(p.amount)}</span>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                      <div>
                        <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-600">Write-offs</p>
                        {(i.writeOffs || []).length === 0 ? (
                          <p className="text-sm text-slate-600">No write-offs.</p>
                        ) : (
                          <ul className="space-y-1 text-sm">
                            {(i.writeOffs || []).map((w) => (
                              <li key={w.id}>
                                <span className="font-semibold">{moneyExact(w.amount)}</span> · {w.reason}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                      <div className="md:col-span-2">
                        <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-600">Proof of payment</p>
                        {(i.proofs || []).length === 0 ? (
                          <p className="text-sm text-slate-600">No slips on this invoice.</p>
                        ) : (
                          <ul className="space-y-2 text-sm">
                            {(i.proofs || []).map((p) => (
                              <li key={p.id} className="flex flex-wrap items-center gap-3">
                                <StatusBadge status={p.status} />
                                <span>
                                  {moneyExact(p.amountClaimed)} · {p.fileName || "File"}
                                  {p.reference ? ` · ${p.reference}` : ""}
                                </span>
                                <button className="font-semibold text-navy" onClick={() => openProofFile(p.id).catch((e) => setError(message(e, "Could not open the file")))}>
                                  Open file
                                </button>
                                {p.reviewNote && <span className="text-slate-600">{p.reviewNote}</span>}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </TableWrap>

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Draft invoice"
        footer={
          <Button
            disabled={!clientId || generating}
            onClick={async () => {
              setGenerating(true);
              setGenerateError(null);
              try {
                await apiPost("/api/v1/invoices/generate", { clientId, caseId: invoiceCaseId || undefined });
                setOpen(false);
                await load();
              } catch (e) {
                setGenerateError(e instanceof Error ? e.message : "Could not create the draft");
              } finally {
                setGenerating(false);
              }
            }}
          >
            {generating ? "Creating…" : "Create draft"}
          </Button>
        }
      >
        <ErrorBanner error={generateError} />
        <p className="mb-3 text-sm text-slate-600">
          Bills unbilled time and expenses on this client&apos;s matters only. Work logged without a matter stays off the invoice.
        </p>
        <Select
          aria-label="Client"
          value={clientId}
          onChange={(e) => {
            setClientId(e.target.value);
            setInvoiceCaseId("");
          }}
        >
          <option value="">Select client</option>
          {clients.map((c) => (
            <option key={c.id} value={c.id}>
              {c.displayName}
            </option>
          ))}
        </Select>
        {clientId && (
          <Select className="mt-3" aria-label="Matter" value={invoiceCaseId} onChange={(e) => setInvoiceCaseId(e.target.value)}>
            <option value="">All of this client&apos;s matters</option>
            {invoiceMatters
              .filter((m) => m.clientId === clientId)
              .map((m) => (
                <option key={m.id} value={m.id}>
                  {m.caseNumber} {m.title}
                </option>
              ))}
          </Select>
        )}
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!payFor}
        onClose={() => {
          setPayFor(null);
          setDialogError(null);
        }}
        title="Record payment"
        footer={
          <Button
            onClick={async () => {
              if (!payFor) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/invoices/${payFor.id}/payments`, payForm);
                setPayFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not record the payment"));
              }
            }}
          >
            Record payment
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Balance due on {payFor?.invoiceNumber} is {moneyExact(payFor?.balanceDue)}. A payment cannot exceed that balance.
        </p>
        <Label>Amount</Label>
        <Input aria-label="Amount" value={payForm.amount} onChange={(e) => setPayForm({ ...payForm, amount: e.target.value })} />
        <Label className="mt-3">Method</Label>
        <Select aria-label="Method" value={payForm.method} onChange={(e) => setPayForm({ ...payForm, method: e.target.value })}>
          <option value="eft">EFT</option>
          <option value="cash">Cash</option>
          <option value="card">Card</option>
          <option value="other">Other</option>
        </Select>
        <Label className="mt-3">Date</Label>
        <Input aria-label="Date" type="date" value={payForm.paidAt} onChange={(e) => setPayForm({ ...payForm, paidAt: e.target.value })} />
        <Label className="mt-3">Reference</Label>
        <Input aria-label="Reference" value={payForm.reference} onChange={(e) => setPayForm({ ...payForm, reference: e.target.value })} />
        <Label className="mt-3">Note</Label>
        <Input aria-label="Note" value={payForm.note} onChange={(e) => setPayForm({ ...payForm, note: e.target.value })} />
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!writeFor}
        onClose={() => {
          setWriteFor(null);
          setDialogError(null);
        }}
        title="Write off balance"
        footer={
          <Button
            variant="danger"
            onClick={async () => {
              if (!writeFor) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/invoices/${writeFor.id}/write-off`, writeForm);
                setWriteFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not write off the balance"));
              }
            }}
          >
            Confirm write-off
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          This writes off {writeForm.amount || asMoney(writeFor?.balanceDue)} on {writeFor?.invoiceNumber}. The invoice total stays {moneyExact(writeFor?.total)}. Amount already paid stays {moneyExact(writeFor?.amountPaid)}. The write-off is stored on its own so the original total, payments, and write-off still reconcile.
        </p>
        <Label>Amount</Label>
        <Input aria-label="Amount" value={writeForm.amount} onChange={(e) => setWriteForm({ ...writeForm, amount: e.target.value })} />
        <Label className="mt-3">Reason</Label>
        <Textarea aria-label="Reason" value={writeForm.reason} onChange={(e) => setWriteForm({ ...writeForm, reason: e.target.value })} placeholder="Why this balance is being written off" />
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!trustFor}
        onClose={() => {
          setTrustFor(null);
          setDialogError(null);
        }}
        title="Apply trust to fees"
        footer={
          <Button
            onClick={async () => {
              if (!trustFor) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/invoices/${trustFor.id}/apply-trust`, trustForm);
                setTrustFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not apply trust"));
              }
            }}
          >
            Apply trust to fees
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          This withdraws the amount from this client&apos;s trust ledger and records one payment on {trustFor?.invoiceNumber}. Only this client&apos;s ledger is used.
        </p>
        <Label>Trust account</Label>
        <Select aria-label="Trust account"
          value={trustForm.accountId}
          onChange={(e) => {
            const accountId = e.target.value;
            setTrustForm({
              ...trustForm,
              accountId,
              amount: trustFor ? asMoney(suggestedTrust(trustFor, accountId)) : trustForm.amount,
            });
          }}
        >
          <option value="">Select account</option>
          {(trustFor?.trustAccounts || []).map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.accountName} ({moneyExact(a.clientLedger)} for this client)
            </option>
          ))}
        </Select>
        <Label className="mt-3">Amount</Label>
        <Input aria-label="Amount" value={trustForm.amount} onChange={(e) => setTrustForm({ ...trustForm, amount: e.target.value })} />
        <Label className="mt-3">Note</Label>
        <Input aria-label="Note" value={trustForm.note} onChange={(e) => setTrustForm({ ...trustForm, note: e.target.value })} />
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!proofFor}
        onClose={() => {
          setProofFor(null);
          setDialogError(null);
        }}
        title="Add proof of payment"
        footer={
          <Button
            onClick={async () => {
              if (!proofFor || !proofFile) {
                setDialogError("Choose a PDF, JPEG, or PNG.");
                return;
              }
              try {
                setDialogError(null);
                const fd = new FormData();
                fd.append("file", proofFile);
                if (proofForm.amount) fd.append("amount", proofForm.amount);
                if (proofForm.reference) fd.append("reference", proofForm.reference);
                if (proofForm.note) fd.append("note", proofForm.note);
                await apiPost(`/api/v1/invoices/${proofFor.id}/proofs`, fd);
                setProofFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not submit the proof"));
              }
            }}
          >
            Submit for review
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Upload the deposit slip or EFT confirmation for {proofFor?.invoiceNumber}. It stays pending until a staff member accepts or rejects it. Accepting records the payment. PDF, JPEG, or PNG, up to 10 MB.
        </p>
        <Label>File</Label>
        <Input aria-label="File"
          type="file"
          accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png"
          onChange={(e) => setProofFile(e.target.files?.[0] || null)}
        />
        <Label className="mt-3">Amount claimed</Label>
        <Input aria-label="Amount claimed" value={proofForm.amount} onChange={(e) => setProofForm({ ...proofForm, amount: e.target.value })} />
        <Label className="mt-3">Reference</Label>
        <Input aria-label="Reference" value={proofForm.reference} onChange={(e) => setProofForm({ ...proofForm, reference: e.target.value })} />
        <Label className="mt-3">Note</Label>
        <Input aria-label="Note" value={proofForm.note} onChange={(e) => setProofForm({ ...proofForm, note: e.target.value })} />
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!acceptFor}
        onClose={() => {
          setAcceptFor(null);
          setDialogError(null);
        }}
        title="Accept proof of payment"
        footer={
          <Button
            onClick={async () => {
              if (!acceptFor) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/payment-proofs/${acceptFor.id}/accept`, acceptForm);
                setAcceptFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not accept the proof"));
              }
            }}
          >
            Accept and record payment
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Accepting records one payment on {acceptFor?.invoiceNumber} for the amount below. The invoice balance updates once. A later accept of the same proof leaves that payment in place.
        </p>
        <Label>Amount</Label>
        <Input aria-label="Amount" value={acceptForm.amount} onChange={(e) => setAcceptForm({ ...acceptForm, amount: e.target.value })} />
        <Label className="mt-3">Method</Label>
        <Select aria-label="Method" value={acceptForm.method} onChange={(e) => setAcceptForm({ ...acceptForm, method: e.target.value })}>
          <option value="eft">EFT</option>
          <option value="cash">Cash</option>
          <option value="card">Card</option>
          <option value="other">Other</option>
        </Select>
        <Label className="mt-3">Note</Label>
        <Input aria-label="Note" value={acceptForm.note} onChange={(e) => setAcceptForm({ ...acceptForm, note: e.target.value })} />
      </Dialog>

      <Dialog
        error={dialogError}
        open={!!rejectFor}
        onClose={() => {
          setRejectFor(null);
          setDialogError(null);
        }}
        title="Reject proof of payment"
        footer={
          <Button
            variant="danger"
            onClick={async () => {
              if (!rejectFor) return;
              try {
                setDialogError(null);
                await apiPost(`/api/v1/payment-proofs/${rejectFor.id}/reject`, { note: rejectNote });
                setRejectFor(null);
                await load();
              } catch (e) {
                setDialogError(message(e, "Could not reject the proof"));
              }
            }}
          >
            Reject proof
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Reject this proof on {rejectFor?.invoiceNumber}. The file stays on the invoice for the audit trail. The invoice balance stays as it was. A note is required.
        </p>
        <Label>Note</Label>
        <Textarea aria-label="Note" value={rejectNote} onChange={(e) => setRejectNote(e.target.value)} placeholder="Why this proof is rejected" />
      </Dialog>
    </div>
  );
}
