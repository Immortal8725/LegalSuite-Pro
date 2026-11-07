"use client";

import { useEffect, useMemo, useState } from "react";

/** Text filter that waits 250 ms, matching the header search. */
export function useDebouncedFilter(ms = 250) {
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => {
    const t = window.setTimeout(() => setQuery(text.trim().toLowerCase()), ms);
    return () => window.clearTimeout(t);
  }, [text, ms]);
  return { text, setText, query, ready: useMemo(() => query, [query]) };
}
