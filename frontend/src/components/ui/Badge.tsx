import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export type Tone = 'info' | 'success' | 'warning' | 'danger' | 'neutral';

const toneClasses: Record<Tone, string> = {
  info: 'bg-info-soft text-info',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  neutral: 'bg-plain-soft text-plain',
};

export interface BadgeProps {
  tone?: Tone;
  /** Adds a small dot before the text. */
  dot?: boolean;
  className?: string;
  children: ReactNode;
}

/** Small pill for labels and counts. Always contains text, so meaning never depends on colour alone. */
export function Badge({ tone = 'neutral', dot = false, className, children }: BadgeProps) {
  return (
    <span className={cn('inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-xs font-bold', toneClasses[tone], className)}>
      {dot && <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}
