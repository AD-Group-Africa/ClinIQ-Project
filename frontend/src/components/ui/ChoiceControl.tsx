import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

interface ChoiceControlProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  type: 'checkbox' | 'radio';
  label: ReactNode;
  description?: ReactNode;
}

/** Shared implementation of Checkbox and Radio: native input + clickable label + optional helper text. */
export const ChoiceControl = forwardRef<HTMLInputElement, ChoiceControlProps>(function ChoiceControl(
  { type, label, description, id, className, ...props },
  ref
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const descriptionId = `${inputId}-description`;
  return (
    <div className={cn('flex min-h-11 items-start gap-3 py-2', className)}>
      <input
        ref={ref}
        id={inputId}
        type={type}
        aria-describedby={description ? descriptionId : undefined}
        className="mt-0.5 h-5 w-5 shrink-0 cursor-pointer accent-brand-dark disabled:cursor-not-allowed"
        {...props}
      />
      <div>
        <label htmlFor={inputId} className="type-label cursor-pointer">
          {label}
        </label>
        {description && (
          <p id={descriptionId} className="type-helper">
            {description}
          </p>
        )}
      </div>
    </div>
  );
});
