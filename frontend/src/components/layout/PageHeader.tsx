import type { ReactNode } from 'react';
import { Breadcrumbs, type BreadcrumbItem } from './Breadcrumbs';

export interface PageHeaderProps {
  title: string;
  kicker?: string;
  description?: string;
  breadcrumbs?: BreadcrumbItem[];
  /** Contextual actions on the right (for example a primary Button). */
  actions?: ReactNode;
}

/** Top of every page: the one <h1>, optional breadcrumbs, short description and actions. */
export function PageHeader({ title, kicker, description, breadcrumbs, actions }: PageHeaderProps) {
  return (
    <div className="mb-7">
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} className="mb-4" />}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          {kicker && <p className="type-eyebrow text-brand-dark">{kicker}</p>}
          <h1 className="type-page-title mt-2">{title}</h1>
          {description && <p className="type-body mt-2 max-w-xl text-muted">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
      </div>
    </div>
  );
}
