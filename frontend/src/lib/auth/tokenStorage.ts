/**
 * The one place that reads or writes ClinIQ's auth tokens.
 *
 * Every other module — the Axios client, authApi, and any future auth context — goes through
 * these functions instead of touching localStorage directly. That is what makes it possible to
 * change *how* tokens are stored later (for example, moving the access token to memory-only)
 * without hunting down every call site.
 *
 * Both tokens live in localStorage for now: the backend requires the refresh token to survive a
 * browser reload (7-day lifetime), and a second storage mechanism just for the access token isn't
 * needed for this foundation step.
 */

const ACCESS_TOKEN_KEY = 'cliniq.accessToken';
const REFRESH_TOKEN_KEY = 'cliniq.refreshToken';

/** Guards every function below during server-side rendering, where there is no localStorage. */
const isBrowser = () => typeof window !== 'undefined';

export function getAccessToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function getRefreshToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

export function clearTokens(): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}
