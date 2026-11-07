/** Run at most once per window, including scroll and resize. */
export function throttle<T extends (...args: never[]) => void>(fn: T, ms: number) {
  let last = 0;
  let timer: number | undefined;
  let pending: Parameters<T> | null = null;
  const run = (args: Parameters<T>) => {
    last = Date.now();
    pending = null;
    fn(...args);
  };
  return (...args: Parameters<T>) => {
    const now = Date.now();
    const wait = ms - (now - last);
    pending = args;
    if (wait <= 0) {
      if (timer !== undefined) {
        window.clearTimeout(timer);
        timer = undefined;
      }
      run(args);
      return;
    }
    if (timer !== undefined) return;
    timer = window.setTimeout(() => {
      timer = undefined;
      if (pending) run(pending);
    }, wait);
  };
}

/** Ignore a second click until the first has had time to finish. */
export function throttleClick(ms = 600) {
  let locked = false;
  return (fn: () => void) => {
    if (locked) return;
    locked = true;
    fn();
    window.setTimeout(() => {
      locked = false;
    }, ms);
  };
}
