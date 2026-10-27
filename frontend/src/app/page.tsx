import { redirect } from 'next/navigation';

/**
 * Phase 1: there is no login or session check yet, so the root simply opens the dashboard.
 * When authentication is built, decide here between /login and /dashboard from the real session.
 */
export default function HomePage() {
  redirect('/dashboard');
}
