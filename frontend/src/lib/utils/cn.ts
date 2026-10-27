type ClassValue = string | false | null | undefined;

/** Joins class names, skipping falsy values. Keeps conditional Tailwind classes readable. */
export function cn(...classes: ClassValue[]): string {
  return classes.filter(Boolean).join(' ');
}
