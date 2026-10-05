"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { Button, PageHeader } from "@/components/page";
import { Dialog } from "@/components/ui/dialog";
import { Input, Label, Select } from "@/components/ui/input";
import { Card, CardBody } from "@/components/ui/card";
import type { CalEvent } from "@/lib/types";

export default function CalendarPage() {
  const [events, setEvents] = useState<CalEvent[]>([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", type: "hearing", location: "", startTime: "" });
  const load = () => apiGet<CalEvent[]>("/api/v1/events").then(setEvents);
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
      <PageHeader title="Calendar" subtitle="Court dates, depositions, and filing clocks. Color follows the event type." actions={<Button onClick={() => setOpen(true)}>New event</Button>} />
      <div className="space-y-4">
        {byDay.length === 0 && <p className="text-sm text-slate-500">No events in the next 40 days.</p>}
        {byDay.map(([day, list]) => (
          <Card key={day}>
            <CardBody>
              <p className="mb-3 text-xs font-bold uppercase text-slate-400">{day}</p>
              <div className="space-y-3">
                {list.map((e) => (
                  <div key={e.id} className="flex flex-wrap justify-between gap-2 border-l-4 border-gold pl-3">
                    <div>
                      <p className="font-semibold">{e.title}</p>
                      <p className="text-xs text-slate-500">
                        {e.type} · {e.location || "TBD"}
                      </p>
                    </div>
                    <p className="text-sm text-slate-500">{formatDateTime(e.startTime)}</p>
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
            onClick={async () => {
              await apiPost("/api/v1/events", {
                ...form,
                startTime: new Date(form.startTime).toISOString(),
              });
              setOpen(false);
              load();
            }}
          >
            Save
          </Button>
        }
      >
        <div className="space-y-3">
          <Label>Title</Label>
          <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
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
