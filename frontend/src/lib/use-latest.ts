import { useEffect, useRef } from "react";

/**
 * Keeps a ref pointing at the latest value. Lets an effect call the current version of a
 * function without listing it as a dependency (which would re-run the effect on every render).
 * Declare it before the effect that reads it, so it is updated first.
 */
export function useLatest<T>(value: T) {
  const ref = useRef(value);
  useEffect(() => {
    ref.current = value;
  });
  return ref;
}
