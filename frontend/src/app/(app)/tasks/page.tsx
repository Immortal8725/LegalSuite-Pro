"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost } from "@/lib/api";
import { Button, PageHeader, StatusBadge } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Textarea, Select } from "@/components/ui/input";
import type { Task } from "@/lib/types";

const COLS = ["todo", "in_progress", "in_review", "blocked", "completed"];

export default function TasksPage() {
  const [rows, setRows] = useState<Task[]>([]);
  const [open, setOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const openDialog = () => {
    setDialogError(null);
    setOpen(true);
  };
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const load = () =>
    apiGet<Task[]>("/api/v1/tasks")
      .then((r) => {
        setRows(r);
        setError(null);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load tasks"));
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
      <PageHeader title="Tasks" subtitle="Move a card by changing its status." actions={<Button onClick={openDialog}>Add task</Button>} />
      <ErrorBanner error={error} />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
        {COLS.map((col) => (
          <div key={col} className="rounded-xl bg-slate-100 p-3">
            <p className="mb-3 text-xs font-bold uppercase text-slate-600">
              {col.replaceAll("_", " ")} · {grouped[col].length}
            </p>
            {grouped[col].map((t) => (
              <div key={t.id} className="mb-2 rounded-lg bg-white p-3 shadow-card">
                <p className="text-sm font-semibold">{t.title}</p>
                <p className="mt-1 text-xs text-slate-600">{t.description}</p>
                <div className="mt-2 flex items-center justify-between">
                  <StatusBadge status={t.priority} />
                  <Select
                    className="h-8 w-auto text-xs"
                    aria-label={`Status of ${t.title}`}
                    value={t.status}
                    onChange={async (e) => {
                      setError(null);
                      try {
                        await apiPatch(`/api/v1/tasks/${t.id}/status`, { status: e.target.value });
                        await load();
                      } catch (err) {
                        setError(err instanceof Error ? err.message : "Could not move the task");
                      }
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
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                await apiPost("/api/v1/tasks", { title, description, status: "todo" });
                setOpen(false);
                setTitle("");
                load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not create the task");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Saving…" : "Create"}
          </Button>
        }
      >
        <ErrorBanner error={dialogError} />
        <Input placeholder="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
        <Textarea className="mt-3" placeholder="Checklist notes" value={description} onChange={(e) => setDescription(e.target.value)} />
      </Dialog>
    </div>
  );
}
