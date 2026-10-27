import { IconLoader2 } from '@tabler/icons-react';
import { cn } from '@/lib/utils/cn';

export interface LoadingProps {
  label?: string;
  className?: string;
}

/** Spinner plus visible text. role="status" makes screen readers announce it politely. */
export function Loading({ label = 'Loading…', className }: LoadingProps) {
  return (
    <div role="status" className={cn('flex items-center justify-center gap-2 p-10 text-sm font-semibold text-muted', className)}>
      <IconLoader2 size={20} className="animate-spin" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
