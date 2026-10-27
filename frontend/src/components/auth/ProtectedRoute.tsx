'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth/AuthContext';
import { Loading } from '@/components/feedback/Loading';

/**
 * Client-side guard for routes that require a logged-in user.
 *
 * This is a UX convenience, not a security boundary — the backend must independently reject any
 * request from an invalid or missing token. It only decides what the browser renders.
 *
 * Status handling (from AuthContext's session restoration, Step 3):
 *  - 'idle' | 'loading'   → session is still being restored; show a loading state, render nothing.
 *  - 'unauthenticated'    → redirect to /login.
 *  - 'authenticated'      → render the protected children.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === 'unauthenticated') {
      router.replace('/login');
    }
  }, [status, router]);

  if (status === 'authenticated') {
    return <>{children}</>;
  }

  // Covers 'idle', 'loading', and the brief moment 'unauthenticated' is set before the redirect
  // above actually navigates away — never render protected content in any of those states.
  return <Loading label="Checking your session…" />;
}