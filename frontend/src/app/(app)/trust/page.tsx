"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { moneyExact } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { Button, PageHeader, TableWrap, Td, Th } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Label, Select, Textarea } from "@/components/ui/input";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import type { TrustAcct, TrustRecon, TrustTx } from "@/lib/types";

export default function TrustPage() {
  const { tenant } = useAuth();
  const za = tenant?.country === "ZA" || tenant?.jurisdiction === "ZA";
  const [accounts, setAccounts] = useState<TrustAcct[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [ledger, setLedger] = useState<TrustTx[]>([]);
  const [recon, setRecon] = useState<TrustRecon | null>(null);
  const [open, setOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const [move, setMove] = useState({ type: "deposit", amount: "5000", description: "Retainer deposit" });
  const [bank, setBank] = useState({ bankBalance: "", notes: "" });
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    const list = await apiGet<TrustAcct[]>("/api/v1/trust/accounts");
    setAccounts(list);
    const id = selected || list[0]?.id;
    if (id) {
      setSelected(id);
      setLedger(await apiGet<TrustTx[]>(`/api/v1/trust/accounts/${id}/ledger`));
    }
    setRecon(await apiGet<TrustRecon>("/api/v1/trust/recon"));
  };
  useEffect(() => {
    load().catch((e) => setError(e.message));
  }, []);

  const acct = accounts.find((a) => a.id === selected);
  const live = acct?.recon;
  const unbalanced = live?.status === "unbalanced";

  return (
    <div>
      <PageHeader
        title={za ? "Section 86 trust" : "Trust / IOLTA"}
        subtitle={
          za
            ? "Legal Practice Act ss 86–87. Bank statement, cashbook, and client ledgers must agree. You cannot spend another client's money."
            : "Client funds never mix with operating. Withdrawals refuse if this client's ledger would go negative."
        }
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setBankOpen(true)}>
              Enter bank balance
            </Button>
            <Button onClick={() => setOpen(true)}>Record movement</Button>
          </div>
        }
      />
      {error && <p className="mb-4 text-sm text-red-700">{error}</p>}
      {live && (
        <Card className={`mb-6 ${unbalanced ? "border-red-300 bg-red-50" : "border-emerald-200 bg-emerald-50"}`}>
          <CardHeader>
            <CardTitle>{unbalanced ? "Three-way unbalanced — do not certify" : "Three-way balanced"}</CardTitle>
          </CardHeader>
          <CardBody className="grid gap-4 sm:grid-cols-3 text-sm">
            <div>
              <p className="text-xs text-slate-500">Bank statement</p>
              <p className="text-xl font-extrabold">{moneyExact(live.bankBalance)}</p>
            </div>
            <div>
              <p className="text-xs text-slate-500">Cashbook</p>
              <p className="text-xl font-extrabold">{moneyExact(live.bookBalance)}</p>
            </div>
            <div>
              <p className="text-xs text-slate-500">Client ledgers</p>
              <p className="text-xl font-extrabold">{moneyExact(live.clientLedgerTotal)}</p>
            </div>
            <p className="sm:col-span-3 text-xs text-slate-600">{live.rule}</p>
            {unbalanced && (
              <p className="sm:col-span-3 font-semibold text-red-900">
                Difference {moneyExact(live.difference)}. Find the missing deposit or the unrecorded transfer before an
                inspector does.
              </p>
            )}
          </CardBody>
        </Card>
      )}
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        {accounts.map((a) => (
          <button
            key={a.id}
            onClick={() => {
              setSelected(a.id);
              apiGet<TrustTx[]>(`/api/v1/trust/accounts/${a.id}/ledger`).then(setLedger);
            }}
            className="text-left"
          >
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
      {live?.ledgers && live.ledgers.length > 0 && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>Client ledgers</CardTitle>
          </CardHeader>
          <CardBody className="space-y-2 text-sm">
            {live.ledgers.map((row, i) => (
              <div key={i} className="flex justify-between">
                <span className="font-mono text-xs">{row.unallocated ? "Unallocated" : row.clientId}</span>
                <span className="font-semibold">{moneyExact(row.balance)}</span>
              </div>
            ))}
          </CardBody>
        </Card>
      )}
      {recon?.history && recon.history.length > 0 && (
        <p className="mb-3 text-xs text-slate-500">
          Last saved recon: {recon.history[0].status}
          {recon.history[0].certified ? " · certified" : " · not certified"} · difference{" "}
          {moneyExact(recon.history[0].difference)}
        </p>
      )}
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
      <Dialog
        open={bankOpen}
        onClose={() => setBankOpen(false)}
        title="Three-way recon"
        footer={
          <Button
            onClick={async () => {
              try {
                setError(null);
                await apiPost("/api/v1/trust/reconcile", {
                  accountId: acct?.id,
                  bankBalance: bank.bankBalance || live?.bankBalance,
                  notes: bank.notes,
                  certify: false,
                });
                setBankOpen(false);
                load();
              } catch (e) {
                setError(e instanceof Error ? e.message : "Could not save recon");
              }
            }}
          >
            Save recon
          </Button>
        }
      >
        <p className="mb-3 text-sm text-slate-600">
          Type the closing balance from this month&apos;s bank statement. Certification is refused while the three legs
          disagree unless you write a full explanation.
        </p>
        <Label>Bank statement balance</Label>
        <Input
          type="number"
          value={bank.bankBalance}
          onChange={(e) => setBank({ ...bank, bankBalance: e.target.value })}
          placeholder={live ? String(live.bankBalance) : ""}
        />
        <Label className="mt-3">Explanation</Label>
        <Textarea value={bank.notes} onChange={(e) => setBank({ ...bank, notes: e.target.value })} />
      </Dialog>
    </div>
  );
}
