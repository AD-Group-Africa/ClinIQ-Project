import { cn } from '@/lib/utils/cn';

/**
 * Shared look for Input, Select, Textarea and SearchField so they can never drift apart.
 * The border colour is the darker --color-border-input (3:1 or better against white), because the
 * lighter Canva border does not meet WCAG 2.2 "non-text contrast" for form fields.
 */
export function controlClasses(invalid?: boolean, className?: string): string {
  return cn(
    'w-full rounded-control border bg-surface px-4 py-3 text-sm font-medium text-ink',
    'placeholder:text-muted transition-shadow',
    'focus:shadow-halo disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-muted',
    invalid ? 'border-danger' : 'border-line-input',
    className
  );
}
