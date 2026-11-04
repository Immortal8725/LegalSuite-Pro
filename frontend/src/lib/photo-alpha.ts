/** Pure helpers for background removal, kept free of browser APIs so they can be unit tested. */

/** Largest processed file we accept (the server allows the same). */
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024;
/** Largest original we will try to open in the browser. */
export const MAX_SOURCE_BYTES = 40 * 1024 * 1024;
/** Longest side kept. Enough for crisp retina screens; smaller photos are never enlarged. */
export const MAX_PHOTO_SIDE = 3200;

/** Scale so the longest side is at most `max`; never enlarges. */
export function fitPhoto(width: number, height: number, max = MAX_PHOTO_SIDE) {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** Bilinear resize of a single-channel float mask. Returns the input when sizes already match. */
export function upsampleMask(mask: Float32Array, mw: number, mh: number, w: number, h: number) {
  if (mw === w && mh === h) return mask;
  const out = new Float32Array(w * h);
  const sx = mw / w;
  const sy = mh / h;
  for (let y = 0; y < h; y++) {
    const fy = Math.min(mh - 1, Math.max(0, (y + 0.5) * sy - 0.5));
    const y0 = Math.floor(fy);
    const y1 = Math.min(mh - 1, y0 + 1);
    const ty = fy - y0;
    for (let x = 0; x < w; x++) {
      const fx = Math.min(mw - 1, Math.max(0, (x + 0.5) * sx - 0.5));
      const x0 = Math.floor(fx);
      const x1 = Math.min(mw - 1, x0 + 1);
      const tx = fx - x0;
      const a = mask[y0 * mw + x0] * (1 - tx) + mask[y0 * mw + x1] * tx;
      const b = mask[y1 * mw + x0] * (1 - tx) + mask[y1 * mw + x1] * tx;
      out[y * w + x] = a * (1 - ty) + b * ty;
    }
  }
  return out;
}

/** Mean over a (2r+1) square window, using a summed-area table. Edges use the part of the window inside the image. */
function boxMean(src: Float32Array, w: number, h: number, r: number) {
  const W = w + 1;
  const sat = new Float64Array(W * (h + 1));
  for (let y = 0; y < h; y++) {
    let row = 0;
    for (let x = 0; x < w; x++) {
      row += src[y * w + x];
      sat[(y + 1) * W + x + 1] = sat[y * W + x + 1] + row;
    }
  }
  const out = new Float32Array(w * h);
  for (let y = 0; y < h; y++) {
    const ya = Math.max(0, y - r);
    const yb = Math.min(h, y + r + 1);
    for (let x = 0; x < w; x++) {
      const xa = Math.max(0, x - r);
      const xb = Math.min(w, x + r + 1);
      const sum = sat[yb * W + xb] - sat[ya * W + xb] - sat[yb * W + xa] + sat[ya * W + xa];
      out[y * w + x] = sum / ((yb - ya) * (xb - xa));
    }
  }
  return out;
}

/**
 * Guided filter (He et al.) that snaps a blurry, upsampled mask to the real edges in the photo,
 * so hair and shoulders stay sharp at full resolution instead of looking like a stretched 256 px mask.
 */
export function refineMask(rgba: Uint8ClampedArray, keep: Float32Array, w: number, h: number, r: number, eps = 1e-4) {
  const n = w * h;
  const I = new Float32Array(n);
  for (let i = 0; i < n; i++) I[i] = (0.299 * rgba[i * 4] + 0.587 * rgba[i * 4 + 1] + 0.114 * rgba[i * 4 + 2]) / 255;
  const II = new Float32Array(n);
  const Ip = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    II[i] = I[i] * I[i];
    Ip[i] = I[i] * keep[i];
  }
  const mI = boxMean(I, w, h, r);
  const mp = boxMean(keep, w, h, r);
  const mII = boxMean(II, w, h, r);
  const mIp = boxMean(Ip, w, h, r);
  const a = new Float32Array(n);
  const b = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const varI = mII[i] - mI[i] * mI[i];
    const cov = mIp[i] - mI[i] * mp[i];
    a[i] = cov / (varI + eps);
    b[i] = mp[i] - a[i] * mI[i];
  }
  const ma = boxMean(a, w, h, r);
  const mb = boxMean(b, w, h, r);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) out[i] = Math.min(1, Math.max(0, ma[i] * I[i] + mb[i]));
  return out;
}

/** Window radius for the guided filter: about two mask cells at the photo's scale, at least 2 px. */
export function refineRadius(w: number, h: number, modelSide = 256) {
  return Math.max(2, Math.round((Math.max(w, h) / modelSide) * 2));
}

/** Smooth S-curve between low and high, so edges fade without a visible band. */
function smoothstep(low: number, high: number, v: number) {
  const t = Math.min(1, Math.max(0, (v - low) / (high - low)));
  return t * t * (3 - 2 * t);
}

/** Writes a keep-confidence mask (1 = person) into the alpha channel. */
export function applyKeepMask(rgba: Uint8ClampedArray, keep: Float32Array, low = 0.1, high = 0.9) {
  const pixels = Math.min(keep.length, rgba.length / 4);
  for (let i = 0; i < pixels; i++) rgba[i * 4 + 3] = Math.round(rgba[i * 4 + 3] * smoothstep(low, high, keep[i]));
  return rgba;
}

/**
 * Turns a background-confidence mask (0 = person, 1 = background) into the alpha channel of an RGBA buffer.
 * A soft ramp keeps hair and shoulder edges smooth instead of a hard cut-out.
 */
export function applyBackgroundMask(rgba: Uint8ClampedArray, background: Float32Array, low = 0.1, high = 0.9) {
  const keep = new Float32Array(background.length);
  for (let i = 0; i < background.length; i++) keep[i] = 1 - background[i];
  return applyKeepMask(rgba, keep, low, high);
}

/** Share of pixels that stayed visible; near 0 means the model found no person. */
export function keptShare(rgba: Uint8ClampedArray) {
  let kept = 0;
  const pixels = rgba.length / 4;
  for (let i = 0; i < pixels; i++) if (rgba[i * 4 + 3] > 127) kept++;
  return pixels ? kept / pixels : 0;
}

/** Whether an original can be uploaded byte for byte: an accepted type, small enough, and not oversized. */
export function canKeepAsIs(type: string, bytes: number, width: number, height: number) {
  return ["image/jpeg", "image/png", "image/webp"].includes(type) && bytes <= MAX_PHOTO_BYTES && Math.max(width, height) <= MAX_PHOTO_SIDE;
}
