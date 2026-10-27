import axios from 'axios';
import { clearTokens, getAccessToken } from '@/lib/auth/tokenStorage';

/**
 * ClinIQ's shared Axios instance.
 *
 * Auth (confirmed Phase 2 contract — Bearer JWT only, no cookies): every request attaches
 * `Authorization: Bearer <accessToken>` when a token is stored. `withCredentials` is never set,
 * and no `X-Clinic-ID` header is added — clinic context comes from the JWT itself.
 *
 * The 401 handling below is UNCHANGED from before, other than reading/clearing the token through
 * tokenStorage instead of a raw localStorage key: on any 401 it clears the tokens and sends the
 * user to /login. This is naive and temporary — it does not attempt a refresh first, and it will
 * also fire for a failed login attempt's own 401 (a pre-existing issue, not introduced here).
 * Both will be replaced by a proper refresh-and-retry flow in a later step.
 */
const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const accessToken = getAccessToken();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearTokens();
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
