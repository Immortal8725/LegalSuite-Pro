"use client";

import { MAX_PHOTO_BYTES, applyBackgroundMask, fitPhoto, keptShare } from "@/lib/photo-alpha";

/**
 * Background removal that runs in the visitor's own browser with Google MediaPipe (Apache 2.0).
 * Nothing is sent to a paid service. The library, its WebAssembly and the small portrait model
 * are fetched only when the attorney first asks for it.
 */
const VERSION = "0.10.35";
const WASM = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VERSION}/wasm`;
const MODEL = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite";

type Segmenter = import("@mediapipe/tasks-vision").ImageSegmenter;
let loading: Promise<Segmenter> | null = null;

function segmenter(): Promise<Segmenter> {
  if (!loading) {
    loading = (async () => {
      const { FilesetResolver, ImageSegmenter } = await import("@mediapipe/tasks-vision");
      const files = await FilesetResolver.forVisionTasks(WASM);
      const make = (delegate: "GPU" | "CPU") =>
        ImageSegmenter.createFromOptions(files, {
          baseOptions: { modelAssetPath: MODEL, delegate },
          runningMode: "IMAGE",
          outputConfidenceMasks: true,
          outputCategoryMask: false,
        });
      try {
        return await make("GPU");
      } catch {
        return make("CPU");
      }
    })();
    loading.catch(() => {
      loading = null;
    });
  }
  return loading;
}

async function drawScaled(file: Blob) {
  const bitmap = await createImageBitmap(file);
  const { width, height } = fitPhoto(bitmap.width, bitmap.height);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) throw new Error("This browser cannot edit photographs.");
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  return { canvas, ctx };
}

function toBlob(canvas: HTMLCanvasElement, type: string, quality?: number) {
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not save the photograph."))), type, quality),
  );
}

/** Prefer WebP with transparency, fall back to PNG on browsers that cannot write WebP. */
async function encodeTransparent(canvas: HTMLCanvasElement) {
  let blob = await toBlob(canvas, "image/webp", 0.9);
  if (blob.type !== "image/webp") blob = await toBlob(canvas, "image/png");
  return blob;
}

export async function removeBackground(file: Blob): Promise<{ blob: Blob; foundPerson: boolean }> {
  const [model, { canvas, ctx }] = await Promise.all([segmenter(), drawScaled(file)]);
  const result = model.segment(canvas);
  try {
    const mask = result.confidenceMasks?.[0];
    if (!mask) throw new Error("Background removal gave no result.");
    const background = mask.getAsFloat32Array();
    const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
    applyBackgroundMask(image.data, background);
    ctx.putImageData(image, 0, 0);
    const blob = await encodeTransparent(canvas);
    if (blob.size > MAX_PHOTO_BYTES) throw new Error("The result is over 5 MB. Try a smaller photograph.");
    return { blob, foundPerson: keptShare(image.data) > 0.02 };
  } finally {
    result.close();
  }
}

/** The photograph as taken, resized so it uploads quickly. */
export async function keepOriginal(file: Blob): Promise<Blob> {
  const { canvas } = await drawScaled(file);
  let blob = await toBlob(canvas, "image/webp", 0.88);
  if (blob.type !== "image/webp") blob = await toBlob(canvas, "image/jpeg", 0.88);
  if (blob.size > MAX_PHOTO_BYTES) throw new Error("That photograph is over 5 MB even after resizing.");
  return blob;
}
