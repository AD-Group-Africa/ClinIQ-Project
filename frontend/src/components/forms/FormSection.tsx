import type { ReactNode } from 'react';

export interface FormSectionProps {
  title: string;
  description?: string;
  children: ReactNode;
}

/** A group of related fields. <fieldset> + <legend> also gives radio groups their accessible name. */
export function FormSection({ title, description, children }: FormSectionProps) {
  return (
    <fieldset className="min-w-0">
      <legend className="type-card-title">{title}</legend>
      {description && <p className="type-body mt-1 text-muted">{description}</p>}
      <div className="mt-4 grid gap-5">{children}</div>
    </fieldset>
  );
}
