"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { apiGet, apiPost, getToken } from "@/lib/api";

type TimerState = {
  running: boolean;
  startedAt?: string;
  elapsedSeconds?: number;
  caseId?: string | null;
};

type TimerCtx = {
  timer: TimerState;
  start: (caseId?: string) => Promise<void>;
  stop: (description?: string) => Promise<void>;
  label: string;
};

const Ctx = createContext<TimerCtx | null>(null);

export function TimerProvider({ children }: { children: React.ReactNode }) {
  const [timer, setTimer] = useState<TimerState>({ running: false });
  const [tick, setTick] = useState(0);

  const refresh = useCallback(async () => {
    if (!getToken()) return;
    try {
      const data = await apiGet<TimerState>("/api/v1/timers/active");
      setTimer(data);
    } catch {
      /* not signed in yet */
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!timer.running) return;
    const id = setInterval(() => setTick((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [timer.running]);

  const start = useCallback(async (caseId?: string) => {
    const data = await apiPost<TimerState>("/api/v1/timers/start", caseId ? { caseId } : {});
    setTimer(data);
  }, []);

  const stop = useCallback(async (description?: string) => {
    await apiPost("/api/v1/timers/stop", { description: description || "Timed work" });
    setTimer({ running: false });
  }, []);

  const elapsed = timer.running
    ? (timer.elapsedSeconds || 0) + (timer.startedAt ? Math.max(0, Math.floor((Date.now() - new Date(timer.startedAt).getTime()) / 1000) - (timer.elapsedSeconds || 0)) : tick)
    : 0;
  const mins = Math.floor(elapsed / 60);
  const secs = elapsed % 60;
  const label = timer.running ? `${mins}:${String(secs).padStart(2, "0")}` : "Timer";

  const value = useMemo(() => ({ timer, start, stop, label }), [timer, start, stop, label]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTimer() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useTimer requires TimerProvider");
  return ctx;
}
