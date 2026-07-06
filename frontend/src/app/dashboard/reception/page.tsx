'use client';
import { useState, useEffect } from 'react';
import api from '@/lib/api';
import Link from 'next/link';

export default function ReceptionDashboard() {
  const [appointments, setAppointments] = useState([]);
  const [patients, setPatients] = useState([]);
  const [showCreatePatient, setShowCreatePatient] = useState(false);
  const [newPatient, setNewPatient] = useState({ firstName: '', lastName: '', phone: '' });

  useEffect(() => {
    api.get('/appointments/today').then(r => setAppointments(r.data.data || [])).catch(() => {});
    api.get('/patients?size=100').then(r => setPatients(r.data.data?.content || [])).catch(() => {});
  }, []);

  const createPatient = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await api.post('/patients', newPatient);
    setPatients([...patients, res.data.data]);
    setNewPatient({ firstName: '', lastName: '', phone: '' });
    setShowCreatePatient(false);
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-bold">Reception Dashboard</h1>
        <button onClick={() => setShowCreatePatient(!showCreatePatient)} className="bg-blue-600 text-white px-4 py-2 rounded">
          + New Patient
        </button>
      </div>

      {showCreatePatient && (
        <form onSubmit={createPatient} className="bg-white p-4 rounded-lg shadow mb-6 flex gap-4 flex-wrap">
          <input type="text" placeholder="First Name" value={newPatient.firstName} onChange={e => setNewPatient({...newPatient, firstName: e.target.value})} className="border p-2 rounded" required />
          <input type="text" placeholder="Last Name" value={newPatient.lastName} onChange={e => setNewPatient({...newPatient, lastName: e.target.value})} className="border p-2 rounded" required />
          <input type="tel" placeholder="Phone" value={newPatient.phone} onChange={e => setNewPatient({...newPatient, phone: e.target.value})} className="border p-2 rounded" required />
          <button type="submit" className="bg-green-600 text-white px-4 py-2 rounded">Save</button>
        </form>
      )}

      <section className="mb-8">
        <h2 className="text-2xl font-semibold mb-4">Today's Appointments</h2>
        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="p-4 text-left">#</th>
                <th className="p-4 text-left">Patient</th>
                <th className="p-4 text-left">Time</th>
                <th className="p-4 text-left">Status</th>
                <th className="p-4 text-left">Doctor</th>
              </tr>
            </thead>
            <tbody>
              {appointments.map((apt: any, idx: number) => (
                <tr key={apt.id} className="border-t">
                  <td className="p-4">{apt.queueNumber || idx + 1}</td>
                  <td className="p-4 font-medium">{apt.patientName}</td>
                  <td className="p-4">{apt.startTime}</td>
                  <td className="p-4"><span className={`px-2 py-1 rounded text-sm ${apt.status === 'CHECKED_IN' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}`}>{apt.status}</span></td>
                  <td className="p-4">{apt.doctorName || 'Unassigned'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-2xl font-semibold mb-4">Patients</h2>
        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="p-4 text-left">Name</th>
                <th className="p-4 text-left">Phone</th>
                <th className="p-4 text-left">Gender</th>
              </tr>
            </thead>
            <tbody>
              {patients.map((p: any) => (
                <tr key={p.id} className="border-t">
                  <td className="p-4">{p.firstName} {p.lastName}</td>
                  <td className="p-4">{p.phone}</td>
                  <td className="p-4">{p.gender || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
