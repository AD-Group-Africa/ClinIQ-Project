import { useId, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';
import { ValidationMessage } from './ValidationMessage';

/** Props to spread onto the control (Input, Select, Textarea…) so label, hint and error are all linked. */
export interface FieldControlProps {
  id: string;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
}

export interface FormFieldProps {
  label: string;
  hint?: string;
  /** Error message. When present the control is marked invalid. */
  error?: string;
  required?: boolean;
  className?: string;
  children: (control: FieldControlProps) => ReactNode;
}

/**
 * Label + control + hint + error, wired together for accessibility.
 *
 *   <FormField label="Full name" required error={errors.name}>
 *     {(control) => <Input {...control} name="name" />}
 *   </FormField>
 */
export function FormField({ label, hint, error, required, className, children }: FormFieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn(className)}>
      <label htmlFor={id} className="type-label mb-2 block">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-0.5 text-danger">
            *
          </span>
        )}
      </label>
      {children({ id, required, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
      {hint && (
        <p id={hintId} className="type-helper mt-1.5">
          {hint}
        </p>
      )}
      {error && <ValidationMessage id={errorId}>{error}</ValidationMessage>}
    </div>
  );
}
