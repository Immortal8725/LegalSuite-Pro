"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page";
import { Badge, Card, CardBody } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import { OperatorOnly, rand } from "@/components/platform";

type Firm = {
  id: string;
  name: string;
  status: string;
  plan: string;
  attorneySeats: number;
  staffSeats: number;
  mrr: number;
  listValue: number;
  unpaidCount: number;
  unpaidTotal: number;
  callMinutesThisMonth: number;
  callChargesThisMonth: number;
};
type Overview = {
  totalMrr: number;
  totalUnpaid: number;
  payingFirms: number;
  firms: Firm[];
};

export default function PlatformBillingPage() {
  const { user } = useAuth();
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user?.role !== "superadmin") return;
    apiGet<Overview>("/api/v1/platform/billing")
      .then(setData)
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Could not load billing."),
      );
  }, [user]);

  if (user?.role !== "superadmin")
    return <OperatorOnly title="Billing overview" />;

  return (
    <div>
      <PageHeader
        title="Billing overview"
        subtitle="Monthly recurring revenue in rand, excluding VAT, from active firms only (seat price times active attorneys, plus staff seats at R349). Trial firms show their value once converted."
      />
      <ErrorBanner error={error} />
      {data ? (
        <>
          <div className="mb-4 grid gap-3 sm:grid-cols-3">
            <Card>
              <CardBody>
                <p className="text-xs uppercase text-slate-500">MRR</p>
                <p className="text-2xl font-semibold" data-testid="total-mrr">
                  {rand(data.totalMrr)}
                </p>
              </CardBody>
            </Card>
            <Card>
              <CardBody>
                <p className="text-xs uppercase text-slate-500">Paying firms</p>
                <p className="text-2xl font-semibold">{data.payingFirms}</p>
              </CardBody>
            </Card>
            <Card>
              <CardBody>
                <p className="text-xs uppercase text-slate-500">
                  Unpaid invoices
                </p>
                <p className="text-2xl font-semibold">
                  {rand(data.totalUnpaid)}
                </p>
              </CardBody>
            </Card>
          </div>
          <Card>
            <CardBody className="overflow-x-auto p-0">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th className="p-3">Firm</th>
                    <th className="p-3">Plan</th>
                    <th className="p-3">Seats</th>
                    <th className="p-3">MRR</th>
                    <th className="p-3">Unpaid</th>
                    <th className="p-3">Calls this month</th>
                  </tr>
                </thead>
                <tbody>
                  {data.firms.map((f) => (
                    <tr
                      key={f.id}
                      className="border-t"
                      data-testid="billing-row"
                    >
                      <td className="p-3">
                        <div className="font-medium">{f.name}</div>
                        <Badge
                          tone={
                            f.status === "active"
                              ? "green"
                              : f.status === "suspended"
                                ? "red"
                                : "slate"
                          }
                        >
                          {f.status}
                        </Badge>
                      </td>
                      <td className="p-3">{f.plan}</td>
                      <td className="p-3">
                        {f.attorneySeats} attorney
                        {f.attorneySeats === 1 ? "" : "s"}, {f.staffSeats} staff
                      </td>
                      <td className="p-3">
                        {rand(f.mrr)}
                        {f.mrr === 0 && f.listValue > 0 ? (
                          <div className="text-xs text-slate-500">
                            {rand(f.listValue)} when active
                          </div>
                        ) : null}
                      </td>
                      <td className="p-3">
                        {f.unpaidCount
                          ? `${f.unpaidCount} (${rand(f.unpaidTotal)})`
                          : "None"}
                      </td>
                      <td className="p-3">
                        {f.callMinutesThisMonth} min,{" "}
                        {rand(f.callChargesThisMonth)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardBody>
          </Card>
        </>
      ) : null}
    </div>
  );
}
