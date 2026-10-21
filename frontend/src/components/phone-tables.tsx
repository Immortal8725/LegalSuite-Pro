"use client";

import { useEffect, useRef, type ReactNode } from "react";

/**
 * Labels every table cell with its column heading so that, below 640px, CSS can show each row as a card
 * (see .rt-cards in globals.css). Tables marked data-keep-table are left as scrollable tables.
 */
function label(root: HTMLElement) {
  root.querySelectorAll<HTMLTableElement>("table:not([data-keep-table])").forEach((table) => {
    const heads = Array.from(table.querySelectorAll("thead th")).map((th) => (th.textContent || "").trim());
    if (heads.length === 0) return;
    table.classList.add("rt-cards");
    table.querySelectorAll("tbody tr").forEach((tr) => {
      Array.from(tr.children).forEach((cell, i) => {
        const text = heads[i] || "";
        if (cell.getAttribute("data-label") !== text) cell.setAttribute("data-label", text);
      });
    });
  });
}

export function PhoneTables({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    let queued = false;
    const run = () => {
      queued = false;
      label(root);
    };
    run();
    const obs = new MutationObserver(() => {
      if (!queued) {
        queued = true;
        requestAnimationFrame(run);
      }
    });
    obs.observe(root, { childList: true, subtree: true });
    return () => obs.disconnect();
  }, []);
  return <div ref={ref}>{children}</div>;
}
