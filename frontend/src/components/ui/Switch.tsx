import { useId, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface SwitchProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
  className?: string;
}

/** On/off setting that takes effect immediately. Use a Checkbox for values submitted with a form. */
export function Switch({ checked, onCheckedChange, label, description, disabled, className }: SwitchProps) {
  const id = useId();
  return (
    <div className={cn('flex min-h-11 items-center justify-between gap-4 py-1', className)}>
      <div>
        <span id={`${id}-label`} className="type-label block">
          {label}
        </span>
        {description && (
          <span id={`${id}-description`} className="type-helper block">
            {description}
          </span>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={`${id}-label`}
        aria-describedby={description ? `${id}-description` : undefined}
        disabled={disabled}
        onClick={() => onCheckedChange(!checked)}
        className={cn(
          'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 border-transparent transition-colors',
          'disabled:cursor-not-allowed disabled:opacity-60',
          checked ? 'bg-brand-dark' : 'bg-line-input'
        )}
      >
        {/* The thumb position (left/right) is a second cue besides colour. */}
        <span
          aria-hidden="true"
          className={cn('h-5 w-5 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-6' : 'translate-x-0')}
        />
      </button>
    </div>
  );
}
