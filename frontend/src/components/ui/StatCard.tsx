import type { ReactNode } from 'react';
import type { Icon } from '@tabler/icons-react';

export interface StatCardProps {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  icon: Icon;
}

/** A single headline number with a label, matching the Canva "metric card". */
export function StatCard({ label, value, detail, icon: IconComponent }: StatCardProps) {
  return (
    <div className="rounded-tile border border-line bg-surface p-5 shadow-card">
      <div className="flex items-start justify-between gap-3">
        <dl>
          <dt className="type-label text-muted">{label}</dt>
          <dd className="mt-2 text-3xl font-extrabold leading-none text-ink">{value}</dd>
        </dl>
        <span className="grid h-[38px] w-[38px] shrink-0 place-items-center rounded-control bg-brand-light text-brand-dark" aria-hidden="true">
          <IconComponent size={20} stroke={2} />
        </span>
      </div>
      {detail && <p className="type-helper mt-4">{detail}</p>}
    </div>
  );
}
