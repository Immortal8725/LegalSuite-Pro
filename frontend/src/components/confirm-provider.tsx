"use client";

import { createContext, useContext, useEffect, useRef, useState } from "react";

type Ask = {
  title: string;
  body: string;
  confirmLabel?: string;
  danger?: boolean;
};

type PendingAsk = Ask & { resolve: (yes: boolean) => void };

type PendingUndo = {
  timer: number;
  commit: () => Promise<void>;
  restore: () => void;
};

type Api = {
  confirm: (ask: Ask) => Promise<boolean>;
  /** Holds a delete for a few seconds. Undo puts the row back. The server is only called if the toast ends. */
  undoable: (label: string, commit: () => Promise<void>, restore: () => void) => void;
};

const Ctx = createContext<Api | null>(null);

export function useConfirm() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("Confirm provider is missing");
  return ctx;
}

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [ask, setAsk] = useState<PendingAsk | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const pending = useRef<PendingUndo | null>(null);

  function flush() {
    const current = pending.current;
    if (!current) return;
    window.clearTimeout(current.timer);
    pending.current = null;
    setToast(null);
    void current.commit();
  }

  const api: Api = {
    confirm: (next) =>
      new Promise<boolean>((resolve) => {
        setAsk({ ...next, resolve });
      }),
    undoable: (label, commit, restore) => {
      flush();
      const timer = window.setTimeout(() => {
        pending.current = null;
        setToast(null);
        void commit();
      }, 5000);
      pending.current = { timer, commit, restore };
      setToast(label);
    },
  };

  function undo() {
    const current = pending.current;
    if (!current) return;
    window.clearTimeout(current.timer);
    pending.current = null;
    setToast(null);
    current.restore();
  }

  function answer(yes: boolean) {
    ask?.resolve(yes);
    setAsk(null);
  }

  useEffect(() => {
    if (!ask) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      ask.resolve(false);
      setAsk(null);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [ask]);

  useEffect(() => () => {
    if (pending.current) {
      window.clearTimeout(pending.current.timer);
      void pending.current.commit();
    }
  }, []);

  return (
    <Ctx.Provider value={api}>
      {children}
      {ask && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center bg-navy-dark/50 p-4" onClick={() => answer(false)}>
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-lift"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 id="confirm-title" className="text-lg font-bold text-navy">{ask.title}</h3>
            <p className="mt-2 text-sm text-slate-600">{ask.body}</p>
            <div className="mt-6 flex justify-end gap-2">
              <button type="button" className="rounded-lg border px-4 py-2 text-sm font-semibold text-navy" onClick={() => answer(false)}>
                Cancel
              </button>
              <button
                type="button"
                className={`rounded-lg px-4 py-2 text-sm font-semibold text-white ${ask.danger === false ? "bg-navy" : "bg-red-700"}`}
                onClick={() => answer(true)}
              >
                {ask.confirmLabel || "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
      {toast && (
        <div className="fixed bottom-4 left-1/2 z-[95] flex -translate-x-1/2 items-center gap-4 rounded-full bg-navy px-4 py-2 text-sm text-white shadow-lift" role="status">
          <span>{toast}</span>
          <button type="button" className="font-bold text-gold" onClick={undo}>
            Undo
          </button>
        </div>
      )}
    </Ctx.Provider>
  );
}
