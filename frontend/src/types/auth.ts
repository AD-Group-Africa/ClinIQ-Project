/**
 * Auth-related types for ClinIQ, matching the confirmed Phase 2 backend contract exactly.
 * Fields here are only what the backend has explicitly documented — nothing is guessed.
 */

/** The exact nine role strings the backend returns. PATIENT exists but has no login flow yet. */
export type UserRole =
  | 'SUPER_ADMIN'
  | 'CLINIC_ADMIN'
  | 'DOCTOR'
  | 'NURSE'
  | 'RECEPTIONIST'
  | 'FINANCE'
  | 'PATIENT'
  | 'LAB_TECHNICIAN'
  | 'PHARMACIST';

/** The user object returned by both /auth/login and /auth/me. */
export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  clinicId: string;
  avatarUrl: string | null;
}

/** POST /auth/login request body. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** The `data` payload of a successful POST /auth/login response. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserProfile;
}

/** The `data` payload of a successful POST /auth/refresh response — new tokens only, no user. */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

/**
 * The eight error codes the backend documents today. Typed as a union for editor autocomplete,
 * but ApiError.code below also accepts any other string — "Main error codes" in the contract
 * implies this list may not be exhaustive, and a forward-compatible parser should not reject an
 * unrecognised code outright.
 */
export type KnownApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'BAD_REQUEST'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'TOO_MANY_REQUESTS'
  | 'INTERNAL_ERROR';

/**
 * Matches the backend's `error` object exactly (see the confirmed error envelope). `details` is
 * typed as a field→message map because that is the one shape the contract documents ("Validation
 * errors use error.details as field → message data"); for non-validation errors the backend sends
 * `null`. No field here is invented beyond what the contract states.
 */
export interface ApiError {
  code: KnownApiErrorCode | string;
  message: string;
  details: Record<string, string> | null;
  timestamp: string;
}

/**
 * The `{success, data, error, meta}` wrapper every ClinIQ endpoint uses. This lives here rather
 * than in its own file because, for now, auth is the only code that needs to type it — if other
 * (non-auth) API modules need it later, move it to a shared types/api.ts at that point.
 */
export type ApiEnvelope<T> =
  | { success: true; data: T; error: null; meta: unknown | null }
  | { success: false; data: null; error: ApiError; meta: unknown | null };

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated' | 'error';

/**
 * Shape for wherever auth state ends up living once a provider/context is built — a later step.
 * Declared now, but nothing in this step constructs or reads an AuthState yet.
 */
export interface AuthState {
  status: AuthStatus;
  user: UserProfile | null;
  error: ApiError | null;
}
