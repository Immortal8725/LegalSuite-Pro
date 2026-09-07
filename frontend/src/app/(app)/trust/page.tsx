"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import type { TrustAcct, TrustTx } from "@/lib/types";

export default function TrustPage() {
  const [accounts, setAccounts] = useState<TrustAcct[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [ledger, setLedger] = useState<TrustTx[]>([]);
  const [open, setOpen] = useState(false);
  const [move, setMove] = useState({ type: "deposit", amount: "5000", description: "Retainer deposit" });

  const load = async () => {
    const list = await apiGet<TrustAcct[]>("/api/v1/trust/accounts");
    setAccounts(list);
    const id = selected || list[0]?.id;
    if (id) {
      setSelected(id);
      setLedger(await apiGet<TrustTx[]>(`/api/v1/trust/accounts/${id}/ledger`));
    }
  };
  useEffect(() => {
    load();
  }, []);

  const acct = accounts.find((a) => a.id === selected);

  return (
    <div>
      <PageHeader
        title="Trust / IOLTA"
        subtitle="Client funds never mix with operating. Withdrawals refuse if the ledger would go negative."
        actions={<Button onClick={() => setOpen(true)}>Record movement</Button>}
      />
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        {accounts.map((a) => (
          <button key={a.id} onClick={() => { setSelected(a.id); apiGet<TrustTx[]>(`/api/v1/trust/accounts/${a.id}/ledger`).then(setLedger); }} className="text-left">
            <Card className={a.id === selected ? "ring-2 ring-gold" : ""}>
              <CardHeader>
                <CardTitle>{a.accountName}</CardTitle>
              </CardHeader>
              <CardBody>
                <p className="text-2xl font-extrabold text-navy">{moneyExact(a.balance)}</p>
                <p className="text-xs text-slate-500">{a.bankName}</p>
              </CardBody>
            </Card>
          </button>
        ))}
      </div>
      <TableWrap>
        <thead>
          <tr>
            <Th>When</Th>
            <Th>Type</Th>
            <Th>Amount</Th>
            <Th>Balance after</Th>
            <Th>Memo</Th>
          </tr>
        </thead>
        <tbody>
          {ledger.map((t) => (
            <tr key={t.id}>
              <Td>{t.createdAt}</Td>
              <Td>{t.type}</Td>
              <Td>{moneyExact(t.amount)}</Td>
              <Td>{moneyExact(t.balanceAfter)}</Td>
              <Td>{t.description}</Td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Trust movement"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/trust/move", { ...move, accountId: acct?.id });
              setOpen(false);
              load();
            }}
          >
            Post
          </Button>
        }
      >
        <Label>Type</Label>
        <Select value={move.type} onChange={(e) => setMove({ ...move, type: e.target.value })}>
          <option value="deposit">Deposit</option>
          <option value="withdrawal">Withdrawal</option>
        </Select>
        <Label className="mt-3">Amount</Label>
        <Input type="number" value={move.amount} onChange={(e) => setMove({ ...move, amount: e.target.value })} />
        <Label className="mt-3">Memo</Label>
        <Input value={move.description} onChange={(e) => setMove({ ...move, description: e.target.value })} />
      </Dialog>
    </div>
  );
}
