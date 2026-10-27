'use client';

import { useEffect } from 'react';
import { Drawer } from '@/components/ui/Drawer';
import type { NavItem } from '@/config/navigation';
import { Sidebar } from './Sidebar';

export interface MobileNavigationProps {
  open: boolean;
  onClose: () => void;
  items: NavItem[];
  activeId?: string;
}

/** The sidebar as a slide-in drawer for screens narrower than 851px. */
export function MobileNavigation({ open, onClose, items, activeId }: MobileNavigationProps) {
  // If the window grows to desktop width while the drawer is open, close it so the page is not left inert.
  useEffect(() => {
    const query = window.matchMedia('(min-width: 851px)');
    const handleChange = (event: MediaQueryListEvent) => {
      if (event.matches) onClose();
    };
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, [onClose]);

  return (
    <Drawer open={open} onClose={onClose} label="Navigation menu" side="left" tone="dark">
      <Sidebar items={items} activeId={activeId} onNavigate={onClose} />
    </Drawer>
  );
}
