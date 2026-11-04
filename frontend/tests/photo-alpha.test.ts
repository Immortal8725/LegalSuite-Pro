import { test } from "node:test";
import assert from "node:assert/strict";
import { applyBackgroundMask, fitPhoto, keptShare } from "../src/lib/photo-alpha.ts";

test("fitPhoto caps the long side and never enlarges", () => {
  assert.deepEqual(fitPhoto(3200, 2400), { width: 1600, height: 1200 });
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
