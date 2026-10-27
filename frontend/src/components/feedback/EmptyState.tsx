import type { ReactNode } from 'react';
import { IconInbox, type Icon } from '@tabler/icons-react';

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: Icon;
  /** Optional next step, for example a Button. */
  action?: ReactNode;
  headingLevel?: 2 | 3 | 4;
}

/** Explains what is missing and, where possible, what to do about it. */
export function EmptyState({ title, description, icon: IconComponent = IconInbox, action, headingLevel = 3 }: EmptyStateProps) {
  const Heading = `h${headingLevel}` as 'h2' | 'h3' | 'h4';
  return (
    <div className="p-12 text-center">
      <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-light text-brand-dark" aria-hidden="true">
        <IconComponent size={24} />
      </div>
      <Heading className="type-card-title mt-4">{title}</Heading>
      {description && <p className="type-body mx-auto mt-2 max-w-sm text-muted">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}
