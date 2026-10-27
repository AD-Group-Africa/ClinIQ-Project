import { forwardRef, type InputHTMLAttributes } from 'react';
import { controlClasses } from './control-styles';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ invalid, className, ...props }, ref) {
  return <input ref={ref} aria-invalid={invalid || undefined} className={controlClasses(invalid, className)} {...props} />;
});
