import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { IconLoader2 } from '@tabler/icons-react';
import { cn } from '@/lib/utils/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner, sets aria-busy and blocks clicks so an action cannot be submitted twice. */
  loading?: boolean;
  leadingIcon?: ReactNode;
  fullWidth?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  // Navy text on the sky fill (6+:1). White on sky would be only 2.2:1.
  primary: 'bg-brand text-navy hover:shadow-lift',
  secondary: 'border border-line-input bg-surface text-ink hover:bg-surface-muted',
  ghost: 'text-brand-dark hover:bg-brand-light',
  danger: 'bg-danger text-white hover:shadow-lift',
};

const sizeClasses: Record<ButtonSize, string> = {
  md: 'min-h-11 px-5 py-2.5',
  sm: 'min-h-9 px-3.5 py-2',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading = false, leadingIcon, fullWidth, className, children, disabled, type = 'button', ...props },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-control text-sm font-bold transition',
        'hover:-translate-y-px motion-reduce:hover:translate-y-0',
        'disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:shadow-none',
        variantClasses[variant],
        sizeClasses[size],
        fullWidth && 'w-full',
        className
      )}
      {...props}
    >
      {loading ? <IconLoader2 size={16} className="animate-spin" aria-hidden="true" /> : leadingIcon}
      {children}
    </button>
  );
});
