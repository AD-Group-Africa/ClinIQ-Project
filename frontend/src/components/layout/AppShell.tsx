
'use client';

import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { ToastProvider } from '@/components/feedback/Toast';
import { findActiveNavItem, navigationItems } from '@/config/navigation';
import { MobileNavigation } from './MobileNavigation';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const closeMenu = useCallback(() => setMenuOpen(false), []);
  const navItems = useMemo(() => navigationItems, []);
  const active = findActiveNavItem(navItems, pathname);

  return (
    <div className="min-h-dvh bg-background shell:flex">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-surface focus:px-4 focus:py-3 focus:text-sm focus:font-bold focus:text-ink focus:shadow-card"
      >
        Skip to main content
      </a>

      <div
        className={`sticky top-0 hidden h-dvh shrink-0 border-r border-navy-line transition-[width] duration-200 shell:block ${
          sidebarCollapsed ? 'w-[84px]' : 'w-[264px]'
        }`}
      >
        <Sidebar
          items={navItems}
          activeId={active?.id}
          collapsed={sidebarCollapsed}
          onToggleCollapse={() => setSidebarCollapsed((current) => !current)}
        />
      </div>

      <div className="min-w-0 flex-1">
        <Topbar
          kicker="ClinIQ workspace"
          title={active?.label ?? 'ClinIQ'}
          onMenuClick={() => setMenuOpen(true)}
          menuOpen={menuOpen}
          backdropSrc="/brand/topbar-backdrop.jpg"
        />

        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-[1440px] px-4 pb-9 pt-[22px] outline-none shell:p-8"
        >
          {children}
        </main>
      </div>

      <MobileNavigation
        open={menuOpen}
        onClose={closeMenu}
        items={navItems}
        activeId={active?.id}
      />

      <ToastProvider />
    </div>
  );
}
