'use client';
import { useState, useEffect } from 'react';
import api from '@/lib/api';

export default function DoctorDashboard() {
  const [appointments, setAppointments] = useState([]);
  const [selectedAppointment, setSelectedAppointment] = useState<any>(null);
  const [consultation, setConsultation] = useState<any>(null);

  useEffect(() => {
    api.get('/appointments/today').then(r => setAppointments(r.data.data || [])).catch(() => {});
  }, []);

  const startConsultation = async (appointment: any) => {
    const res = await api.post('/consultations/start', {
      appointmentId: appointment.id,
      patientId: appointment.patientId,
    });
    setSelectedAppointment(appointment);
    setConsultation(res.data.data);
  };

  const updateConsultation = async (field: string, value: string) => {
    const res = await api.patch(`/consultations/${consultation.id}`, { [field]: value });
    setConsultation(res.data.data);
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-3xl font-bold mb-8">Doctor Portal</h1>
      {!selectedAppointment ? (
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">Today's Patients</h2>
          <table className="w-full">
            <thead><tr><th className="p-2 text-left">Queue</th><th className="p-2 text-left">Patient</th><th className="p-2 text-left">Status</th><th className="p-2"></th></tr></thead>
            <tbody>
              {appointments.map((apt: any, idx: number) => (
                <tr key={apt.id} className="border-t">
                  <td className="p-2">{apt.queueNumber || idx + 1}</td>
                  <td className="p-2 font-medium">{apt.patientName}</td>
                  <td className="p-2">{apt.status}</td>
                  <td className="p-2"><button onClick={() => startConsultation(apt)} className="bg-blue-600 text-white px-3 py-1 rounded">Start</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">Consultation: {selectedAppointment.patientName}</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block font-medium mb-1">Chief Complaint</label>
              <textarea value={consultation.chiefComplaint || ''} onChange={(e) => updateConsultation('chiefComplaint', e.target.value)} className="w-full border p-2 rounded" rows={3} />
            </div>
            <div>
              <label className="block font-medium mb-1">Examination</label>
              <textarea value={consultation.examination || ''} onChange={(e) => updateConsultation('examination', e.target.value)} className="w-full border p-2 rounded" rows={3} />
            </div>
            <div>
              <label className="block font-medium mb-1">Diagnosis</label>
              <input value={consultation.diagnosis || ''} onChange={(e) => updateConsultation('diagnosis', e.target.value)} className="w-full border p-2 rounded" />
            </div>
            <div>
              <label className="block font-medium mb-1">Treatment Plan</label>
              <textarea value={consultation.treatmentPlan || ''} onChange={(e) => updateConsultation('treatmentPlan', e.target.value)} className="w-full border p-2 rounded" rows={3} />
            </div>
            <div>
              <label className="block font-medium mb-1">Notes</label>
              <textarea value={consultation.notes || ''} onChange={(e) => updateConsultation('notes', e.target.value)} className="w-full border p-2 rounded" rows={3} />
            </div>
          </div>
          <div className="mt-6 flex gap-4">
            <button onClick={() => api.post(`/consultations/${consultation.id}/complete`)} className="bg-green-600 text-white px-6 py-2 rounded">Complete Consultation</button>
            <button onClick={() => setSelectedAppointment(null)} className="bg-gray-500 text-white px-6 py-2 rounded">Back</button>
          </div>
        </div>
      )}
    </div>
  );
}
