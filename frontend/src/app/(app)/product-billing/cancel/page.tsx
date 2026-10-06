"use client";

import Link from "next/link";
import { PageHeader } from "@/components/page";
import { Card, CardBody } from "@/components/ui/card";

export default function PayFastCancelPage() {
  return (
    <div>
      <PageHeader
        title="Checkout cancelled"
        subtitle="No seat charge was sent from this return."
      />
      <Card>
        <CardBody>
          <p className="text-sm text-slate-600">You can start the Light subscription again from product billing.</p>
          <Link href="/product-billing" className="mt-4 inline-block text-sm font-semibold text-navy underline">
            Product billing
          </Link>
        </CardBody>
      </Card>
    </div>
  );
}
