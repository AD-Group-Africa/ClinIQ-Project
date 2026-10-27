'use client';

import type { ReactNode } from 'react';
import { IconX } from '@tabler/icons-react';
import { useDialog } from '@/hooks/useDialog';
import { cn } from '@/lib/utils/cn';

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  /** Accessible name of the drawer (there is no visible title). */
  label: string;
  side?: 'left' | 'right';
  /** "dark" uses the navy sidebar colours (used for the mobile navigation). */
  tone?: 'light' | 'dark';
  children: ReactNode;
}

/** Side panel built on the same native <dialog> as Modal, so it traps focus and closes on Escape. */
export function Drawer({ open, onClose, label, side = 'right', tone = 'light', children }: DrawerProps) {
  const { ref, dialogProps } = useDialog(open, onClose);
  const dark = tone === 'dark';

  return (
    <dialog
      ref={ref}
      aria-label={label}
      {...dialogProps}
      className={cn(
        'fixed inset-y-0 m-0 h-dvh max-h-none w-72 max-w-[calc(100%-3rem)] overflow-y-auto p-0 shadow-drawer',
        'backdrop:bg-overlay',
        side === 'left' ? 'left-0 right-auto open:animate-drawer-in-left' : 'left-auto right-0 open:animate-drawer-in-right',
        dark ? 'bg-navy text-on-navy' : 'bg-surface text-ink'
      )}
    >
      <button
        type="button"
        onClick={onClose}
        aria-label={`Close ${label.toLowerCase()}`}
        data-autofocus
        className={cn(
          'absolute right-3 top-3 z-10 grid h-11 w-11 place-items-center rounded-control',
          dark ? 'text-on-navy hover:bg-nav-hover focus-visible:outline-accent' : 'text-muted hover:bg-surface-muted'
        )}
      >
        <IconX size={20} aria-hidden="true" />
      </button>
      {children}
    </dialog>
  );
}
