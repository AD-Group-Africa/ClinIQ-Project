
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import api from '@/lib/api';

export default function RegisterPage() {
  const router = useRouter();

  const [form, setForm] = useState({
    clinicName: '',
    clinicSlug: '',
    clinicPhone: '',
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    phone: '',
  });

  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setSubmitting(true);

    try {
      await api.post('/auth/register', {
        ...form,
        clinicSlug: form.clinicSlug.trim().toLowerCase(),
        clinicPhone: form.clinicPhone || null,
        phone: form.phone || null,
      });

      setMessage('Registration successful! Redirecting to login...');

      setTimeout(() => {
        router.push('/login');
      }, 1200);
    } catch (err: any) {
      setMessage(
        err.response?.data?.error?.message ||
        'Registration failed. Please check your details and try again.'
      );
    } finally {
      setSubmitting(false);
    }
  };

  const updateField = (field: keyof typeof form, value: string) => {
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 p-4">
      <form
        onSubmit={handleSubmit}
        className="bg-white p-8 rounded-lg shadow-md w-full max-w-md"
      >
        <h1 className="text-2xl font-bold mb-2 text-center">
          Register Your Clinic
        </h1>

        <p className="text-gray-500 text-sm text-center mb-6">
          Create your clinic administrator account.
        </p>

        {message && (
          <div role="status" className="text-center mb-4 text-sm">
            {message}
          </div>
        )}

        <label className="block text-sm mb-1" htmlFor="clinicName">
          Clinic Name *
        </label>
        <input
          id="clinicName"
          value={form.clinicName}
          onChange={(e) => updateField('clinicName', e.target.value)}
          placeholder="e.g. Green Valley Clinic"
          className="w-full p-2 border rounded mb-4"
          required
        />

        <label className="block text-sm mb-1" htmlFor="clinicSlug">
          Clinic URL Name *
        </label>
        <input
          id="clinicSlug"
          value={form.clinicSlug}
          onChange={(e) =>
            updateField(
              'clinicSlug',
              e.target.value.toLowerCase().trim().replace(/\s+/g, '-')
            )
          }
          placeholder="e.g. green-valley-clinic"
          minLength={3}
          maxLength={60}
          className="w-full p-2 border rounded mb-4"
          required
        />

        <label className="block text-sm mb-1" htmlFor="clinicPhone">
          Clinic Phone (optional)
        </label>
        <input
          id="clinicPhone"
          type="tel"
          value={form.clinicPhone}
          onChange={(e) => updateField('clinicPhone', e.target.value)}
          placeholder="Clinic contact number"
          className="w-full p-2 border rounded mb-4"
        />

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm mb-1" htmlFor="firstName">
              First Name *
            </label>
            <input
              id="firstName"
              value={form.firstName}
              onChange={(e) => updateField('firstName', e.target.value)}
              className="w-full p-2 border rounded mb-4"
              required
            />
          </div>

          <div>
            <label className="block text-sm mb-1" htmlFor="lastName">
              Last Name *
            </label>
            <input
              id="lastName"
              value={form.lastName}
              onChange={(e) => updateField('lastName', e.target.value)}
              className="w-full p-2 border rounded mb-4"
              required
            />
          </div>
        </div>

        <label className="block text-sm mb-1" htmlFor="email">
          Email *
        </label>
        <input
          id="email"
          type="email"
          value={form.email}
          onChange={(e) => updateField('email', e.target.value)}
          className="w-full p-2 border rounded mb-4"
          required
        />

        <label className="block text-sm mb-1" htmlFor="phone">
          Administrator Phone (optional)
        </label>
        <input
          id="phone"
          type="tel"
          value={form.phone}
          onChange={(e) => updateField('phone', e.target.value)}
          placeholder="07XXXXXXXX"
          className="w-full p-2 border rounded mb-4"
        />

        <label className="block text-sm mb-1" htmlFor="password">
          Password *
        </label>
        <input
          id="password"
          type="password"
          value={form.password}
          onChange={(e) => updateField('password', e.target.value)}
          minLength={8}
          className="w-full p-2 border rounded mb-6"
          required
        />

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-green-600 text-white p-2 rounded hover:bg-green-700 disabled:opacity-60"
        >
          {submitting ? 'Registering...' : 'Register Clinic'}
        </button>

        <p className="text-center text-sm text-gray-600 mt-4">
          Already registered?{' '}
          <a href="/login" className="text-blue-600 hover:underline">
            Sign in
          </a>
        </p>
      </form>
    </div>
  );
}
