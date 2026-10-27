import type { ReactNode } from 'react';
import Link from 'next/link';
import { cn } from '@/lib/utils/cn';
import type { NavItem } from '@/config/navigation';
import { BrandLogo } from './BrandLogo';

export interface SidebarProps {
  items: NavItem[];
  activeId?: string;
  onNavigate?: () => void;
  footer?: ReactNode;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

const itemBase =
  'flex w-full items-center gap-3 rounded-control px-3.5 py-3 text-sm font-semibold transition-colors';

export function Sidebar({
  items,
  activeId,
  onNavigate,
  footer,
  collapsed = false,
  onToggleCollapse,
}: SidebarProps) {
  return (
    <div className="flex h-full flex-col bg-navy text-on-navy">
      <div
        className={cn(
          'border-b border-white/10 pb-5 pt-6',
          collapsed ? 'px-3' : 'px-5'
        )}
      >
        <Link
          href="/dashboard"
          onClick={onNavigate}
          aria-label="ClinIQ dashboard"
          className={cn(
  'flex rounded-mark bg-white py-2 focus-visible:outline-accent',
  collapsed
    ? 'w-full justify-center px-1'
    : 'w-fit px-3'
)}
        >
          <BrandLogo variant={collapsed ? 'mark' : 'full'} />
        </Link>

        {onToggleCollapse && (
          <button
            type="button"
            onClick={onToggleCollapse}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className={cn(
              'mt-4 flex w-full items-center rounded-control p-2 text-on-navy-muted hover:bg-nav-hover hover:text-white focus-visible:outline-accent',
              collapsed ? 'justify-center' : 'justify-start'
            )}
          >
            <span
  aria-hidden="true"
  className="flex w-5 flex-col items-center gap-1"
>
  <span className="h-0.5 w-5 rounded bg-current" />
  <span className="h-0.5 w-5 rounded bg-current" />
  <span className="h-0.5 w-5 rounded bg-current" />
</span>

            {!collapsed && (
              <span className="ml-2 text-sm">Collapse sidebar</span>
            )}
          </button>
        )}
      </div>

      <nav aria-label="Main" className="flex-1 overflow-y-auto px-3 py-5">
        {!collapsed && (
          <p className="type-eyebrow mb-3 px-3 text-on-navy-muted">
            Workspaces
          </p>
        )}

        <ul className="space-y-1">
          {items.map((item) => {
            const IconComponent = item.icon;

            if (item.status === 'planned') {
              return (
                <li key={item.id}>
                  <span
                    title={collapsed ? item.label : undefined}
                    className={cn(
                      itemBase,
                      'cursor-default text-on-navy-muted',
                      collapsed && 'justify-center px-2'
                    )}
                  >
                    <IconComponent
                      size={18}
                      stroke={2}
                      aria-hidden="true"
                    />

                    {!collapsed && (
                      <>
                        <span className="flex-1">{item.label}</span>
                        <span className="rounded-full border border-white/25 px-2 py-0.5 text-[0.6875rem] font-bold uppercase tracking-wide">
                          Soon
                        </span>
                      </>
                    )}
                  </span>
                </li>
              );
            }

            const active = item.id === activeId;

            return (
              <li key={item.id}>
                <Link
                  href={item.href}
                  onClick={onNavigate}
                  aria-current={active ? 'page' : undefined}
                  title={collapsed ? item.label : undefined}
                  className={cn(
                    itemBase,
                    'focus-visible:outline-accent',
                    collapsed && 'justify-center px-2',
                    active
                      ? 'bg-nav-active text-white shadow-nav-indicator'
                      : 'text-on-navy-muted hover:bg-nav-hover hover:text-white'
                  )}
                >
                  <IconComponent
                    size={18}
                    stroke={2}
                    aria-hidden="true"
                  />

                  {!collapsed && <span>{item.label}</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {footer && !collapsed && (
        <div className="mx-4 mb-5">{footer}</div>
      )}
    </div>
  );
}