import type { ReactNode } from 'react';
import { IconAlertCircle, IconAlertTriangle, IconCircleCheck, IconInfoCircle } from '@tabler/icons-react';
import { cn } from '@/lib/utils/cn';
import type { Tone } from '@/components/ui/Badge';

type AlertTone = Exclude<Tone, 'neutral'>;

const toneStyles: Record<AlertTone, { box: string; text: string; icon: typeof IconInfoCircle }> = {
  info: { box: 'bg-info-soft', text: 'text-info', icon: IconInfoCircle },
  success: { box: 'bg-success-soft', text: 'text-success', icon: IconCircleCheck },
  warning: { box: 'bg-warning-soft', text: 'text-warning', icon: IconAlertTriangle },
  danger: { box: 'bg-danger-soft', text: 'text-danger', icon: IconAlertCircle },
};

export interface AlertProps {
  tone?: AlertTone;
  title?: string;
  children: ReactNode;
  className?: string;
}

/** Inline message. The icon and the title carry the meaning too, so colour is never the only cue. */
export function Alert({ tone = 'info', title, children, className }: AlertProps) {
  const { box, text, icon: IconComponent } = toneStyles[tone];
  return (
    <div
      // Errors and warnings interrupt the screen reader; info and success wait politely.
      role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-control border border-line p-4', box, className)}
    >
      <IconComponent size={20} className={cn('mt-0.5 shrink-0', text)} aria-hidden="true" />
      <div className="text-sm">
        {title && <p className={cn('font-bold', text)}>{title}</p>}
        <div className={cn('font-medium text-ink', title && 'mt-0.5')}>{children}</div>
      </div>
    </div>
  );
}
