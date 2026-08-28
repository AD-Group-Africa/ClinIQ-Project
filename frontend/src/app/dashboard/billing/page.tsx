'use client';
import { useState, useEffect } from "react";
import api from "@/lib/api";
import toast from "react-hot-toast";

export default function BillingPage() {
  const [invoices, setInvoices] = useState<any[]>([]);
  const [patients, setPatients] = useState<any[]>([]);
  const [form, setForm] = useState({ patientId: "", lineItems: [{ description: "", quantity: 1, unitPrice: 0 }], discount: 0 });

  useEffect(() => {
    fetchInvoices();
    api.get("/patients?size=200").then(r => setPatients(r.data.data || [])).catch(() => {});
  }, []);

  const fetchInvoices = async () => {
    try {
      const res = await api.get("/billing/invoices?size=50");
      setInvoices(res.data.data || []);
    } catch {}
  };

  const createInvoice = async () => {
    try {
      await api.post("/billing/invoices", form);
      toast.success("Invoice created");
      fetchInvoices();
    } catch (err: any) {
      toast.error(err.response?.data?.error?.message || "Creation failed");
    }
  };

  const recordPayment = async (invoiceId: string, amount: number) => {
    try {
      await api.post("/billing/payments", { invoiceId, amount, method: "CASH" });
      toast.success("Payment recorded");
      fetchInvoices();
    } catch (err: any) {
      toast.error(err.response?.data?.error?.message || "Payment failed");
    }
  };

  return (
    <div>
      <h2 className="text-3xl font-bold mb-6">Billing</h2>
      <div className="bg-white rounded-lg shadow p-6 mb-8">
        <h3 className="text-xl font-semibold mb-4">Create Invoice</h3>
        <select value={form.patientId} onChange={(e) => setForm({...form, patientId: e.target.value})} className="border p-2 rounded mb-4 w-full">
          <option value="">Select Patient</option>
          {patients.map((p) => <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>)}
        </select>
        {form.lineItems.map((item, idx) => (
          <div key={idx} className="flex gap-4 mb-2">
            <input type="text" placeholder="Description" value={item.description} onChange={(e) => { const items = [...form.lineItems]; items[idx].description = e.target.value; setForm({...form, lineItems: items}); }} className="border p-2 rounded flex-1" />
            <input type="number" placeholder="Qty" value={item.quantity} onChange={(e) => { const items = [...form.lineItems]; items[idx].quantity = +e.target.value; setForm({...form, lineItems: items}); }} className="border p-2 rounded w-20" />
            <input type="number" placeholder="Price" value={item.unitPrice} onChange={(e) => { const items = [...form.lineItems]; items[idx].unitPrice = +e.target.value; setForm({...form, lineItems: items}); }} className="border p-2 rounded w-24" />
            <button onClick={() => setForm({...form, lineItems: form.lineItems.filter((_, i) => i !== idx)})} className="text-red-500">X</button>
          </div>
        ))}
        <button onClick={() => setForm({...form, lineItems: [...form.lineItems, { description: "", quantity: 1, unitPrice: 0 }]})} className="text-clinic-600 mb-4">+ Add Line</button>
        <button onClick={createInvoice} className="bg-green-600 text-white px-6 py-2 rounded">Create Invoice</button>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-xl font-semibold mb-4">Recent Invoices</h3>
        <table className="w-full">
          <thead><tr><th className="p-2 text-left">#</th><th className="p-2 text-left">Patient</th><th className="p-2 text-left">Total</th><th className="p-2 text-left">Paid</th><th className="p-2 text-left">Status</th><th className="p-2"></th></tr></thead>
          <tbody>
            {invoices.map((inv) => (
              <tr key={inv.id} className="border-t">
                <td className="p-2">{inv.invoiceNumber}</td>
                <td className="p-2">{inv.patientName}</td>
                <td className="p-2">KES {inv.total}</td>
                <td className="p-2">KES {inv.paidAmount}</td>
                <td className="p-2">{inv.status}</td>
                <td className="p-2">{inv.status !== "PAID" && <button onClick={() => recordPayment(inv.id, inv.total - inv.paidAmount)} className="bg-clinic-600 text-white px-3 py-1 rounded text-sm">Pay</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
