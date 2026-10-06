/** Pexels License placeholders until a firm uploads its own photographs.
 * reading-room 2467506, desk 271624,
 * portrait-lindiwe 5196044, portrait-thabo 4600305, portrait-sipho 34592823.
 */
const NAMED: Record<string, string> = {
  "Lindiwe Mokoena": "/marketing/portrait-lindiwe.jpg",
  "Thabo Ndlovu": "/marketing/portrait-thabo.jpg",
  "Sipho Dlamini": "/marketing/portrait-sipho.jpg",
};

const POOL = Object.values(NAMED);

export const READING_ROOM = "/marketing/reading-room.jpg";
export const DESK = "/marketing/desk.jpg";

export function portraitSrc(name: string, index = 0) {
  return NAMED[name] || POOL[index % POOL.length];
}
