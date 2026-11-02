"use client";

import { useRef, useState } from "react";
import { Camera, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { photosToPdf } from "@/lib/scan-pdf";
import { MAX_UPLOAD_BYTES } from "@/lib/inbox";

/**
 * Take photos with the phone camera (or pick photos and PDFs) and combine them into one PDF.
 * Pages are turned the right way up and compressed on this device before anything is sent.
 */
export function ScanToPdf({ onReady, label = "Scan with camera" }: { onReady: (pdf: File) => void | Promise<void>; label?: string }) {
  const [pages, setPages] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const cam = useRef<HTMLInputElement>(null);

  async function finish() {
    setBusy(true);
    setErr(null);
    try {
      const pdf = await photosToPdf(pages);
      if (pdf.size > MAX_UPLOAD_BYTES) throw new Error("The scan is over 50 MB. Remove some pages and try again.");
      await onReady(pdf);
      setPages([]);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Could not make the PDF.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-2">
      <input
        ref={cam}
        type="file"
        accept="image/*,application/pdf"
        capture="environment"
        multiple
        className="hidden"
        aria-label="Take a photo of a page"
        onChange={(e) => {
          const picked = Array.from(e.target.files ?? []);
          setPages((p) => [...p, ...picked]);
          e.target.value = "";
        }}
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" size="sm" variant="outline" onClick={() => cam.current?.click()}>
          <Camera className="h-4 w-4" /> {pages.length ? "Add another page" : label}
        </Button>
        {pages.length > 0 && (
          <Button type="button" size="sm" disabled={busy} onClick={finish}>
            {busy ? "Making PDF..." : `Make PDF (${pages.length} page${pages.length === 1 ? "" : "s"})`}
          </Button>
        )}
      </div>
      {pages.length > 0 && (
        <ol className="flex flex-wrap gap-1 text-xs text-slate-600">
          {pages.map((p, i) => (
            <li key={`${p.name}-${i}`} className="inline-flex items-center gap-1 rounded border px-2 py-0.5">
              Page {i + 1}
              <button type="button" aria-label={`Remove page ${i + 1}`} onClick={() => setPages((all) => all.filter((_, j) => j !== i))}>
                <X className="h-3 w-3" />
              </button>
            </li>
          ))}
        </ol>
      )}
      {err && <p className="text-xs text-red-700">{err}</p>}
    </div>
  );
}
