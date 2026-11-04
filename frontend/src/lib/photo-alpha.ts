/** Pure helpers for background removal, kept free of browser APIs so they can be unit tested. */

export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
export const MAX_PHOTO_SIDE = 1600;

/** Scale so the longest side is at most `max`; never enlarges. */
export function fitPhoto(width: number, height: number, max = MAX_PHOTO_SIDE) {
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/**
 * Turns a background-confidence mask (0 = person, 1 = background) into the alpha channel of an RGBA buffer.
 * A soft ramp keeps hair and shoulder edges smooth instead of a hard cut-out.
 */
export function applyBackgroundMask(rgba: Uint8ClampedArray, background: Float32Array, low = 0.15, high = 0.85) {
  const pixels = Math.min(background.length, rgba.length / 4);
  for (let i = 0; i < pixels; i++) {
    const keep = 1 - background[i];
    const t = Math.min(1, Math.max(0, (keep - low) / (high - low)));
    rgba[i * 4 + 3] = Math.round(rgba[i * 4 + 3] * t);
  }
  return rgba;
}

/** Share of pixels that stayed visible; near 0 means the model found no person. */
export function keptShare(rgba: Uint8ClampedArray) {
  let kept = 0;
  const pixels = rgba.length / 4;
  for (let i = 0; i < pixels; i++) if (rgba[i * 4 + 3] > 127) kept++;
  return pixels ? kept / pixels : 0;
}
