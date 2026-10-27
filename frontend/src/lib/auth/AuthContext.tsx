'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { getMe } from './authApi';
import { getAccessToken } from './tokenStorage';
import type { AuthState } from '@/types/auth';

const initialState: AuthState = { status: 'idle', user: null, error: null };

/**
 * What useAuth() returns: the restored AuthState plus `refresh`, a way to re-run the same check
 * on demand. `tokenStorage` has no reactivity — writing a token there does not, by itself, update
 * this context — so `refresh()` exists specifically for callers (the login page) that write new
 * tokens and need the context to catch up before routing anywhere that depends on it.
 * `refresh` is UI plumbing, not a backend concept, so it lives here rather than in types/auth.ts.
 */
export interface AuthContextValue extends AuthState {
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Restores the session on first load: if a stored access token exists, it is verified against
 * GET /auth/me and the resulting user is held in state; if not (or if it's invalid), the app is
 * marked unauthenticated. Nothing here redirects or checks routes — that is ProtectedRoute's job.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(initialState);
  const mountedRef = useRef(true);

  /**
   * Runs the "am I logged in" check: no token → unauthenticated; token present → verify it via
   * GET /auth/me. Always updates context state. On failure it ALSO rethrows the typed ApiError,
   * so a caller that needs to react immediately (rather than just observing the next render) can.
   * The initial-mount effect below deliberately swallows that rethrow; a caller like the login
   * page should not.
   */
  const refresh = useCallback(async () => {
    const accessToken = getAccessToken();
    if (!accessToken) {
      if (mountedRef.current) setState({ status: 'unauthenticated', user: null, error: null });
      return;
    }

    if (mountedRef.current) setState({ status: 'loading', user: null, error: null });
    try {
      const user = await getMe();
      if (mountedRef.current) setState({ status: 'authenticated', user, error: null });
    } catch (error) {
      // getMe() always throws a typed ApiError (see src/lib/apiError.ts) — the stored token
      // was missing, expired, or otherwise rejected by the server.
      const apiError = error as AuthState['error'];
      if (mountedRef.current) setState({ status: 'unauthenticated', user: null, error: apiError });
      throw apiError;
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    // Boot-time restoration: a failure here just means "not logged in" — there is no form or
    // action to show an error against, so the rejection is intentionally not surfaced further.
    refresh().catch(() => {});
    return () => {
      mountedRef.current = false;
    };
  }, [refresh]);

  return <AuthContext.Provider value={{ ...state, refresh }}>{children}</AuthContext.Provider>;
}

/** Reads the current AuthState plus refresh(). Must be used inside <AuthProvider>. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}