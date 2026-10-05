"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";

type Field = { name: string; value: string };
type Checkout = {
  action: string;
  amountCents: number;
  amount: string;
  subscriptionType: string;
  frequency?: string | null;
  mPaymentId: string;
  fields: Field[];
};
type Payment = {
  id: string;
  kind: string;
  mPaymentId?: string;
  pfPaymentId?: string;
  amountCents: number;
  amount: string;
  status: string;
  period?: string;
  createdAt?: string;
};
type Minutes = {
  id?: string;
  period: string;
  minutes: number;
  amountCents: number;
  amount: string;
  status: string;
  mPaymentId?: string;
  pfPaymentId?: string;
  note?: string;
};
type Status = {
  environment: string;
  merchantConfigured: boolean;
  checkoutReady: boolean;
  missing: string[];
  passphraseSet: boolean;
  canManage: boolean;
  seatPrice: string;
  seatCount: number;
  seatStatus: string;
  seatActiveUntil?: string | null;
  lastPaymentAt?: string | null;
  lastAmount?: string | null;
  lastPfPaymentId?: string | null;
  lastMPaymentId?: string | null;
  tokenPresent: boolean;
  tokenHint?: string | null;
  payments: Payment[];
  minutes: Minutes[];
  note: string;
};

function currentPeriod() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function postToPayFast(checkout: Checkout) {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = checkout.action;
  for (const field of checkout.fields) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = field.name;
    input.value = field.value;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}

export default function ProductBillingPage() {
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [seats, setSeats] = useState("1");
  const [period, setPeriod] = useState(currentPeriod);
  const [busy, setBusy] = useState<string | null>(null);

  const load = () =>
    apiGet<Status>("/api/v1/product-billing")
      .then(setStatus)
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  async function start(path: string, body: unknown, key: string) {
    setError(null);
    setBusy(key);
    try {
      const checkout = await apiPost<Checkout>(path, body);
      postToPayFast(checkout);
    } catch (e) {
      setError(e instanceof Error ? e.message : "PayFast checkout did not start");
      setBusy(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Product billing"
        subtitle="PayFast collects the LegalSuite Light seat and public-network minutes. Client fee invoices and trust receipts are not charged here."
      />
      <ErrorBanner error={error} />
      {status && (
        <>
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Light seat</CardTitle>
              <StatusBadge status={status.seatStatus} />
            </CardHeader>
            <CardBody>
              <p className="mb-4 text-sm text-slate-500">{status.note}</p>
              <div className="mb-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div>
                  <p className="text-xs text-slate-400">Seat price</p>
                  <p className="text-xl font-extrabold text-navy">{moneyExact(status.seatPrice, "ZAR")} / month</p>
                </div>
                <div>
                  <p className="text-xs text-slate-400">Seats on the account</p>
                  <p className="text-xl font-extrabold text-navy">{status.seatCount}</p>
                </div>
                <div>
                  <p className="text-xs text-slate-400">Last payment</p>
                  <p className="text-xl font-extrabold text-navy">
                    {status.lastAmount ? moneyExact(status.lastAmount, "ZAR") : "None"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-slate-400">Card token</p>
                  <p className="text-xl font-extrabold text-navy">
                    {status.tokenPresent ? `On file, ending ${status.tokenHint || ""}` : "Not saved"}
                  </p>
                </div>
              </div>
              <p className="mb-4 text-xs text-slate-500">
                Environment: {status.environment}. PayFast merchant {status.merchantConfigured ? "is set" : "is not set"}.
                Passphrase {status.passphraseSet ? "is set" : "is blank"}. A blank passphrase is valid only when the PayFast merchant has none.
                {status.lastPfPaymentId ? ` Last PayFast id ${status.lastPfPaymentId}.` : ""}
                {status.seatActiveUntil ? ` Seat active until ${status.seatActiveUntil}.` : ""}
              </p>
              {!status.checkoutReady && (
                <p className="mb-4 text-sm text-amber-800">
                  Checkout needs {status.missing.join(", ")} on the server. Use sandbox until the live merchant is approved.
                </p>
              )}
              {status.canManage ? (
                <div className="flex flex-wrap items-end gap-3">
                  <div>
                    <Label>Seats</Label>
                    <Input
                      type="number"
                      min={1}
                      max={100}
                      value={seats}
                      onChange={(e) => setSeats(e.target.value)}
                      className="w-24"
                    />
                  </div>
                  <Button
                    disabled={!status.checkoutReady || busy !== null}
                    onClick={() => start("/api/v1/product-billing/seats/checkout", { seats: Number(seats) || 1 }, "seat")}
                  >
                    {busy === "seat" ? "Opening PayFast…" : "Subscribe with PayFast"}
                  </Button>
                  <Button
                    variant="outline"
                    disabled={!status.checkoutReady || busy !== null}
                    onClick={() => start("/api/v1/product-billing/token/checkout", {}, "token")}
                  >
                    {busy === "token" ? "Opening PayFast…" : "Save a card token"}
                  </Button>
                </div>
              ) : (
                <p className="text-sm text-slate-500">A firm owner or director starts the subscription. You can still read this status.</p>
              )}
            </CardBody>
          </Card>

          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Phone minutes</CardTitle>
            </CardHeader>
            <CardBody>
              <p className="mb-4 text-sm text-slate-500">
                Minutes are pay-what-you-use and are charged with the saved PayFast token. This does not include the seat.
                The pilot trigger is manual.
              </p>
              {status.canManage && (
                <div className="mb-4 flex flex-wrap items-end gap-3">
                  <div>
                    <Label>Period</Label>
                    <Input type="month" value={period} onChange={(e) => setPeriod(e.target.value)} className="w-44" />
                  </div>
                  <Button
                    variant="outline"
                    disabled={busy !== null}
                    onClick={async () => {
                      setError(null);
                      setBusy("minutes");
                      try {
                        await apiPost("/api/v1/product-billing/minutes/charge", { period });
                        await load();
                      } catch (e) {
                        setError(e instanceof Error ? e.message : "Minute charge failed");
                      } finally {
                        setBusy(null);
                      }
                    }}
                  >
                    {busy === "minutes" ? "Charging…" : "Invoice minutes and charge"}
                  </Button>
                </div>
              )}
              {status.minutes.length === 0 ? (
                <p className="text-sm text-slate-500">No minute invoices yet.</p>
              ) : (
                <TableWrap>
                  <thead>
                    <tr>
                      <Th>Period</Th>
                      <Th>Minutes</Th>
                      <Th>Amount</Th>
                      <Th>Status</Th>
                      <Th>PayFast id</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {status.minutes.map((row) => (
                      <tr key={row.id || row.period}>
                        <Td>{row.period}</Td>
                        <Td>{row.minutes}</Td>
                        <Td>{moneyExact(row.amount, "ZAR")}</Td>
                        <Td>
                          <StatusBadge status={row.status} />
                        </Td>
                        <Td className="font-mono text-xs">{row.pfPaymentId || row.note || ""}</Td>
                      </tr>
                    ))}
                  </tbody>
                </TableWrap>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>PayFast payments</CardTitle>
            </CardHeader>
            <CardBody className="p-0">
              {status.payments.length === 0 ? (
                <p className="p-5 text-sm text-slate-500">No product payments yet. A return from PayFast is not itself proof of payment. The seat turns active after the ITN is checked.</p>
              ) : (
                <TableWrap>
                  <thead>
                    <tr>
                      <Th>Kind</Th>
                      <Th>Merchant id</Th>
                      <Th>PayFast id</Th>
                      <Th>Amount</Th>
                      <Th>Status</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {status.payments.map((row) => (
                      <tr key={row.id}>
                        <Td>{row.kind}</Td>
                        <Td className="font-mono text-xs">{row.mPaymentId}</Td>
                        <Td className="font-mono text-xs">{row.pfPaymentId}</Td>
                        <Td>{moneyExact(row.amount, "ZAR")}</Td>
                        <Td>
                          <StatusBadge status={row.status} />
                        </Td>
                      </tr>
                    ))}
                  </tbody>
                </TableWrap>
              )}
            </CardBody>
          </Card>
        </>
      )}
    </div>
  );
}
