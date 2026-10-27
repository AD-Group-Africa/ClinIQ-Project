'use client';

import type { ReactElement } from 'react';
import toast, { Toaster } from 'react-hot-toast';
import { IconAlertCircle, IconCircleCheck, IconInfoCircle } from '@tabler/icons-react';

/**
 * Renders the toast area once (AppShell does this). Styling follows the Canva toast:
 * navy background, white text, cyan check icon. Toasts stay for 6s (8s for errors) and pause on hover.
 * react-hot-toast marks each toast role="status" aria-live="polite", so screen readers announce them.
 */
export function ToastProvider() {
  return (
    <Toaster
      position="bottom-right"
      containerStyle={{ bottom: 20, right: 20 }}
      toastOptions={{
        duration: 6000,
        style: {
          background: 'var(--color-navy)',
          color: '#ffffff',
          borderRadius: 'var(--radius-control)',
          padding: '12px 16px',
          fontSize: '14px',
          fontWeight: 600,
          boxShadow: 'var(--shadow-toast)',
          maxWidth: '420px',
        },
      }}
    />
  );
}

function show(message: string, icon: ReactElement, duration?: number) {
  return toast(message, { icon, duration });
}

/** Call these after an action finishes (for example a successful save). Never use for silent failures. */
export const notify = {
  success: (message: string) => show(message, <IconCircleCheck size={20} color="var(--color-accent)" aria-hidden="true" />),
  error: (message: string) => show(message, <IconAlertCircle size={20} color="var(--color-danger-on-dark)" aria-hidden="true" />, 8000),
  info: (message: string) => show(message, <IconInfoCircle size={20} color="var(--color-accent)" aria-hidden="true" />),
};
