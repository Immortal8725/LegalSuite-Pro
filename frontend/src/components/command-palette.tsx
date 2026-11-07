"use client";

import { useEffect, useMemo, useState } from "react";

export type PaletteItem = { href: string; label: string; section: string };

export function CommandPalette({
  open,
  items,
  onClose,
  onGo,
}: {
  open: boolean;
  items: PaletteItem[];
  onClose: () => void;
  onGo: (href: string) => void;
}) {
  const [q, setQ] = useState("");
  useEffect(() => {
    if (open) setQ("");
  }, [open]);
  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return items.filter((item) => !needle || `${item.label} ${item.section}`.toLowerCase().includes(needle)).slice(0, 12);
  }, [items, q]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[85] flex items-start justify-center bg-navy-dark/50 p-4 pt-24" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="Go to a page" className="w-full max-w-lg rounded-2xl bg-white shadow-lift" onClick={(e) => e.stopPropagation()}>
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Go to a page"
          className="w-full rounded-t-2xl border-b px-4 py-3 text-sm outline-none"
          onKeyDown={(e) => {
            if (e.key === "Enter" && shown[0]) onGo(shown[0].href);
          }}
        />
        <ul className="max-h-80 overflow-y-auto p-2">
          {shown.map((item) => (
            <li key={item.href}>
              <button type="button" className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50" onClick={() => onGo(item.href)}>
                <span className="font-semibold text-navy">{item.label}</span>
                <span className="text-xs text-slate-500">{item.section}</span>
              </button>
            </li>
          ))}
          {shown.length === 0 && <li className="px-3 py-2 text-sm text-slate-600">No matching page.</li>}
        </ul>
      </div>
    </div>
  );
}

export function ShortcutHelp({ open, onClose }: { open: boolean; onClose: () => void }) {
  if (!open) return null;
  const rows = [
    ["Ctrl K or Cmd K", "Open the page list"],
    ["?", "Show these shortcuts"],
    ["Escape", "Close a dialog"],
    ["Enter", "Send a message"],
  ];
  return (
    <div className="fixed inset-0 z-[85] flex items-center justify-center bg-navy-dark/50 p-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby="shortcut-title" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-lift" onClick={(e) => e.stopPropagation()}>
        <h3 id="shortcut-title" className="text-lg font-bold text-navy">Keyboard shortcuts</h3>
        <ul className="mt-4 space-y-2 text-sm">
          {rows.map(([key, what]) => (
            <li key={key} className="flex justify-between gap-4">
              <span className="font-semibold text-navy">{key}</span>
              <span className="text-slate-600">{what}</span>
            </li>
          ))}
        </ul>
        <button type="button" className="mt-6 rounded-lg bg-navy px-4 py-2 text-sm font-semibold text-white" onClick={onClose}>
          Close
        </button>
      </div>
    </div>
  );
}
