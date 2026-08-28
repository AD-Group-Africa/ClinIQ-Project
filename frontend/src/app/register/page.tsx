'use client';
import Link from "next/link";

export default function RegisterPage() {
  return (
    <div className="w-full min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-white">
      <div className="bg-white p-8 rounded-xl shadow-lg w-full max-w-md text-center">
        <h1 className="text-2xl font-bold mb-4 text-clinic-600">Pre-Registration</h1>
        <p className="text-gray-600 mb-6">
          Thank you for choosing ClinIQ. Please visit the clinic reception to complete your registration and book your first appointment.
        </p>
        <Link href="/login" className="text-clinic-600 hover:underline">Back to login</Link>
      </div>
    </div>
  );
}
