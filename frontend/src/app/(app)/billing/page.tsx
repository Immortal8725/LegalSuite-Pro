"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, StatusBadge, TableWrap, Td, Th } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Select } from "@/components/ui/input";
import type { Invoice, Party } from "@/lib/types";

export default function BillingPage() {
  const [rows, setRows] = useState<Invoice[]>([]);
  const [clients, setClients] = useState<Party[]>([]);
  const [open, setOpen] = useState(false);
  const [clientId, setClientId] = useState("");
  const load = () => apiGet<Invoice[]>("/api/v1/invoices").then(setRows);
  useEffect(() => {
    load();
    apiGet<Party[]>("/api/v1/clients").then(setClients);
  }, []);

  return (
    <div>
      <PageHeader
        title="Billing & invoices"
        subtitle="Generate a draft from unbilled time and expenses. Clients can pay from the portal."
        actions={<Button onClick={() => setOpen(true)}>Generate from unbilled time and expenses</Button>}
      />
      <TableWrap>
        <thead>
          <tr>
            <Th>Invoice</Th>
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
            <tr key={i.id}>
              <Td className="font-mono font-semibold">{i.invoiceNumber}</Td>
              <Td>{i.dateIssued}</Td>
              <Td>{i.dateDue}</Td>
              <Td>{moneyExact(i.total)}</Td>
              <Td>{moneyExact(i.balanceDue)}</Td>
              <Td>
                <StatusBadge status={i.status} />
              </Td>
              <Td className="space-x-2">
                {i.status !== "paid" && (
                  <>
                    <button className="text-sm font-semibold text-navy" onClick={() => apiPost(`/api/v1/invoices/${i.id}/send`, {}).then(load)}>
                      Send
                    </button>
                    <button className="text-sm font-semibold text-emerald-700" onClick={() => apiPost(`/api/v1/invoices/${i.id}/pay`, {}).then(load)}>
                      Record paid
                    </button>
                  </>
                )}
              </Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Draft invoice"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/invoices/generate", { clientId });
              setOpen(false);
              load();
            }}
          >
            Create draft
          </Button>
        }
      >
        <Select value={clientId} onChange={(e) => setClientId(e.target.value)}>
          <option value="">Select client</option>
          {clients.map((c) => (
            <option key={c.id} value={c.id}>
              {c.displayName}
            </option>
          ))}
        </Select>
      </Dialog>
    </div>
  );
}
