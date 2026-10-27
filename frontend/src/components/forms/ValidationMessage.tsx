import type { ReactNode } from 'react';
import { IconAlertCircle } from '@tabler/icons-react';

export interface ValidationMessageProps {
  id?: string;
  children: ReactNode;
}

/** Error text under a field. The icon and the hidden "Error:" prefix keep it clear without colour. */
export function ValidationMessage({ id, children }: ValidationMessageProps) {
  return (
    <p id={id} className="mt-1.5 flex items-start gap-1.5 text-xs font-semibold text-danger">
      <IconAlertCircle size={16} className="mt-px shrink-0" aria-hidden="true" />
      <span>
        <span className="sr-only">Error: </span>
        {children}
      </span>
    </p>
  );
}
