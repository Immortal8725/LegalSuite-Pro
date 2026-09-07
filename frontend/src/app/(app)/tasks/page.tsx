"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Textarea, Select } from "@/components/ui/input";
import type { Task } from "@/lib/types";

const COLS = ["todo", "in_progress", "in_review", "blocked", "completed"];

export default function TasksPage() {
  const [rows, setRows] = useState<Task[]>([]);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const load = () => apiGet<Task[]>("/api/v1/tasks").then(setRows);
  useEffect(() => {
    load();
  }, []);
  const grouped = useMemo(() => {
    const g: Record<string, Task[]> = {};
    for (const c of COLS) g[c] = rows.filter((t) => t.status === c);
    return g;
  }, [rows]);

  return (
    <div>
      <PageHeader title="Tasks" subtitle="Drag is a click: move a card by changing its status." actions={<Button onClick={() => setOpen(true)}>Add task</Button>} />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {COLS.map((col) => (
          <div key={col} className="rounded-xl bg-slate-100 p-3">
            <p className="mb-3 text-xs font-bold uppercase text-slate-500">
              {col.replaceAll("_", " ")} · {grouped[col].length}
            </p>
            {grouped[col].map((t) => (
              <div key={t.id} className="mb-2 rounded-lg bg-white p-3 shadow-card">
                <p className="text-sm font-semibold">{t.title}</p>
                <p className="mt-1 text-xs text-slate-500">{t.description}</p>
                <div className="mt-2 flex items-center justify-between">
                  <StatusBadge status={t.priority} />
                  <Select
                    className="h-8 w-auto text-xs"
                    value={t.status}
                    onChange={async (e) => {
                      await apiPatch(`/api/v1/tasks/${t.id}/status`, { status: e.target.value });
                      load();
                    }}
                  >
                    {COLS.map((s) => (
                      <option key={s}>{s}</option>
                    ))}
                  </Select>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Task"
        footer={
          <Button
            onClick={async () => {
              await apiPost("/api/v1/tasks", { title, description, status: "todo" });
              setOpen(false);
              setTitle("");
              load();
            }}
          >
            Create
          </Button>
        }
      >
        <Input placeholder="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
        <Textarea className="mt-3" placeholder="Checklist notes" value={description} onChange={(e) => setDescription(e.target.value)} />
      </Dialog>
    </div>
  );
}
