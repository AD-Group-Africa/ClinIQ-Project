import type { ReactNode } from 'react';
import { IconMenu2 } from '@tabler/icons-react';

export interface TopbarProps {
  /** Small uppercase line above the title (for example the product or clinic context). */
  kicker: string;
  /** Current workspace name. Rendered as text, not a heading: the page's <h1> lives in PageHeader. */
  title: string;
  onMenuClick: () => void;
  menuOpen: boolean;
  /**
   * Optional photo behind the bar (the Canva design has one, but that image file is not part of this repo).
   * Leave undefined to get the plain navy bar.
   */
  backdropSrc?: string;
  /** Right-hand slot, reserved for notifications, clinic status and the user menu once those have real data. */
  children?: ReactNode;
}

export function Topbar({ kicker, title, onMenuClick, menuOpen, backdropSrc, children }: TopbarProps) {
  return (
    <header className="relative overflow-hidden border-b border-navy-line bg-navy">
      {backdropSrc && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={backdropSrc} alt="" className="absolute inset-0 h-full w-full object-cover opacity-40" />
      )}
      <div aria-hidden="true" className="absolute inset-0 bg-topbar-overlay" />
      <div className="relative flex min-h-28 items-center justify-between gap-5 px-5 py-5 shell:min-h-[126px] shell:px-[34px] shell:py-6">
        <div className="flex items-center gap-4">
          {/* Mobile-only button that opens the navigation drawer. */}
          <button
            type="button"
            onClick={onMenuClick}
            aria-label="Open navigation menu"
            aria-haspopup="dialog"
            aria-expanded={menuOpen}
            className="grid h-11 w-11 shrink-0 place-items-center rounded-control border border-white/20 bg-white/10 text-white focus-visible:outline-accent shell:hidden"
          >
            <IconMenu2 size={20} aria-hidden="true" />
          </button>
          <div>
            <p className="type-eyebrow text-accent">{kicker}</p>
            <p className="mt-2 font-heading text-2xl font-bold leading-tight text-white shell:text-[1.75rem]">{title}</p>
          </div>
        </div>
        {children && <div className="hidden items-center gap-3 xs:flex">{children}</div>}
      </div>
    </header>
  );
}
