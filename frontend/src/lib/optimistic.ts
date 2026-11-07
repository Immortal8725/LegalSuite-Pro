/** Apply a low-risk change at once, then roll it back if the server refuses. */
export async function optimistic(apply: () => void, rollback: () => void, request: () => Promise<unknown>) {
  apply();
  try {
    await request();
  } catch (err) {
    rollback();
    throw err;
  }
}
