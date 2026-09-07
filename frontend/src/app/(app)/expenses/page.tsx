"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Select } from "@/components/ui/input";
import type { Expense } from "@/lib/types";

export default function ExpensesPage() {
  const [rows, setRows] = useState<Expense[]>([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ category: "filing fees", description: "", amount: "", vendor: "" });
  const load = () => apiGet<Expense[]>("/api/v1/expenses").then(setRows);
  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Expenses" subtitle="Court costs and expert fees waiting to hit the next invoice." actions={<Button onClick={() => setOpen(true)}>Log expense</Button>} />
      <TableWrap>
        <thead>
          <tr>
            <Th>Date</Th>
            <Th>Category</Th>
            <Th>Vendor</Th>
            <Th>Description</Th>
            <Th>Amount</Th>
            <Th>Status</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((e) => (
            <tr key={e.id}>
              <Td>{e.date}</Td>
              <Td>{e.category}</Td>
              <Td>{e.vendor}</Td>
              <Td>{e.description}</Td>
              <Td>{moneyExact(e.amount)}</Td>
              <Td>{e.status}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Expense"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/expenses", form);
              setOpen(false);
              load();
            }}
          >
            Save
          </Button>
        }
      >
        <Select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
          <option>filing fees</option>
          <option>travel</option>
          <option>expert fees</option>
          <option>postage</option>
          <option>other</option>
        </Select>
        <Input className="mt-3" placeholder="Vendor" value={form.vendor} onChange={(e) => setForm({ ...form, vendor: e.target.value })} />
        <Input className="mt-3" placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <Input className="mt-3" type="number" placeholder="Amount" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
      </Dialog>
    </div>
  );
}
