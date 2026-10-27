'use client';

import { useId, type ReactNode } from 'react';
import { IconX } from '@tabler/icons-react';
import { useDialog } from '@/hooks/useDialog';
import { cn } from '@/lib/utils/cn';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children?: ReactNode;
  /** Buttons row shown at the bottom. */
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  /** Set to false while an action is running so Escape and backdrop clicks cannot close the dialog. */
  dismissible?: boolean;
}

const sizeClasses = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl' } as const;

export function Modal({ open, onClose, title, description, children, footer, size = 'md', dismissible = true }: ModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  const { ref, dialogProps } = useDialog(open, onClose, dismissible);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      {...dialogProps}
      className={cn(
        'm-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] overflow-y-auto rounded-card border border-line bg-surface p-0 text-ink shadow-card',
        'backdrop:bg-overlay open:animate-dialog-in',
        sizeClasses[size]
      )}
    >
      <div className="flex items-start justify-between gap-4 p-6 pb-2">
        <div>
          <h2 id={titleId} className="type-section-title">
            {title}
          </h2>
          {description && (
            <p id={descriptionId} className="type-body mt-1 text-muted">
              {description}
            </p>
          )}
        </div>
        {dismissible && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="-mr-2 -mt-1 grid h-11 w-11 shrink-0 place-items-center rounded-control text-muted hover:bg-surface-muted hover:text-ink"
          >
            <IconX size={20} aria-hidden="true" />
          </button>
        )}
      </div>
      {children && <div className="px-6 py-4">{children}</div>}
      {footer && <div className="flex flex-wrap justify-end gap-3 border-t border-line p-6">{footer}</div>}
    </dialog>
  );
}
