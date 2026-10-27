import { forwardRef, useId, type InputHTMLAttributes } from 'react';
import { IconSearch } from '@tabler/icons-react';
import { controlClasses } from './control-styles';

export interface SearchFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  /** Accessible name. Visually hidden because the magnifier icon already signals the purpose. */
  label: string;
}

export const SearchField = forwardRef<HTMLInputElement, SearchFieldProps>(function SearchField(
  { label, id, className, ...props },
  ref
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <div className="relative">
      <label htmlFor={inputId} className="sr-only">
        {label}
      </label>
      <IconSearch size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-muted" aria-hidden="true" />
      <input ref={ref} id={inputId} type="search" className={controlClasses(false, `pl-11 ${className ?? ''}`)} {...props} />
    </div>
  );
});
