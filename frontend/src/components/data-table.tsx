"use client";

import { useMemo, useState, type ReactNode } from "react";
import { TableWrap, Td, Th } from "@/components/page";

export type Column<T> = {
  key: string;
  header: string;
  sortValue?: (row: T) => string | number;
  render: (row: T) => ReactNode;
};

export function DataTable<T extends { id: string }>({
  rows,
  columns,
  pageSize = 20,
  selected,
  onSelected,
  empty,
}: {
  rows: T[];
  columns: Column<T>[];
  pageSize?: number;
  selected: string[];
  onSelected: (ids: string[]) => void;
  empty?: ReactNode;
}) {
  const [sortKey, setSortKey] = useState(columns[0]?.key || "");
  const [dir, setDir] = useState<"asc" | "desc">("asc");
  const [page, setPage] = useState(0);

  const sorted = useMemo(() => {
    const col = columns.find((c) => c.key === sortKey);
    const copy = [...rows];
    if (!col?.sortValue) return copy;
    copy.sort((a, b) => {
      const av = col.sortValue!(a);
      const bv = col.sortValue!(b);
      const cmp = typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv));
      return dir === "asc" ? cmp : -cmp;
    });
    return copy;
  }, [rows, columns, sortKey, dir]);

  const pages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const safePage = Math.min(page, pages - 1);
  const slice = sorted.slice(safePage * pageSize, safePage * pageSize + pageSize);
  const pageIds = slice.map((r) => r.id);
  const allOnPage = pageIds.length > 0 && pageIds.every((id) => selected.includes(id));

  function toggleSort(key: string) {
    if (sortKey === key) setDir(dir === "asc" ? "desc" : "asc");
    else {
      setSortKey(key);
      setDir("asc");
    }
    setPage(0);
  }

  function toggleAll() {
    if (allOnPage) onSelected(selected.filter((id) => !pageIds.includes(id)));
    else onSelected([...new Set([...selected, ...pageIds])]);
  }

  function toggle(id: string) {
    onSelected(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);
  }

  if (rows.length === 0) return <>{empty}</>;

  return (
    <div>
      <TableWrap>
        <thead>
          <tr>
            <Th>
              <input type="checkbox" aria-label="Select rows on this page" checked={allOnPage} onChange={toggleAll} />
            </Th>
            {columns.map((c) => (
              <Th key={c.key}>
                {c.sortValue ? (
                  <button type="button" className="font-bold uppercase" onClick={() => toggleSort(c.key)}>
                    {c.header} {sortKey === c.key ? (dir === "asc" ? "↑" : "↓") : ""}
                  </button>
                ) : (
                  c.header
                )}
              </Th>
            ))}
          </tr>
        </thead>
        <tbody>
          {slice.map((row) => (
            <tr key={row.id} className="hover:bg-slate-50">
              <Td>
                <input type="checkbox" aria-label="Select row" checked={selected.includes(row.id)} onChange={() => toggle(row.id)} />
              </Td>
              {columns.map((c) => (
                <Td key={c.key}>{c.render(row)}</Td>
              ))}
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <div className="mt-3 flex items-center justify-between text-sm text-slate-600">
        <span>
          {sorted.length} row{sorted.length === 1 ? "" : "s"}
          {selected.length ? ` · ${selected.length} selected` : ""}
        </span>
        <div className="flex gap-2">
          <button type="button" className="rounded border px-2 py-1" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
            Previous
          </button>
          <span>
            Page {safePage + 1} of {pages}
          </span>
          <button type="button" className="rounded border px-2 py-1" disabled={safePage >= pages - 1} onClick={() => setPage(safePage + 1)}>
            Next
          </button>
        </div>
      </div>
    </div>
  );
}

export function downloadCsv(filename: string, headers: string[], lines: string[][]) {
  const esc = (v: string) => `"${v.replaceAll("\"", "\"\"")}"`;
  const body = [headers, ...lines].map((row) => row.map(esc).join(",")).join("\n");
  const blob = new Blob([body], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
