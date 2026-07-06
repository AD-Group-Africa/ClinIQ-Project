'use client';
import api from '@/lib/api';
import { useState, useEffect } from 'react';

export default function QueueTable() {
  const [appointments, setAppointments] = useState([]);

  useEffect(() => {
    api.get('/appointments/today').then(r => setAppointments(r.data.data || [])).catch(() => {});
  }, []);

  return (
    <table className="w-full">
      <thead><tr><th className="p-2 text-left">Queue</th><th className="p-2 text-left">Patient</th><th className="p-2 text-left">Time</th><th className="p-2 text-left">Status</th></tr></thead>
      <tbody>
        {appointments.map((apt: any, idx: number) => (
          <tr key={apt.id} className="border-t">
            <td className="p-2">{apt.queueNumber || idx + 1}</td>
            <td className="p-2 font-medium">{apt.patientName}</td>
            <td className="p-2">{apt.startTime}</td>
            <td className="p-2">{apt.status}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
