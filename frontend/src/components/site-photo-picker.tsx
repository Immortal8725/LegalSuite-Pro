"use client";

import { MAX_SOURCE_BYTES } from "@/lib/photo-alpha";
import { useEffect, useRef, useState } from "react";
import { api, apiBlob } from "@/lib/api";
import { Button } from "@/components/ui/button";
import type { PublicSitePhotoState } from "@/lib/types";

const CHECKER = {
  backgroundImage: "linear-gradient(45deg,#e2e8f0 25%,transparent 25%),linear-gradient(-45deg,#e2e8f0 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#e2e8f0 75%),linear-gradient(-45deg,transparent 75%,#e2e8f0 75%)",
  backgroundSize: "16px 16px",
  backgroundPosition: "0 0,0 8px,8px -8px,-8px 0",
};

type Stage =
  | { kind: "idle" }
  | { kind: "working"; message: string }
  | { kind: "review"; original: string; originalBlob: Blob; cutout: string | null; cutoutBlob: Blob | null; note?: string };

function stateLabel(state?: PublicSitePhotoState) {
  if (!state || (!state.draft && !state.live)) return "Using the Owlaw placeholder.";
  if (state.pending && state.draft) return "New photograph saved. It goes live once Owlaw approves your branding.";
  if (state.pending && !state.draft) return "Removed from your draft. The placeholder returns once Owlaw approves your branding.";
  return "On the public site.";
}

export function SitePhotoPicker({
  slot,
  label,
  hint,
  state,
  cutoutByDefault,
  onSaved,
}: {
  slot: string;
  label: string;
  hint: string;
  state?: PublicSitePhotoState;
  cutoutByDefault: boolean;
  onSaved: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<Stage>({ kind: "idle" });
  const [current, setCurrent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let url: string | null = null;
    if (state?.draft) {
      apiBlob(`/api/v1/public-site/photos/${slot}`)
        .then((blob) => {
          url = URL.createObjectURL(blob);
          setCurrent(url);
        })
        .catch(() => setCurrent(null));
    } else {
      setCurrent(null);
    }
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [slot, state?.draft, state?.draftVersion]);

  function reset() {
    if (stage.kind === "review") {
      URL.revokeObjectURL(stage.original);
      if (stage.cutout) URL.revokeObjectURL(stage.cutout);
    }
    setStage({ kind: "idle" });
    if (input.current) input.current.value = "";
  }

  async function choose(file: File) {
    setError(null);
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      setError("Use a JPEG, PNG or WebP photograph.");
      return;
    }
    if (file.size > MAX_SOURCE_BYTES) {
      setError("That photograph is over 40 MB. Choose a smaller one.");
      return;
    }
    const tools = await import("@/lib/remove-background");
    setStage({ kind: "working", message: "Preparing the photograph..." });
    const originalBlob = await tools.keepOriginal(file);
    const original = URL.createObjectURL(originalBlob);
    if (!cutoutByDefault) {
      setStage({ kind: "review", original, originalBlob, cutout: null, cutoutBlob: null });
      return;
    }
    setStage({ kind: "working", message: "Clearing the background. The first time takes a little longer while the tool loads (about 10 MB)." });
    try {
      const { blob, foundPerson } = await tools.removeBackground(file);
      setStage({
        kind: "review",
        original,
        originalBlob,
        cutout: URL.createObjectURL(blob),
        cutoutBlob: blob,
        note: foundPerson ? undefined : "No person was found in this photograph. Keep the original instead.",
      });
    } catch (err) {
      setStage({
        kind: "review",
        original,
        originalBlob,
        cutout: null,
        cutoutBlob: null,
        note: `The background could not be cleared on this device${err instanceof Error ? ` (${err.message})` : ""}. You can keep the original.`,
      });
    }
  }

  async function clearNow() {
    if (stage.kind !== "review") return;
    const { original, originalBlob } = stage;
    setStage({ kind: "working", message: "Clearing the background. The first time takes a little longer while the tool loads (about 10 MB)." });
    try {
      const tools = await import("@/lib/remove-background");
      const { blob, foundPerson } = await tools.removeBackground(originalBlob);
      setStage({ kind: "review", original, originalBlob, cutout: URL.createObjectURL(blob), cutoutBlob: blob, note: foundPerson ? undefined : "No person was found in this photograph." });
    } catch (err) {
      setStage({ kind: "review", original, originalBlob, cutout: null, cutoutBlob: null, note: err instanceof Error ? err.message : "Could not clear the background." });
    }
  }

  async function upload(blob: Blob) {
    setError(null);
    const ext = blob.type === "image/png" ? "png" : blob.type === "image/jpeg" ? "jpg" : "webp";
    const form = new FormData();
    form.append("file", blob, `${slot}.${ext}`);
    setStage({ kind: "working", message: "Saving..." });
    try {
      await api(`/api/v1/public-site/photos/${slot}`, { method: "POST", body: form });
      reset();
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the photograph.");
      setStage({ kind: "idle" });
    }
  }

  async function remove() {
    setError(null);
    try {
      await api(`/api/v1/public-site/photos/${slot}`, { method: "DELETE" });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove the photograph.");
    }
  }

  return (
    <div className="border-b border-slate-100 pb-5" data-testid={`photo-${slot}`}>
      <div className="flex flex-wrap items-start gap-4">
        <div className="relative h-28 w-24 shrink-0 overflow-hidden border border-slate-200" style={CHECKER}>
          {current ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={current} alt={`Current ${label}`} className="h-full w-full object-cover object-top" />
          ) : (
            <span className="absolute inset-0 grid place-items-center px-2 text-center text-[11px] text-slate-500">Placeholder</span>
          )}
        </div>
        <div className="min-w-0 flex-1 text-sm">
          <p className="font-semibold text-slate-900">{label}</p>
          <p className="mt-1 text-slate-600">{hint}</p>
          <p className="mt-1 text-xs text-slate-600">{stateLabel(state)}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <input
              ref={input}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              aria-label={`Choose ${label}`}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) choose(file).catch((err: unknown) => {
                  setError(err instanceof Error ? err.message : "Could not read that photograph.");
                  setStage({ kind: "idle" });
                });
              }}
            />
            <Button variant="outline" disabled={stage.kind === "working"} onClick={() => input.current?.click()}>
              {current ? "Replace photograph" : "Choose photograph"}
            </Button>
            {state?.draft && stage.kind === "idle" ? (
              <Button variant="outline" onClick={() => remove()}>
                Use placeholder
              </Button>
            ) : null}
          </div>
          {error ? <p className="mt-2 text-sm text-red-700">{error}</p> : null}
        </div>
      </div>

      {stage.kind === "working" ? (
        <p className="mt-4 flex items-center gap-2 text-sm text-slate-700" role="status">
          <span className="h-3 w-3 animate-spin rounded-full border-2 border-navy border-t-transparent" />
          {stage.message}
        </p>
      ) : null}

      {stage.kind === "review" ? (
        <div className="mt-4 space-y-3">
          <div className="grid gap-3 sm:grid-cols-3">
            <figure>
              <div className="aspect-[4/5] overflow-hidden border border-slate-200 bg-white">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={stage.original} alt="Original" className="h-full w-full object-cover object-top" />
              </div>
              <figcaption className="mt-1 text-xs text-slate-600">Original</figcaption>
            </figure>
            {stage.cutout ? (
              <>
                <figure>
                  <div className="aspect-[4/5] overflow-hidden border border-slate-200 bg-[#f4f1ea]">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={stage.cutout} alt="Background cleared, light site" className="h-full w-full object-cover object-top" />
                  </div>
                  <figcaption className="mt-1 text-xs text-slate-600">Background cleared, on the light site</figcaption>
                </figure>
                <figure>
                  <div className="aspect-[4/5] overflow-hidden border border-slate-200 bg-[#0d1626]">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={stage.cutout} alt="Background cleared, dark site" className="h-full w-full object-cover object-top" />
                  </div>
                  <figcaption className="mt-1 text-xs text-slate-600">On the dark site</figcaption>
                </figure>
              </>
            ) : null}
          </div>
          {stage.note ? <p className="text-sm text-amber-700">{stage.note}</p> : null}
          <div className="flex flex-wrap gap-2">
            {stage.cutoutBlob ? <Button onClick={() => upload(stage.cutoutBlob as Blob)}>Use with background cleared</Button> : null}
            {!stage.cutout ? (
              <Button variant="outline" onClick={() => clearNow()}>
                Clear the background
              </Button>
            ) : null}
            <Button variant="outline" onClick={() => upload(stage.originalBlob)}>
              Keep original
            </Button>
            <Button variant="outline" onClick={() => reset()}>
              Cancel
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
