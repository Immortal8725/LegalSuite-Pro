import { test } from "node:test";
import assert from "node:assert/strict";
import { applyBackgroundMask, canKeepAsIs, fitPhoto, keptShare, refineMask, refineRadius, upsampleMask } from "../src/lib/photo-alpha.ts";

test("fitPhoto caps the long side at 3200 and never enlarges", () => {
  assert.deepEqual(fitPhoto(6400, 4800), { width: 3200, height: 2400 });
  assert.deepEqual(fitPhoto(800, 600), { width: 800, height: 600 });
});

test("background pixels become transparent and the person stays", () => {
  const rgba = new Uint8ClampedArray([10, 10, 10, 255, 20, 20, 20, 255, 30, 30, 30, 255]);
  applyBackgroundMask(rgba, new Float32Array([1, 0, 0.5]));
  assert.equal(rgba[3], 0);
  assert.equal(rgba[7], 255);
  assert.ok(rgba[11] > 100 && rgba[11] < 160);
  assert.ok(Math.abs(keptShare(rgba) - 2 / 3) < 0.01);
});

test("upsampleMask keeps size when equal and interpolates when larger", () => {
  const m = new Float32Array([0, 1]);
  assert.equal(upsampleMask(m, 2, 1, 2, 1), m);
  const up = upsampleMask(m, 2, 1, 4, 1);
  assert.equal(up.length, 4);
  assert.equal(up[0], 0);
  assert.equal(up[3], 1);
  assert.ok(up[1] > 0 && up[1] < up[2]);
});

test("refineMask snaps a blurry mask to the real edge in the photo", () => {
  // 40 px wide strip: dark person on the left half, bright background on the right.
  const w = 40, h = 4;
  const rgba = new Uint8ClampedArray(w * h * 4);
  const blurry = new Float32Array(w * h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const v = x < 20 ? 30 : 230;
      rgba.set([v, v, v, 255], (y * w + x) * 4);
      blurry[y * w + x] = Math.min(1, Math.max(0, (30 - x) / 20)); // soft ramp from x=10 to x=30
    }
  const sharp = refineMask(rgba, blurry, w, h, 4);
  const at = (x: number, m: Float32Array) => m[w + x];
  // Just inside the person the refined mask is more confident; just outside it is less.
  assert.ok(at(18, sharp) > at(18, blurry));
  assert.ok(at(22, sharp) < at(22, blurry));
});

test("refineRadius grows with resolution", () => {
  assert.equal(refineRadius(256, 200), 2);
  assert.equal(refineRadius(3200, 2400), 25);
});

test("originals are kept byte for byte only when type, size and dimensions allow", () => {
  assert.ok(canKeepAsIs("image/jpeg", 4_000_000, 3000, 2000));
  assert.ok(!canKeepAsIs("image/heic", 4_000_000, 3000, 2000));
  assert.ok(!canKeepAsIs("image/jpeg", 20_000_000, 3000, 2000));
  assert.ok(!canKeepAsIs("image/png", 4_000_000, 4000, 3000));
});
