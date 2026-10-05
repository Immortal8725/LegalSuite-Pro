"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader } from "@/components/page";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorBanner } from "@/components/ui/dialog";
import type { ModuleCard } from "@/lib/types";
import { cn, money } from "@/lib/utils";

export default function ModulesPage() {
  const { tenant, reloadMe } = useAuth();
  const [catalog, setCatalog] = useState<ModuleCard[]>([]);
  const [error, setError] = useState<string | null>(null);
  const enabled = new Set(tenant?.enabledModules ?? []);

  useEffect(() => {
    apiGet<ModuleCard[]>("/api/v1/modules").then(setCatalog).catch((e) => setError(e.message));
  }, []);

  async function toggle(mod: ModuleCard) {
    if (mod.core || !tenant) return;
    setError(null);
    try {
      await apiPost(`/api/v1/tenants/${tenant.id}/modules`, {
        moduleId: mod.id,
        enabled: !enabled.has(mod.slug),
      });
      await reloadMe();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update modules.");
    }
  }

  const addOnCost = catalog
    .filter((m) => !m.core && enabled.has(m.slug))
    .reduce((s, m) => s + Number(m.priceMonthly ?? 0), 0);

  return (
    <div>
      <PageHeader
        title="Manage modules"
        subtitle={`Light includes the practice desk and section 86 trust. Add-ons outside the seat this month: ${money(addOnCost)}. Public-network minutes are pay-what-you-use and are not a module price.`}
      />
      <ErrorBanner error={error} />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {catalog.map((m) => {
          const on = enabled.has(m.slug);
          return (
            <Card key={m.id} className={cn(on && "border border-emerald-400")}>
              <CardHeader>
                <CardTitle>
                  {m.icon} {m.name}
                </CardTitle>
              </CardHeader>
              <CardBody>
                <p className="mb-3 text-sm text-slate-500">{m.description}</p>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">{m.core ? "Included" : `${money(m.priceMonthly)}/mo`}</span>
                  <Button size="sm" variant={on ? "default" : "outline"} disabled={m.core} onClick={() => toggle(m)}>
                    {m.core ? "Always on" : on ? "Enabled" : "Add"}
                  </Button>
                </div>
              </CardBody>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
