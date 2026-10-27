'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { login } from '@/lib/auth/authApi';
import { useAuth } from '@/lib/auth/AuthContext';
import type { ApiError } from '@/types/auth';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const router = useRouter();
  const { refresh } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      // login() calls POST /auth/login and, on success, stores the access and refresh tokens via
      // tokenStorage itself — this page never touches storage directly.
      await login(email, password);
    } catch (err) {
      setError((err as ApiError).message || 'Login failed');
      setSubmitting(false);
      return;
    }

    try {
      // tokenStorage has no reactivity, so AuthContext does not know a login just happened until
      // told to check again. refresh() re-runs the same GET /auth/me check used at app boot and
      // updates the shared auth state before we navigate anywhere that depends on it.
      await refresh();
    } catch (err) {
      // login() succeeded and tokens were stored, but confirming the session failed right after
      // (for example the token was rejected immediately, or the network dropped). Tell the user
      // plainly rather than navigating to a dashboard that would just redirect them straight back.
      setError(`Signed in, but we could not confirm your session (${(err as ApiError).message}). Please try again.`);
      setSubmitting(false);
      return;
    }

    router.push('/dashboard');
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <form onSubmit={handleSubmit} className="bg-white p-8 rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6 text-center">ClinIQ Login</h1>
        {error && <div className="text-red-500 mb-4">{error}</div>}
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full p-2 border rounded mb-4"
          required
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full p-2 border rounded mb-6"
          required
        />
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-blue-600 text-white p-2 rounded hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {submitting ? 'Signing in…' : 'Sign In'}
        </button>
      </form>
    </div>
  );
}