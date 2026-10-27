import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-card border border-line bg-surface shadow-card', className)} {...props} />;
}

interface CardHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** Heading level, so the page outline stays logical. Defaults to h3 (h1 is the page title). */
  headingLevel?: 2 | 3 | 4;
}

export function CardHeader({ title, description, actions, headingLevel = 3 }: CardHeaderProps) {
  const Heading = `h${headingLevel}` as 'h2' | 'h3' | 'h4';
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-6">
      <div>
        <Heading className="type-card-title">{title}</Heading>
        {description && <p className="type-body mt-1 text-muted">{description}</p>}
      </div>
      {actions}
    </div>
  );
}

export function CardBody({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-6', className)} {...props} />;
}
