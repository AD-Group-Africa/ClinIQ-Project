import { isAxiosError } from 'axios';
import type { ApiError } from '@/types/auth';

/**
 * Turns whatever Axios (or anything else) throws into a typed ApiError, so callers never touch a
 * raw AxiosError or guess at its shape. Use it from a catch block:
 *
 *   try {
 *     await api.post('/auth/login', body);
 *   } catch (error) {
 *     throw parseApiError(error);
 *   }
 *
 * The three codes below — NETWORK_ERROR, UNEXPECTED_RESPONSE, UNKNOWN_ERROR — are NOT part of the
 * backend contract. They only appear when the backend could not be reached at all, or something
 * outside the documented contract went wrong (a malformed response, a non-Axios exception). Every
 * real backend failure comes back as one of the eight documented codes in src/types/auth.ts.
 */
export function parseApiError(error: unknown): ApiError {
  const timestamp = new Date().toISOString();

  if (isAxiosError(error)) {
    const body = error.response?.data as { error?: ApiError } | undefined;
    if (body?.error && typeof body.error.code === 'string' && typeof body.error.message === 'string') {
      return body.error;
    }
    if (!error.response) {
      return {
        code: 'NETWORK_ERROR',
        message: 'Could not reach the ClinIQ server. Check your connection and try again.',
        details: null,
        timestamp,
      };
    }
    return {
      code: 'UNEXPECTED_RESPONSE',
      message: `The server responded with an unexpected error (status ${error.response.status}).`,
      details: null,
      timestamp,
    };
  }

  if (error instanceof Error) {
    return { code: 'UNKNOWN_ERROR', message: error.message, details: null, timestamp };
  }

  return { code: 'UNKNOWN_ERROR', message: 'An unexpected error occurred.', details: null, timestamp };
}
