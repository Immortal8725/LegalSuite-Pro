import { PDFDocument } from "pdf-lib";

/** Longest side of a scanned page in pixels. About 200 dpi on an A4 page: sharp enough to read, small enough to send. */
export const SCAN_MAX_SIDE = 2000;
export const SCAN_JPEG_QUALITY = 0.72;

/** Fit width x height inside max on the longest side, never enlarging. */
export function fitWithin(width: number, height: number, max = SCAN_MAX_SIDE) {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** A safe file name for a scan, e.g. "Scan 2026-11-02 1405.pdf". */
export function scanFileName(now = new Date()) {
  const p = (n: number) => String(n).padStart(2, "0");
  return `Scan ${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())} ${p(now.getHours())}${p(now.getMinutes())}.pdf`;
}

/** Decode a photo the right way up (EXIF orientation), shrink it and re-encode as JPEG. */
async function compressPhoto(file: File): Promise<{ bytes: Uint8Array; width: number; height: number }> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  const { width, height } = fitWithin(bitmap.width, bitmap.height);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("This browser cannot process photos.");
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close?.();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", SCAN_JPEG_QUALITY));
  if (!blob) throw new Error("Could not read that photo. Try again.");
  return { bytes: new Uint8Array(await blob.arrayBuffer()), width, height };
}

/** Combine photos (and any PDFs) into one PDF, one photo per page, in the order given. */
export async function photosToPdf(files: File[], name = scanFileName()): Promise<File> {
  if (files.length === 0) throw new Error("Take at least one photo first.");
  const pdf = await PDFDocument.create();
  pdf.setTitle(name.replace(/\.pdf$/i, ""));
  pdf.setCreator("Owlaw scan");
  for (const f of files) {
    if (f.type === "application/pdf" || f.name.toLowerCase().endsWith(".pdf")) {
      const src = await PDFDocument.load(await f.arrayBuffer(), { ignoreEncryption: true });
      const pages = await pdf.copyPages(src, src.getPageIndices());
      pages.forEach((p) => pdf.addPage(p));
      continue;
    }
    if (!f.type.startsWith("image/")) throw new Error(`${f.name} is not a photo or PDF.`);
    const img = await compressPhoto(f);
    const jpg = await pdf.embedJpg(img.bytes);
    const page = pdf.addPage([img.width, img.height]);
    page.drawImage(jpg, { x: 0, y: 0, width: img.width, height: img.height });
  }
  const out = await pdf.save();
  return new File([out as BlobPart], name, { type: "application/pdf" });
}
