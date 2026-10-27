import { forwardRef, type TextareaHTMLAttributes } from 'react';
import { controlClasses } from './control-styles';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { invalid, className, rows = 3, ...props },
  ref
) {
  return (
    <textarea ref={ref} rows={rows} aria-invalid={invalid || undefined} className={controlClasses(invalid, `resize-y ${className ?? ''}`)} {...props} />
  );
});
