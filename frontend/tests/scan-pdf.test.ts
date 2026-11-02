import { test } from "node:test";
import assert from "node:assert/strict";
import { PDFDocument } from "pdf-lib";
import { fitWithin, photosToPdf, scanFileName } from "../src/lib/scan-pdf.ts";

test("fitWithin shrinks the longest side to 2000 and never enlarges", () => {
  assert.deepEqual(fitWithin(4000, 3000), { width: 2000, height: 1500 });
  assert.deepEqual(fitWithin(3000, 4032), { width: 1488, height: 2000 });
  assert.deepEqual(fitWithin(800, 600), { width: 800, height: 600 });
});

test("scan file names are dated PDFs", () => {
  assert.equal(scanFileName(new Date(2026, 10, 2, 9, 5)), "Scan 2026-11-02 0905.pdf");
});

test("pages from several PDFs are combined in order into one PDF", async () => {
  const make = async (n: number) => {
    const d = await PDFDocument.create();
    for (let i = 0; i < n; i++) d.addPage([200 + i, 300]);
    return new File([(await d.save()) as BlobPart], `p${n}.pdf`, { type: "application/pdf" });
  };
  const out = await photosToPdf([await make(2), await make(1)], "Scan.pdf");
  assert.equal(out.type, "application/pdf");
  const back = await PDFDocument.load(await out.arrayBuffer());
  assert.equal(back.getPageCount(), 3);
  assert.equal(Math.round(back.getPage(1).getWidth()), 201);
});

test("non-photo files are refused", async () => {
  await assert.rejects(photosToPdf([new File(["x"], "a.exe", { type: "application/octet-stream" })]), /not a photo or PDF/);
  await assert.rejects(photosToPdf([]), /at least one photo/);
});
