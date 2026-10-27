import api from '@/lib/api';
import { parseApiError } from '@/lib/apiError';
import type { ApiEnvelope, AuthResponse, TokenResponse, UserProfile } from '@/types/auth';
import { clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from './tokenStorage';

/**
 * The only module that calls ClinIQ's /auth/* endpoints.
 *
 * Every function here throws a typed ApiError (via parseApiError) instead of a raw AxiosError,
 * and every function that receives new tokens stores them through tokenStorage — nothing else in
 * the app should read or write the tokens directly.
 *
 * This step deliberately does NOT wire automatic refresh into the Axios client. If a request gets
 * a 401, the existing interceptor in lib/api.ts still just clears tokens and sends the user to
 * /login — refreshAccessToken() below exists and works, but nothing calls it automatically yet.
 */

export async function login(email: string, password: string): Promise<AuthResponse> {
  try {
    const response = await api.post<ApiEnvelope<AuthResponse>>('/auth/login', { email, password });
    const body = response.data;
    if (!body.success) throw parseApiError({ response: { data: body, status: 200 } });
    setAccessToken(body.data.accessToken);
    setRefreshToken(body.data.refreshToken);
    return body.data;
  } catch (error) {
    throw parseApiError(error);
  }
}

export async function getMe(): Promise<UserProfile> {
  try {
    const response = await api.get<ApiEnvelope<UserProfile>>('/auth/me');
    const body = response.data;
    if (!body.success) throw parseApiError({ response: { data: body, status: 200 } });
    return body.data;
  } catch (error) {
    throw parseApiError(error);
  }
}

/** Exchanges the stored refresh token for a new access + refresh token pair (rotation). */
export async function refreshAccessToken(): Promise<TokenResponse> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw parseApiError(new Error('No refresh token is stored — the user is not logged in.'));
  }
  try {
    const response = await api.post<ApiEnvelope<TokenResponse>>('/auth/refresh', { refreshToken });
    const body = response.data;
    if (!body.success) throw parseApiError({ response: { data: body, status: 200 } });
    setAccessToken(body.data.accessToken);
    setRefreshToken(body.data.refreshToken);
    return body.data;
  } catch (error) {
    throw parseApiError(error);
  }
}

/** Calls the server to revoke the current session, then always clears local tokens regardless. */
export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout');
  } finally {
    clearTokens();
  }
}
