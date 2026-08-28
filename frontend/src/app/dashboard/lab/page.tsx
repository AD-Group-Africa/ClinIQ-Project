'use client';
import { useState, useEffect } from "react";
import api from "@/lib/api";
import toast from "react-hot-toast";

export default function LabPage() {
  const [tests, setTests] = useState<any[]>([]);
  const [pending, setPending] = useState<any[]>([]);
  const [requestForm, setRequestForm] = useState({ patientId: "", testIds: [] as string[], priority: "ROUTINE" });
  const [resultsForm, setResultsForm] = useState<any>(null);

  useEffect(() => {
    api.get("/lab/tests?size=100").then(r => setTests(r.data.data || [])).catch(() => {});
    fetchPending();
  }, []);

  const fetchPending = async () => {
    try {
      const res = await api.get("/lab/requests/pending?size=50");
      setPending(res.data.data || []);
    } catch {}
  };

  const createRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post("/lab/requests", requestForm);
      toast.success("Lab request sent");
      fetchPending();
    } catch (err: any) { toast.error(err.response?.data?.error?.message || "Failed"); }
  };

  const enterResults = async (requestId: string) => {
    // simplified: just complete for demo; full results form would be more complex
    try {
      await api.patch(`/lab/requests/${requestId}/status`, { status: "COMPLETED" });
      toast.success("Results entered (demo)");
      fetchPending();
    } catch (err: any) { toast.error(err.response?.data?.error?.message || "Failed"); }
  };

  return (
    <div>
      <h2 className="text-3xl font-bold mb-6">Laboratory</h2>

      {/* Create Lab Request */}
      <div className="bg-white rounded-lg shadow p-6 mb-8">
        <h3 className="text-xl font-semibold mb-4">New Lab Request</h3>
        <form onSubmit={createRequest} className="flex gap-4 flex-wrap">
          <input type="text" placeholder="Patient ID" value={requestForm.patientId} onChange={(e) => setRequestForm({...requestForm, patientId: e.target.value})} className="border p-2 rounded" required />
          <select multiple value={requestForm.testIds} onChange={(e) => setRequestForm({...requestForm, testIds: Array.from(e.target.selectedOptions, opt => opt.value)})} className="border p-2 rounded">
            {tests.map(t => <option key={t.id} value={t.id}>{t.name} (KES {t.price})</option>)}
          </select>
          <select value={requestForm.priority} onChange={(e) => setRequestForm({...requestForm, priority: e.target.value})} className="border p-2 rounded">
            <option value="ROUTINE">Routine</option><option value="URGENT">Urgent</option><option value="STAT">STAT</option>
          </select>
          <button type="submit" className="bg-clinic-600 text-white px-4 py-2 rounded">Send Request</button>
        </form>
      </div>

      {/* Pending Requests */}
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-xl font-semibold mb-4">Pending Requests</h3>
        <table className="w-full">
          <thead><tr><th className="p-2 text-left">Request #</th><th className="p-2 text-left">Patient</th><th className="p-2 text-left">Tests</th><th className="p-2 text-left">Priority</th><th className="p-2"></th></tr></thead>
          <tbody>
            {pending.map(req => (
              <tr key={req.id} className="border-t">
                <td className="p-2">{req.requestNumber}</td>
                <td className="p-2">{req.patientName}</td>
                <td className="p-2">{req.tests?.length || 0} tests</td>
                <td className="p-2">{req.priority}</td>
                <td className="p-2"><button onClick={() => enterResults(req.id)} className="bg-green-600 text-white px-3 py-1 rounded text-sm">Enter Results</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
