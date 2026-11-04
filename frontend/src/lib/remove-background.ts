"use client";

import {
  MAX_PHOTO_BYTES,
  applyKeepMask,
  canKeepAsIs,
  fitPhoto,
  keptShare,
  refineMask,
  refineRadius,
  upsampleMask,
} from "@/lib/photo-alpha";

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

/** The model works on a 256 px grid, so it only needs a modest copy; the mask is then refined at full size. */
const SEGMENT_SIDE = 1024;

async function drawScaled(file: Blob) {
  const bitmap = await createImageBitmap(file);
  const source = { width: bitmap.width, height: bitmap.height };
  const { width, height } = fitPhoto(bitmap.width, bitmap.height);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) throw new Error("This browser cannot edit photographs.");
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  return { canvas, ctx, source };
}

function shrink(canvas: HTMLCanvasElement, max: number) {
  const { width, height } = fitPhoto(canvas.width, canvas.height, max);
  if (width === canvas.width) return canvas;
  const small = document.createElement("canvas");
  small.width = width;
  small.height = height;
  const ctx = small.getContext("2d");
  if (!ctx) return canvas;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(canvas, 0, 0, width, height);
  return small;
}

function toBlob(canvas: HTMLCanvasElement, type: string, quality?: number) {
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not save the photograph."))), type, quality),
  );
}

const LIMIT_MB = Math.round(MAX_PHOTO_BYTES / 1024 / 1024);

/** Lossless PNG first; near-lossless WebP only when the PNG would be too large to upload. */
async function encodeTransparent(canvas: HTMLCanvasElement) {
  const png = await toBlob(canvas, "image/png");
  if (png.size <= MAX_PHOTO_BYTES) return png;
  for (const q of [0.97, 0.94]) {
    const webp = await toBlob(canvas, "image/webp", q);
    if (webp.type === "image/webp" && webp.size <= MAX_PHOTO_BYTES) return webp;
  }
  throw new Error(`The result is over ${LIMIT_MB} MB. Try a smaller photograph.`);
}

export async function removeBackground(file: Blob): Promise<{ blob: Blob; foundPerson: boolean }> {
  const [model, { canvas, ctx }] = await Promise.all([segmenter(), drawScaled(file)]);
  const input = shrink(canvas, SEGMENT_SIDE);
  const result = model.segment(input);
  try {
    const mask = result.confidenceMasks?.[0];
    if (!mask) throw new Error("Background removal gave no result.");
    const background = mask.getAsFloat32Array();
    const keepSmall = new Float32Array(background.length);
    for (let i = 0; i < background.length; i++) keepSmall[i] = 1 - background[i];
    const { width: w, height: h } = canvas;
    const keep = upsampleMask(keepSmall, mask.width, mask.height, w, h);
    const image = ctx.getImageData(0, 0, w, h);
    const refined = refineMask(image.data, keep, w, h, refineRadius(w, h));
    applyKeepMask(image.data, refined);
    ctx.putImageData(image, 0, 0);
    const blob = await encodeTransparent(canvas);
    return { blob, foundPerson: keptShare(image.data) > 0.02 };
  } finally {
    result.close();
  }
}

/** The photograph as taken. Uploaded byte for byte when it already fits; otherwise resized at near-lossless quality. */
export async function keepOriginal(file: Blob): Promise<Blob> {
  const { canvas, source } = await drawScaled(file);
  if (canKeepAsIs(file.type, file.size, source.width, source.height)) return file;
  for (const q of [0.95, 0.9]) {
    let blob = await toBlob(canvas, "image/webp", q);
    if (blob.type !== "image/webp") blob = await toBlob(canvas, "image/jpeg", q);
    if (blob.size <= MAX_PHOTO_BYTES) return blob;
  }
  throw new Error(`That photograph is over ${LIMIT_MB} MB even after resizing.`);
}
