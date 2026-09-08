"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Loading, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/components/auth-provider";

type FitnessItem = {
  id: string;
  done: boolean;
  owner: string;
  title: string;
  why: string;
};

type Fitness = {
  jurisdiction: string;
  title: string;
  score: string;
  done: number;
  total: number;
  next: string;
  items: FitnessItem[];
  trustRecon?: { worstStatus?: string };
};

export default function FitnessPage() {
  const { tenant } = useAuth();
  const [data, setData] = useState<Fitness | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Fitness>("/api/v1/fitness")
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return <Loading />;

  const za = data.jurisdiction === "ZA";

  return (
    <div>
      <PageHeader
        title={data.title}
        subtitle={`${tenant?.firmName} · ${data.score}. Product items are built. The rest is what you still have to do to survive an inspector.`}
      />
      <Card className="mb-6">
        <CardBody>
          <p className="text-sm text-slate-700">{data.next}</p>
          {data.trustRecon?.worstStatus === "unbalanced" && (
            <Link href="/trust" className="mt-3 inline-block text-sm font-semibold text-red-800">
              Open the three-way recon →
            </Link>
          )}
        </CardBody>
      </Card>
      <div className="grid gap-4">
        {data.items.map((item) => (
          <Card key={item.id} className={item.done ? "border-emerald-200" : "border-slate-200"}>
            <CardHeader>
              <CardTitle className="flex flex-wrap items-center gap-2 text-base">
                <span
                  className={`rounded-full px-2 py-0.5 text-[11px] font-bold uppercase ${
                    item.done ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-600"
                  }`}
                >
                  {item.done ? "In product" : item.owner === "you" ? "You" : item.owner}
                </span>
                {item.title}
              </CardTitle>
            </CardHeader>
            <CardBody>
              <p className="text-sm text-slate-600">{item.why}</p>
            </CardBody>
          </Card>
        ))}
      </div>
      <p className="mt-8 text-xs text-slate-400">
        {za
          ? "Clocks cite the Prescription Act, RAF Act, Act 40 of 2002, LRA, and LPA. Confirm interruptions and condonation before you rely on a date."
          : "Texas clocks cite the Civil Practice and Remedies Code. Confirm tolling before you rely on a date."}{" "}
        This page is a fitness score, not legal advice.
      </p>
    </div>
  );
}
