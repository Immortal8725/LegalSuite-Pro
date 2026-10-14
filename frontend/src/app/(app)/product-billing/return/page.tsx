"use client";

import Link from "next/link";
import { PageHeader } from "@/components/page";
import { Card, CardBody } from "@/components/ui/card";

export default function PayFastReturnPage() {
  return (
    <div>
      <PageHeader
        title="Back from PayFast"
        subtitle="This page only means the browser returned. The seat turns active after the ITN is checked."
      />
      <Card>
        <CardBody>
          <p className="text-sm text-slate-600">
            Open product billing and refresh the status. A complete payment stores the PayFast payment id and, when PayFast sends one, the card token used for later minute charges.
          </p>
          <Link href="/product-billing" className="mt-4 inline-block text-sm font-semibold text-navy underline">
            Product billing
          </Link>
        </CardBody>
      </Card>
    </div>
  );
}
