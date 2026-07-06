'use client';
import { useState } from 'react';
import api from '@/lib/api';

export default function RegisterPage() {
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    email: '',
    password: '',
  });
  const [message, setMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/auth/register-patient', form);
      setMessage('Registration successful! You can now login.');
    } catch (err: any) {
      setMessage(err.response?.data?.error?.message || 'Registration failed');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <form onSubmit={handleSubmit} className="bg-white p-8 rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6 text-center">Patient Registration</h1>
        {message && <div className="text-center mb-4 text-sm">{message}</div>}
        <input type="text" placeholder="First Name" value={form.firstName} onChange={(e) => setForm({...form, firstName: e.target.value})} className="w-full p-2 border rounded mb-4" required />
        <input type="text" placeholder="Last Name" value={form.lastName} onChange={(e) => setForm({...form, lastName: e.target.value})} className="w-full p-2 border rounded mb-4" required />
        <input type="tel" placeholder="Phone (07XXXXXXXX)" value={form.phone} onChange={(e) => setForm({...form, phone: e.target.value})} className="w-full p-2 border rounded mb-4" required />
        <input type="email" placeholder="Email" value={form.email} onChange={(e) => setForm({...form, email: e.target.value})} className="w-full p-2 border rounded mb-4" />
        <input type="password" placeholder="Password" value={form.password} onChange={(e) => setForm({...form, password: e.target.value})} className="w-full p-2 border rounded mb-6" />
        <button type="submit" className="w-full bg-green-600 text-white p-2 rounded hover:bg-green-700">Register</button>
      </form>
    </div>
  );
}
