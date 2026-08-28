'use client';
import { useState, useEffect } from "react";
import api from "@/lib/api";
import toast from "react-hot-toast";
import { DrugStockResponse } from "@/types";

export default function PharmacyPage() {
  const [drugs, setDrugs] = useState<DrugStockResponse[]>([]);
  const [showAddStock, setShowAddStock] = useState(false);
  const [showDispense, setShowDispense] = useState(false);
  const [stockForm, setStockForm] = useState({ drugId: "", batchNumber: "", quantity: 0, unitCost: 0, expiryDate: "" });
  const [dispenseForm, setDispenseForm] = useState({ drugId: "", patientId: "", quantity: 0 });

  useEffect(() => { fetchDrugs(); }, []);

  const fetchDrugs = async () => {
    try {
      const res = await api.get("/pharmacy/drugs?size=100");
      setDrugs(res.data.data || []);
    } catch {}
  };

  const addStock = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post("/pharmacy/stock/add", stockForm);
      toast.success("Stock added");
      fetchDrugs();
    } catch (err: any) { toast.error(err.response?.data?.error?.message || "Failed"); }
  };

  const dispense = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post("/pharmacy/dispense", dispenseForm);
      toast.success("Dispensed");
      fetchDrugs();
    } catch (err: any) { toast.error(err.response?.data?.error?.message || "Failed"); }
  };

  return (
    <div>
      <h2 className="text-3xl font-bold mb-6">Pharmacy</h2>
      <div className="flex gap-4 mb-6">
        <button onClick={() => setShowAddStock(!showAddStock)} className="bg-clinic-600 text-white px-4 py-2 rounded">+ Add Stock</button>
        <button onClick={() => setShowDispense(!showDispense)} className="bg-clinic-600 text-white px-4 py-2 rounded">Dispense</button>
      </div>

      {showAddStock && (
        <form onSubmit={addStock} className="bg-white p-4 rounded-lg shadow mb-4 flex gap-4 flex-wrap">
          <select value={stockForm.drugId} onChange={(e) => setStockForm({...stockForm, drugId: e.target.value})} className="border p-2 rounded" required>
            <option value="">Select Drug</option>
            {drugs.map(d => <option key={d.drugId} value={d.drugId}>{d.name}</option>)}
          </select>
          <input type="text" placeholder="Batch #" value={stockForm.batchNumber} onChange={(e) => setStockForm({...stockForm, batchNumber: e.target.value})} className="border p-2 rounded" required />
          <input type="number" placeholder="Quantity" value={stockForm.quantity || ""} onChange={(e) => setStockForm({...stockForm, quantity: +e.target.value})} className="border p-2 rounded" required />
          <input type="number" placeholder="Unit Cost" value={stockForm.unitCost || ""} onChange={(e) => setStockForm({...stockForm, unitCost: +e.target.value})} className="border p-2 rounded" />
          <input type="date" value={stockForm.expiryDate} onChange={(e) => setStockForm({...stockForm, expiryDate: e.target.value})} className="border p-2 rounded" required />
          <button type="submit" className="bg-green-600 text-white px-4 py-2 rounded">Save</button>
        </form>
      )}

      {showDispense && (
        <form onSubmit={dispense} className="bg-white p-4 rounded-lg shadow mb-4 flex gap-4 flex-wrap">
          <select value={dispenseForm.drugId} onChange={(e) => setDispenseForm({...dispenseForm, drugId: e.target.value})} className="border p-2 rounded" required>
            <option value="">Select Drug</option>
            {drugs.map(d => <option key={d.drugId} value={d.drugId}>{d.name}</option>)}
          </select>
          <input type="text" placeholder="Patient ID" value={dispenseForm.patientId} onChange={(e) => setDispenseForm({...dispenseForm, patientId: e.target.value})} className="border p-2 rounded" required />
          <input type="number" placeholder="Quantity" value={dispenseForm.quantity || ""} onChange={(e) => setDispenseForm({...dispenseForm, quantity: +e.target.value})} className="border p-2 rounded" required />
          <button type="submit" className="bg-green-600 text-white px-4 py-2 rounded">Dispense</button>
        </form>
      )}

      <div className="bg-white rounded-lg shadow overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr><th className="p-3 text-left">Drug</th><th className="p-3 text-left">Category</th><th className="p-3 text-left">Stock</th><th className="p-3 text-left">Status</th><th className="p-3 text-left">Price (KES)</th></tr>
          </thead>
          <tbody>
            {drugs.map(d => (
              <tr key={d.drugId} className="border-t">
                <td className="p-3 font-medium">{d.name}</td>
                <td className="p-3">{d.category}</td>
                <td className="p-3">{d.totalStock}</td>
                <td className="p-3"><span className={`px-2 py-1 rounded text-sm ${d.stockStatus === "IN_STOCK" ? "bg-green-100 text-green-800" : d.stockStatus === "LOW_STOCK" ? "bg-yellow-100 text-yellow-800" : "bg-red-100 text-red-800"}`}>{d.stockStatus}</span></td>
                <td className="p-3">{d.sellingPrice}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
