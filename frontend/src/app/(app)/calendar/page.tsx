"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { Button, PageHeader } from "@/components/page";
import { Dialog, ErrorBanner } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import { Card, CardBody } from "@/components/ui/card";
import type { CalEvent } from "@/lib/types";

export default function CalendarPage() {
  const [events, setEvents] = useState<CalEvent[]>([]);
  const [open, setOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const openDialog = () => {
    setDialogError(null);
    setOpen(true);
  };
  const [form, setForm] = useState({ title: "", type: "hearing", location: "", startTime: "" });
  const [error, setError] = useState<string | null>(null);
  const load = () =>
    apiGet<CalEvent[]>("/api/v1/events")
      .then((r) => {
        setEvents(r);
        setError(null);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load the calendar"));
  useEffect(() => {
    load();
  }, []);

  const byDay = useMemo(() => {
    const map = new Map<string, CalEvent[]>();
    for (const e of events) {
      const day = e.startTime?.slice(0, 10) || "undated";
      map.set(day, [...(map.get(day) || []), e]);
    }
    return [...map.entries()].sort();
  }, [events]);

  return (
    <div>
      <PageHeader title="Calendar" subtitle="Court dates, depositions, and filing clocks. Color follows the event type." actions={<Button onClick={openDialog}>New event</Button>} />
      <ErrorBanner error={error} />
      <div className="space-y-4">
        {byDay.length === 0 && <p className="text-sm text-slate-600">No events in the next 40 days.</p>}
        {byDay.map(([day, list]) => (
          <Card key={day}>
            <CardBody>
              <p className="mb-3 text-xs font-bold uppercase text-slate-600">{day}</p>
              <div className="space-y-3">
                {list.map((e) => (
                  <div key={e.id} className="flex flex-wrap justify-between gap-2 border-l-4 border-gold pl-3">
                    <div>
                      <p className="font-semibold">{e.title}</p>
                      <p className="text-xs text-slate-600">
                        {e.type} · {e.location || "TBD"}
                      </p>
                    </div>
                    <p className="text-sm text-slate-600">{formatDateTime(e.startTime)}</p>
                  </div>
                ))}
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Calendar event"
        footer={
          <Button
            disabled={saving}
            onClick={async () => {
              setSaving(true);
              setDialogError(null);
              try {
                await apiPost("/api/v1/events", {
                  ...form,
                  startTime: new Date(form.startTime).toISOString(),
                });
                setOpen(false);
                load();
              } catch (err) {
                setDialogError(err instanceof Error ? err.message : "Could not save the event");
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Saving…" : "Save"}
          </Button>
        }
      >
        <ErrorBanner error={dialogError} />
        <div className="space-y-3">
          <Label>Title</Label>
          <Input aria-label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="hearing">Hearing</option>
            <option value="deadline">Deadline</option>
            <option value="deposition">Deposition</option>
            <option value="meeting">Meeting</option>
            <option value="consultation">Consultation</option>
          </Select>
          <Input type="datetime-local" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
          <Input placeholder="Location" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
        </div>
      </Dialog>
    </div>
  );
}
