'use client';
import { useState, useEffect } from "react";
import api from "@/lib/api";
import toast from "react-hot-toast";

export default function DoctorPage() {
  const [appointments, setAppointments] = useState<any[]>([]);
  const [selected, setSelected] = useState<any>(null);
  const [consultation, setConsultation] = useState<any>(null);

  useEffect(() => {
    api.get("/appointments/today").then(r => setAppointments(r.data.data || [])).catch(() => {});
  }, []);

  const startConsultation = async (appointment: any) => {
    const res = await api.post("/consultations/start", { appointmentId: appointment.id, patientId: appointment.patientId });
    setSelected(appointment);
    setConsultation(res.data.data);
  };

  const update = async (field: string, value: string) => {
    const res = await api.patch(`/consultations/${consultation.id}`, { [field]: value });
    setConsultation(res.data.data);
  };

  const complete = async () => {
    await api.post(`/consultations/${consultation.id}/complete`);
    toast.success("Consultation completed");
    setSelected(null);
    setConsultation(null);
  };

  return (
    <div>
      <h2 className="text-3xl font-bold mb-6">Doctor Portal</h2>
      {!selected ? (
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-xl font-semibold mb-4">Today's Patients</h3>
          <table className="w-full">
            <thead><tr><th className="p-2 text-left">Queue</th><th className="p-2 text-left">Patient</th><th className="p-2 text-left">Status</th><th className="p-2"></th></tr></thead>
            <tbody>
              {appointments.map((apt, idx) => (
                <tr key={apt.id} className="border-t">
                  <td className="p-2">{apt.queueNumber || idx + 1}</td>
                  <td className="p-2 font-medium">{apt.patientName}</td>
                  <td className="p-2">{apt.status}</td>
                  <td className="p-2"><button onClick={() => startConsultation(apt)} className="bg-clinic-600 text-white px-3 py-1 rounded">Start</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-xl font-semibold mb-4">Consultation: {selected.patientName}</h3>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block font-medium mb-1">Chief Complaint</label><textarea value={consultation.chiefComplaint || ""} onChange={(e) => update("chiefComplaint", e.target.value)} className="w-full border p-2 rounded" rows={3} /></div>
            <div><label className="block font-medium mb-1">Examination</label><textarea value={consultation.examination || ""} onChange={(e) => update("examination", e.target.value)} className="w-full border p-2 rounded" rows={3} /></div>
            <div><label className="block font-medium mb-1">Diagnosis</label><input value={consultation.diagnosis || ""} onChange={(e) => update("diagnosis", e.target.value)} className="w-full border p-2 rounded" /></div>
            <div><label className="block font-medium mb-1">Treatment Plan</label><textarea value={consultation.treatmentPlan || ""} onChange={(e) => update("treatmentPlan", e.target.value)} className="w-full border p-2 rounded" rows={3} /></div>
            <div><label className="block font-medium mb-1">Notes</label><textarea value={consultation.notes || ""} onChange={(e) => update("notes", e.target.value)} className="w-full border p-2 rounded" rows={3} /></div>
          </div>
          <div className="mt-6 flex gap-4">
            <button onClick={complete} className="bg-green-600 text-white px-6 py-2 rounded">Complete</button>
            <button onClick={() => { setSelected(null); setConsultation(null); }} className="bg-gray-500 text-white px-6 py-2 rounded">Back</button>
          </div>
        </div>
      )}
    </div>
  );
}
