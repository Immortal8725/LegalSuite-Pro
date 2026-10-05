"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { moneyExact } from "@/lib/utils";

type Line = { kind: string; description: string; amount: number };
type Preview = {
  period: string;
  seatSubtotal?: number;
  modulesSubtotal: number;
  pstnSubtotal: number;
  pstnMinutes: number;
  didMonthly?: number;
  messagingSubtotal?: number;
  smsCount?: number;
  whatsappCount?: number;
  total: number;
  lineItems: Line[];
  note: string;
  issued?: { id: string; status: string; invoiceNumber: string; total: number } | null;
};
type Issued = {
  id: string;
  period: string;
  invoiceNumber: string;
  status: string;
  total: number;
  modulesSubtotal: number;
  pstnSubtotal: number;
};

export default function UsagePage() {
  const [preview, setPreview] = useState<Preview | null>(null);
  const [history, setHistory] = useState<Issued[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([apiGet<Preview>("/api/v1/usage"), apiGet<Issued[]>("/api/v1/usage/history")])
      .then(([p, h]) => {
        setPreview(p);
        setHistory(h);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader
        title="Month-end usage"
        subtitle="Light is the monthly seat and includes section 86 trust. Public-network minutes, SMS, and WhatsApp are pay-what-you-use. There is no minute bundle. Email through the firm's SMTP server is not metered."
      />
      <ErrorBanner error={error} />
      {preview && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>This period · {preview.period}</CardTitle>
            {preview.issued ? (
              <StatusBadge status={preview.issued.status} />
            ) : (
              <Button
                onClick={async () => {
                  await apiPost("/api/v1/usage/issue", {});
                  load();
                }}
              >
                Issue month-end invoice
              </Button>
            )}
          </CardHeader>
          <CardBody>
            <p className="mb-4 text-sm text-slate-500">{preview.note}</p>
            <div className="mb-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
              <div>
                <p className="text-xs text-slate-400">Light seat</p>
                <p className="text-xl font-extrabold text-navy">{moneyExact(preview.seatSubtotal ?? 0)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Add-ons outside the seat</p>
                <p className="text-xl font-extrabold text-navy">{moneyExact(preview.modulesSubtotal)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Phone ({preview.pstnMinutes} min, no bundle)</p>
                <p className="text-xl font-extrabold text-navy">{moneyExact(preview.pstnSubtotal)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">SMS and WhatsApp ({(preview.smsCount || 0) + (preview.whatsappCount || 0)})</p>
                <p className="text-xl font-extrabold text-navy">{moneyExact(preview.messagingSubtotal || 0)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Due</p>
                <p className="text-xl font-extrabold text-gold">{moneyExact(preview.total)}</p>
              </div>
            </div>
            <TableWrap>
              <thead>
                <tr>
                  <Th>Line</Th>
                  <Th>Amount</Th>
                </tr>
              </thead>
              <tbody>
                {(preview.lineItems || []).map((l, i) => (
                  <tr key={i}>
                    <Td>{l.description}</Td>
                    <Td>{moneyExact(l.amount)}</Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          </CardBody>
        </Card>
      )}
      <Card>
        <CardHeader>
          <CardTitle>Issued usage invoices</CardTitle>
        </CardHeader>
        <CardBody className="p-0">
          {history.length === 0 ? (
            <p className="p-5 text-sm text-slate-500">None issued yet. The preview is the Light seat plus public-network minutes used this month. A local number is about R79 if you take one, or bundled if the operator includes it.</p>
          ) : (
            <TableWrap>
              <thead>
                <tr>
                  <Th>#</Th>
                  <Th>Period</Th>
                  <Th>Total</Th>
                  <Th>Status</Th>
                  <Th></Th>
                </tr>
              </thead>
              <tbody>
                {history.map((inv) => (
                  <tr key={inv.id}>
                    <Td className="font-mono">{inv.invoiceNumber}</Td>
                    <Td>{inv.period}</Td>
                    <Td>{moneyExact(inv.total)}</Td>
                    <Td>
                      <StatusBadge status={inv.status} />
                    </Td>
                    <Td>
                      {inv.status !== "paid" && (
                        <button
                          className="text-sm font-semibold text-navy"
                          onClick={async () => {
                            await apiPost(`/api/v1/usage/${inv.id}/pay`, {});
                            load();
                          }}
                        >
                          Mark paid
                        </button>
                      )}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
