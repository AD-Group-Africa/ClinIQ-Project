import { forwardRef, type SelectHTMLAttributes } from 'react';
import { IconChevronDown } from '@tabler/icons-react';
import { controlClasses } from './control-styles';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

/** Native <select> (best keyboard and screen-reader support) with the shared ClinIQ control styling. */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select({ invalid, className, children, ...props }, ref) {
  return (
    <div className="relative">
      <select ref={ref} aria-invalid={invalid || undefined} className={controlClasses(invalid, `appearance-none pr-11 ${className ?? ''}`)} {...props}>
        {children}
      </select>
      <IconChevronDown size={18} className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-muted" aria-hidden="true" />
    </div>
  );
});
