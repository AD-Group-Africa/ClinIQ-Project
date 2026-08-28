'use client';
import { useState, useEffect } from "react";
import api from "@/lib/api";
import toast from "react-hot-toast";
import { Patient, Appointment } from "@/types";

export default function ReceptionPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [showRegister, setShowRegister] = useState(false);
  const [showBooking, setShowBooking] = useState(false);
  const [form, setForm] = useState({ firstName: "", lastName: "", phone: "" });

  useEffect(() => {
    fetchAppointments();
    fetchPatients();
  }, []);

  const fetchAppointments = async () => {
    try {
      const res = await api.get("/appointments/today");
      setAppointments(res.data.data || []);
    } catch {}
  };

  const fetchPatients = async () => {
    try {
      const res = await api.get("/patients?size=100");
      setPatients(res.data.data || []);
    } catch {}
  };

  const registerPatient = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post("/patients", form);
      toast.success("Patient registered");
      setForm({ firstName: "", lastName: "", phone: "" });
      setShowRegister(false);
      fetchPatients();
    } catch (err: any) {
      toast.error(err.response?.data?.error?.message || "Registration failed");
    }
  };

  return (
    <div>
      <h2 className="text-3xl font-bold mb-6">Reception Dashboard</h2>

      {/* Today's Appointments */}
      <section className="mb-8">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-xl font-semibold">Today's Appointments</h3>
          <button onClick={() => setShowBooking(true)} className="bg-clinic-600 text-white px-4 py-2 rounded-lg">+ Book Appointment</button>
        </div>
        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="p-3 text-left">Queue</th><th className="p-3 text-left">Patient</th><th className="p-3 text-left">Time</th><th className="p-3 text-left">Status</th><th className="p-3 text-left">Doctor</th>
              </tr>
            </thead>
            <tbody>
              {appointments.map((apt, idx) => (
                <tr key={apt.id} className="border-t">
                  <td className="p-3">{apt.queueNumber || idx + 1}</td>
                  <td className="p-3 font-medium">{apt.patientName}</td>
                  <td className="p-3">{apt.startTime}</td>
                  <td className="p-3"><span className={`px-2 py-1 rounded text-sm ${apt.status === "CHECKED_IN" ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-800"}`}>{apt.status}</span></td>
                  <td className="p-3">{apt.doctorName || "Unassigned"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Patients List */}
      <section>
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-xl font-semibold">Patients</h3>
          <button onClick={() => setShowRegister(!showRegister)} className="bg-clinic-600 text-white px-4 py-2 rounded-lg">+ New Patient</button>
        </div>

        {showRegister && (
          <form onSubmit={registerPatient} className="bg-white p-4 rounded-lg shadow mb-4 flex gap-4 flex-wrap">
            <input type="text" placeholder="First Name" value={form.firstName} onChange={(e) => setForm({...form, firstName: e.target.value})} className="border p-2 rounded" required />
            <input type="text" placeholder="Last Name" value={form.lastName} onChange={(e) => setForm({...form, lastName: e.target.value})} className="border p-2 rounded" required />
            <input type="tel" placeholder="Phone" value={form.phone} onChange={(e) => setForm({...form, phone: e.target.value})} className="border p-2 rounded" required />
            <button type="submit" className="bg-green-600 text-white px-4 py-2 rounded">Save</button>
          </form>
        )}

        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr><th className="p-3 text-left">Name</th><th className="p-3 text-left">Phone</th><th className="p-3 text-left">Gender</th></tr>
            </thead>
            <tbody>
              {patients.map((p) => (
                <tr key={p.id} className="border-t">
                  <td className="p-3">{p.firstName} {p.lastName}</td>
                  <td className="p-3">{p.phone}</td>
                  <td className="p-3">{p.gender || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
