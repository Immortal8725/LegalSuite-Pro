const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailProblem(value: string, required = true): string | null {
  const v = value.trim();
  if (!v) return required ? "Enter an email address." : null;
  return EMAIL.test(v) ? null : "Enter a valid email address.";
}

export function requiredProblem(value: string, label: string): string | null {
  return value.trim() ? null : `Enter ${label}.`;
}

export function passwordProblem(value: string, min = 8): string | null {
  return value.length >= min ? null : `Use at least ${min} characters.`;
}

export function FieldHint({ text }: { text?: string | null }) {
  if (!text) return null;
  return <p className="mt-1 text-xs text-red-700">{text}</p>;
}
