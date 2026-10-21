"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader } from "@/components/page";
import { ErrorBanner } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { OperatorOnly } from "@/components/platform";

type Staff = { id: string; email: string; fullName: string; status?: string; totpEnabled: boolean; mustChangePassword?: boolean };
type Invite = { inviteLink?: string; inviteEmailSent?: boolean };

export default function SupportStaffPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Staff[]>([]);
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "" });
  const [issued, setIssued] = useState<Invite | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => {
    apiGet<Staff[]>("/api/v1/platform/support-staff").then(setRows).catch((e) => setError(e instanceof Error ? e.message : "Could not load"));
  }, []);
  useEffect(() => {
    if (user?.role === "superadmin") load();
  }, [user, load]);
  if (user?.role !== "superadmin") return <OperatorOnly title="Support staff" />;
  return (
    <div>
      <PageHeader title="Support staff" subtitle="Owlaw support can work the approval queues only. They must use two-factor sign-in and cannot manage firms, plans or billing or see client data." />
      <ErrorBanner error={error} />
      <div className="mb-6 grid gap-3 rounded border bg-white p-4 sm:grid-cols-4 sm:items-end">
        <div><Label>First name</Label><Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} /></div>
        <div><Label>Last name</Label><Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} /></div>
        <div><Label>Email</Label><Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></div>
        <Button onClick={async () => {
          setError(null);
          try {
            setIssued(await apiPost<Invite>("/api/v1/platform/support-staff", form));
            setForm({ firstName: "", lastName: "", email: "" });
            load();
          } catch (e) { setError(e instanceof Error ? e.message : "Could not invite"); }
        }}>Invite</Button>
      </div>
      {issued && <p className="mb-4 break-all rounded bg-amber-50 p-3 text-sm">{issued.inviteEmailSent ? "Invite emailed." : issued.inviteLink ? `Email could not be sent. Share this set-up link privately: ${issued.inviteLink}` : "Invite created."}</p>}
      <div className="space-y-2">
        {rows.map((s) => (
          <div key={s.id} className="flex flex-col gap-2 rounded border bg-white p-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-medium">{s.fullName}</p>
              <p className="break-all text-sm text-slate-600">{s.email} · {s.status === "inactive" ? "Removed" : s.totpEnabled ? "2FA on" : "2FA not set up yet"}</p>
            </div>
            {s.status !== "inactive" && (
              <Button variant="outline" onClick={async () => {
                if (!confirm(`Remove ${s.fullName}?`)) return;
                try { await apiPost(`/api/v1/platform/support-staff/${s.id}/remove`, {}); load(); } catch (e) { setError(e instanceof Error ? e.message : "Could not remove"); }
              }}>Remove</Button>
            )}
          </div>
        ))}
        {rows.length === 0 && <p className="text-sm text-slate-600">No support staff yet.</p>}
      </div>
    </div>
  );
}
