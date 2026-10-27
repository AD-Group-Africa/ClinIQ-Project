import {
  IconHeartbeat,
  IconFlask,
  IconLayoutDashboard,
  IconPill,
  IconShieldCheck,
  IconStethoscope,
  IconWallet,
  IconUsers,
  type Icon,
  IconCalendarEvent,
} from '@tabler/icons-react';

/**
 * Navigation structure for the ClinIQ shell.
 *
 * - The labels and icons mirror the sidebar in the Canva reception mockup.
 * - `status: 'planned'` items are shown but not linked, because their screens (and the API contracts
 *   behind them) do not exist yet. They become `'available'` with an `href` when the module ships.
 * - `permission` is RESERVED for role/permission-based navigation. It is deliberately unused: the
 *   backend permission model is not confirmed yet, so no permission names are invented here.
 *   When RBAC lands, filter this list in one place (AppShell) before passing it to the Sidebar.
 */
interface NavItemBase {
  id: string;
  label: string;
  icon: Icon;
  permission?: string;
}

export type NavItem =
  | (NavItemBase & { status: 'available'; href: string })
  | (NavItemBase & { status: 'planned' });

export const navigationItems: NavItem[] = [
  { id: 'overview', label: 'Overview', icon: IconLayoutDashboard, status: 'available', href: '/dashboard' },
  { id: 'patients',label: 'Patients',icon: IconUsers,status: 'available', href: '/dashboard/patients',},
  {id: 'appointments',label: 'Appointments', icon: IconCalendarEvent,status: 'available',href: '/dashboard/appointments',},
  { id: 'doctor', label: 'Doctor', icon: IconStethoscope, status: 'planned' },
  { id: 'nursing', label: 'Nursing', icon: IconHeartbeat, status: 'planned' },
  { id: 'pharmacy', label: 'Pharmacy', icon: IconPill, status: 'planned' },
  { id: 'laboratory', label: 'Laboratory', icon: IconFlask, status: 'planned' },
  { id: 'billing', label: 'Billing', icon: IconWallet, status: 'planned' },
  { id: 'administration', label: 'Administration', icon: IconShieldCheck, status: 'planned' },
];

/** Returns the available item whose href is the longest match for the current path. */
export function findActiveNavItem(items: NavItem[], pathname: string): NavItem | undefined {
  let best: NavItem | undefined;
  let bestLength = -1;
  for (const item of items) {
    if (item.status !== 'available') continue;
    const matches = pathname === item.href || pathname.startsWith(`${item.href}/`);
    if (matches && item.href.length > bestLength) {
      best = item;
      bestLength = item.href.length;
    }
  }
  return best;
}
