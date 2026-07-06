// =============================================================
// ClinIQ — Doctor Portal & Billing Frontend
// Copy each section to its file path shown above it.
// These complete the remaining frontend pages.
// =============================================================


// =============================================
// FILE: src/lib/api/consultations.ts
// =============================================
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import toast from 'react-hot-toast'

export function useDoctorQueue() {
  return useQuery({
    queryKey: ['appointments', 'doctor-queue'],
    queryFn: async () => {
      const { data } = await apiClient.get('/api/v1/appointments/today')
      return (data.data ?? []) as any[]
    },
    refetchInterval: 20_000,
  })
}

export function useStartConsultation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (payload: any) => {
      const { data } = await apiClient.post('/api/v1/consultations/start', payload)
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['consultations'] }),
  })
}

export function useUpdateConsultation() {
  return useMutation({
    mutationFn: async ({ id, ...payload }: any) => {
      const { data } = await apiClient.patch(`/api/v1/consultations/${id}`, payload)
      return data.data
    },
  })
}

export function useCompleteConsultation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.post(`/api/v1/consultations/${id}/complete`)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['appointments'] })
      toast.success('Consultation completed — patient discharged')
    },
  })
}

export function useAddPrescription() {
  return useMutation({
    mutationFn: async (payload: any) => {
      const { data } = await apiClient.post('/api/v1/consultations/prescriptions', payload)
      return data.data
    },
    onSuccess: () => toast.success('Prescription saved'),
  })
}

export function usePatientTimeline(patientId: string | null) {
  return useQuery({
    queryKey: ['consultations', 'timeline', patientId],
    queryFn: async () => {
      const { data } = await apiClient.get(
        `/api/v1/consultations/patient/${patientId}/timeline?size=5`
      )
      return data.data ?? []
    },
    enabled: !!patientId,
  })
}


// =============================================
// FILE: src/lib/api/billing.ts
// =============================================
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import toast from 'react-hot-toast'

export function useInvoices(status = '', page = 0) {
  return useQuery({
    queryKey: ['invoices', status, page],
    queryFn: async () => {
      const q = status ? `&status=${status}` : ''
      const { data } = await apiClient.get(`/api/v1/billing/invoices?page=${page}&size=20${q}`)
      return { invoices: data.data ?? [], meta: data.meta }
    },
    staleTime: 30_000,
  })
}

export function useCreateInvoice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (payload: any) => {
      const { data } = await apiClient.post('/api/v1/billing/invoices', payload)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      toast.success('Invoice created')
    },
    onError: (e: any) => toast.error(e?.response?.data?.error?.message ?? 'Failed to create invoice'),
  })
}

export function useRecordPayment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (payload: any) => {
      const { data } = await apiClient.post('/api/v1/billing/payments', payload)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      toast.success('Payment recorded')
    },
  })
}

export function useRevenueReport(year?: number, month?: number) {
  const now = new Date()
  return useQuery({
    queryKey: ['revenue', year ?? now.getFullYear(), month ?? now.getMonth() + 1],
    queryFn: async () => {
      const y = year ?? now.getFullYear()
      const m = month ?? now.getMonth() + 1
      const { data } = await apiClient.get(`/api/v1/billing/reports/revenue?year=${y}&month=${m}`)
      return data.data
    },
    staleTime: 60_000,
  })
}

export function useInitiateMpesa() {
  return useMutation({
    mutationFn: async (payload: any) => {
      const { data } = await apiClient.post('/api/v1/billing/mpesa/initiate', payload)
      return data.data
    },
    onSuccess: (d) => toast.success(d?.message ?? 'M-Pesa prompt sent'),
    onError: () => toast.error('M-Pesa initiation failed'),
  })
}


// =============================================
// FILE: src/app/(dashboard)/doctor/page.tsx
// =============================================
'use client'
import { useState, useCallback, useRef } from 'react'
import { useSelector } from 'react-redux'
import { selectUser } from '@/lib/store/slices/authSlice'
import {
  useDoctorQueue, useStartConsultation, useUpdateConsultation,
  useCompleteConsultation, useAddPrescription, usePatientTimeline
} from '@/lib/api/consultations'

type RxItem = { drugName: string; dosage: string; frequency: string; duration: string; instructions: string; quantity: number }

export default function DoctorPortalPage() {
  const user = useSelector(selectUser)
  const { data: queue = [], isLoading } = useDoctorQueue()
  const startConsult  = useStartConsultation()
  const updateConsult = useUpdateConsultation()
  const complete      = useCompleteConsultation()
  const addRx         = useAddPrescription()

  const [activeAppt,   setActiveAppt]   = useState<any>(null)
  const [consultId,    setConsultId]    = useState<string | null>(null)
  const [notes,        setNotes]        = useState('')
  const [examination,  setExamination]  = useState('')
  const [diagnosis,    setDiagnosis]    = useState('')
  const [treatment,    setTreatment]    = useState('')
  const [rxItems,      setRxItems]      = useState<RxItem[]>([])
  const [showRxForm,   setShowRxForm]   = useState(false)
  const [newRx,        setNewRx]        = useState<RxItem>({ drugName:'', dosage:'', frequency:'', duration:'', instructions:'', quantity:1 })
  const [vitals,       setVitals]       = useState({ systolicBp:'', diastolicBp:'', pulse:'', temperature:'', weight:'', spo2:'' })
  const [showHistory,  setShowHistory]  = useState(false)
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Filter queue to this doctor's patients
  const myQueue = queue.filter((a: any) =>
    a.status === 'CHECKED_IN' || a.status === 'IN_CONSULTATION' || a.status === 'SCHEDULED'
  )

  const statusColors: Record<string, string> = {
    CHECKED_IN:      'text-amber-600 bg-amber-50',
    IN_CONSULTATION: 'text-green-600 bg-green-50',
    SCHEDULED:       'text-blue-600 bg-blue-50',
  }

  async function callPatient(appt: any) {
    setActiveAppt(appt)
    setNotes(''); setExamination(''); setDiagnosis(''); setTreatment(''); setRxItems([])
    setVitals({ systolicBp:'', diastolicBp:'', pulse:'', temperature:'', weight:'', spo2:'' })
    try {
      const c = await startConsult.mutateAsync({
        appointmentId: appt.id,
        patientId: appt.patientId ?? appt.id,
      })
      setConsultId(c?.id ?? null)
    } catch {
      setConsultId(null)
    }
  }

  const autoSave = useCallback((field: string, value: string) => {
    if (!consultId) return
    if (saveTimer.current) clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      updateConsult.mutate({ id: consultId, [field]: value })
    }, 1200)
  }, [consultId])

  function handleDischarge() {
    if (!consultId) return
    if (rxItems.length > 0) {
      addRx.mutate({ consultationId: consultId, medications: rxItems })
    }
    complete.mutate(consultId)
    setActiveAppt(null); setConsultId(null)
  }

  function addRxItem() {
    if (!newRx.drugName) return
    setRxItems(prev => [...prev, newRx])
    setNewRx({ drugName:'', dosage:'', frequency:'', duration:'', instructions:'', quantity:1 })
    setShowRxForm(false)
  }

  const { data: history = [] } = usePatientTimeline(showHistory ? activeAppt?.patientId : null)

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-sm font-semibold text-gray-900">Doctor portal</h1>
          <p className="text-xs text-gray-400">
            Dr. {user?.firstName} {user?.lastName} · {myQueue.length} patient{myQueue.length !== 1 ? 's' : ''} today
          </p>
        </div>
        {activeAppt && (
          <div className="flex items-center gap-2">
            <button onClick={() => setShowHistory(h => !h)}
              className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50">
              {showHistory ? 'Hide history' : 'Patient history'}
            </button>
            <button onClick={handleDischarge} disabled={complete.isPending}
              className="px-3 py-1.5 text-xs bg-teal-600 text-white rounded-lg font-medium hover:bg-teal-700 disabled:opacity-50">
              {complete.isPending ? 'Discharging…' : '✓ Complete & discharge'}
            </button>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-hidden flex">
        {/* Queue panel */}
        <aside className="w-56 shrink-0 bg-white border-r border-gray-100 flex flex-col overflow-hidden">
          <div className="px-4 py-2.5 border-b border-gray-100">
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">My queue</p>
          </div>
          <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
            {isLoading && (
              <div className="text-xs text-gray-400 text-center py-8">Loading…</div>
            )}
            {!isLoading && myQueue.length === 0 && (
              <div className="text-xs text-gray-400 text-center py-8">Queue is empty</div>
            )}
            {myQueue.map((appt: any, i: number) => {
              const sc = statusColors[appt.status] ?? 'text-gray-600 bg-gray-50'
              const initials = appt.patientName?.split(' ').map((n: string) => n[0]).join('').slice(0,2).toUpperCase() ?? '?'
              const isActive = activeAppt?.id === appt.id
              return (
                <button key={appt.id} onClick={() => callPatient(appt)}
                  className={`w-full text-left p-2.5 rounded-lg border transition-colors ${
                    isActive
                      ? 'border-teal-200 bg-teal-50'
                      : 'border-gray-100 bg-white hover:border-gray-200 hover:bg-gray-50'
                  }`}>
                  <div className="flex items-center gap-2 mb-1.5">
                    <div className="w-7 h-7 rounded-full bg-teal-50 flex items-center justify-center text-teal-600 text-[10px] font-semibold shrink-0">
                      {initials}
                    </div>
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-gray-900 truncate">{appt.patientName}</p>
                      <p className="text-[10px] text-gray-400">#{i + 1} · {appt.startTime}</p>
                    </div>
                  </div>
                  <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-medium ${sc}`}>
                    {appt.status.replace('_', ' ')}
                  </span>
                </button>
              )
            })}
          </div>
        </aside>

        {/* Consultation workspace */}
        <main className="flex-1 overflow-y-auto p-5 space-y-4">
          {!activeAppt ? (
            <div className="h-full flex items-center justify-center text-sm text-gray-400 flex-col gap-3">
              <svg className="w-12 h-12 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414A1 1 0 0119 9.414V19a2 2 0 01-2 2z" />
              </svg>
              Select a patient to start consultation
            </div>
          ) : (
            <>
              {/* Patient header */}
              <div className="bg-white rounded-xl border border-gray-100 p-4 flex items-center gap-4">
                <div className="w-11 h-11 rounded-full bg-teal-50 flex items-center justify-center text-teal-600 text-sm font-semibold shrink-0">
                  {activeAppt.patientName?.split(' ').map((n: string) => n[0]).join('').slice(0,2).toUpperCase()}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-gray-900">{activeAppt.patientName}</p>
                  <p className="text-xs text-gray-400">{activeAppt.patientPhone} · {activeAppt.type}</p>
                </div>
                {activeAppt.chiefComplaint && (
                  <div className="text-right">
                    <p className="text-[10px] text-gray-400 mb-0.5">Chief complaint</p>
                    <p className="text-xs text-gray-600 max-w-48">{activeAppt.chiefComplaint}</p>
                  </div>
                )}
              </div>

              {/* Vitals */}
              <div className="bg-white rounded-xl border border-gray-100 p-4">
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-3">Vitals</p>
                <div className="grid grid-cols-3 gap-3 lg:grid-cols-6">
                  {[
                    { k:'systolicBp',  label:'Systolic',  unit:'mmHg', placeholder:'120' },
                    { k:'diastolicBp', label:'Diastolic', unit:'mmHg', placeholder:'80'  },
                    { k:'pulse',       label:'Pulse',     unit:'bpm',  placeholder:'72'  },
                    { k:'temperature', label:'Temp',      unit:'°C',   placeholder:'36.6'},
                    { k:'weight',      label:'Weight',    unit:'kg',   placeholder:'65'  },
                    { k:'spo2',        label:'SpO₂',      unit:'%',    placeholder:'98'  },
                  ].map(v => (
                    <div key={v.k}>
                      <p className="text-[10px] text-gray-400 mb-1">{v.label} <span className="text-gray-300">({v.unit})</span></p>
                      <input
                        type="number"
                        value={(vitals as any)[v.k]}
                        onChange={e => setVitals(p => ({ ...p, [v.k]: e.target.value }))}
                        placeholder={v.placeholder}
                        className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* Clinical notes */}
              <div className="grid grid-cols-2 gap-4">
                {[
                  { label:'History & presenting complaint', key:'history', val:notes, set:(v:string)=>{ setNotes(v); autoSave('history',v) }, rows:4 },
                  { label:'Examination findings', key:'examination', val:examination, set:(v:string)=>{ setExamination(v); autoSave('examination',v) }, rows:4 },
                  { label:'Diagnosis', key:'diagnosis', val:diagnosis, set:(v:string)=>{ setDiagnosis(v); autoSave('diagnosis',v) }, rows:3 },
                  { label:'Treatment plan', key:'treatmentPlan', val:treatment, set:(v:string)=>{ setTreatment(v); autoSave('treatmentPlan',v) }, rows:3 },
                ].map(f => (
                  <div key={f.key} className="bg-white rounded-xl border border-gray-100 p-4">
                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">{f.label}</p>
                    <textarea
                      rows={f.rows}
                      value={f.val}
                      onChange={e => f.set(e.target.value)}
                      placeholder={`Enter ${f.label.toLowerCase()}…`}
                      className="w-full text-sm text-gray-700 placeholder-gray-300 resize-none focus:outline-none"
                    />
                    {consultId && <p className="text-[10px] text-gray-300 mt-1">Auto-saving…</p>}
                  </div>
                ))}
              </div>

              {/* Prescriptions */}
              <div className="bg-white rounded-xl border border-gray-100 p-4">
                <div className="flex items-center justify-between mb-3">
                  <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">Prescriptions</p>
                  <button onClick={() => setShowRxForm(true)}
                    className="px-2.5 py-1 text-xs bg-teal-50 border border-teal-200 text-teal-700 rounded-lg hover:bg-teal-100">
                    + Add medication
                  </button>
                </div>

                {rxItems.length === 0 ? (
                  <p className="text-xs text-gray-300 text-center py-3">No medications added yet</p>
                ) : (
                  <div className="space-y-2">
                    {rxItems.map((rx, i) => (
                      <div key={i} className="flex items-start justify-between p-2.5 bg-gray-50 rounded-lg">
                        <div>
                          <p className="text-xs font-medium text-gray-900">{rx.drugName} — {rx.dosage}</p>
                          <p className="text-[10px] text-gray-400">{rx.frequency} · {rx.duration} · Qty: {rx.quantity}</p>
                          {rx.instructions && <p className="text-[10px] text-gray-400 italic">{rx.instructions}</p>}
                        </div>
                        <button onClick={() => setRxItems(p => p.filter((_,j) => j !== i))}
                          className="text-[10px] text-red-400 hover:text-red-600 ml-2">✕</button>
                      </div>
                    ))}
                  </div>
                )}

                {showRxForm && (
                  <div className="mt-3 p-3 border border-dashed border-teal-200 rounded-lg bg-teal-50 space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      {[
                        { k:'drugName',     p:'Drug name *',      wide:true  },
                        { k:'dosage',       p:'Dosage (e.g. 500mg)' },
                        { k:'frequency',    p:'Frequency (e.g. TDS)' },
                        { k:'duration',     p:'Duration (e.g. 7 days)' },
                        { k:'instructions', p:'Instructions' },
                      ].map(f => (
                        <input key={f.k} type="text"
                          placeholder={f.p}
                          value={(newRx as any)[f.k]}
                          onChange={e => setNewRx(p => ({ ...p, [f.k]: e.target.value }))}
                          className={`px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none focus:ring-1 focus:ring-teal-500 ${f.wide ? 'col-span-2' : ''}`}
                        />
                      ))}
                      <input type="number" min={1}
                        placeholder="Quantity"
                        value={newRx.quantity}
                        onChange={e => setNewRx(p => ({ ...p, quantity: +e.target.value }))}
                        className="px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none"
                      />
                    </div>
                    <div className="flex gap-2">
                      <button onClick={addRxItem} className="px-3 py-1.5 text-xs bg-teal-600 text-white rounded-lg hover:bg-teal-700">
                        Add to prescription
                      </button>
                      <button onClick={() => setShowRxForm(false)} className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg text-gray-500">
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Patient history panel */}
              {showHistory && history.length > 0 && (
                <div className="bg-white rounded-xl border border-gray-100 p-4">
                  <p className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-3">Previous visits</p>
                  <div className="space-y-3">
                    {history.slice(0, 3).map((c: any) => (
                      <div key={c.id} className="p-3 bg-gray-50 rounded-lg">
                        <p className="text-xs font-medium text-gray-700">Visit #{c.visitNumber} — {new Date(c.createdAt).toLocaleDateString()}</p>
                        {c.diagnosis && <p className="text-xs text-gray-500 mt-1">Dx: {c.diagnosis}</p>}
                        {c.prescriptions?.length > 0 && (
                          <p className="text-[10px] text-gray-400 mt-1">
                            Rx: {c.prescriptions[0].medications?.slice(0,2).map((m: any) => m.drugName).join(', ')}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  )
}


// =============================================
// FILE: src/app/(dashboard)/billing/page.tsx
// =============================================
'use client'
import { useState } from 'react'
import { format } from 'date-fns'
import { useInvoices, useCreateInvoice, useRecordPayment, useRevenueReport, useInitiateMpesa } from '@/lib/api/billing'
import { usePatients } from '@/lib/api/patients'

type LineItem = { description: string; quantity: number; unitPrice: number }

export default function BillingPage() {
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [showPay,    setShowPay]    = useState<any>(null)
  const [patientSearch, setPatientSearch] = useState('')
  const [selectedPatient, setSelectedPatient] = useState<any>(null)
  const [lines, setLines] = useState<LineItem[]>([{ description:'', quantity:1, unitPrice:0 }])
  const [discount, setDiscount] = useState(0)
  const [payMethod, setPayMethod] = useState('CASH')
  const [payAmount, setPayAmount] = useState('')
  const [mpesaPhone, setMpesaPhone] = useState('')

  const { data, isLoading } = useInvoices(statusFilter, page)
  const { invoices = [], meta } = data ?? {}
  const { data: revenue } = useRevenueReport()
  const { data: patientData } = usePatients(patientSearch)
  const createInvoice = useCreateInvoice()
  const recordPayment = useRecordPayment()
  const initiateMpesa = useInitiateMpesa()

  const subtotal = lines.reduce((s, l) => s + l.quantity * l.unitPrice, 0)
  const total = Math.max(0, subtotal - discount)

  function addLine() { setLines(p => [...p, { description:'', quantity:1, unitPrice:0 }]) }
  function removeLine(i: number) { setLines(p => p.filter((_, j) => j !== i)) }

  async function handleCreateInvoice() {
    if (!selectedPatient) return
    await createInvoice.mutateAsync({
      patientId: selectedPatient.id,
      lineItems: lines.filter(l => l.description),
      discount,
    })
    setShowCreate(false)
    setLines([{ description:'', quantity:1, unitPrice:0 }])
    setSelectedPatient(null)
    setDiscount(0)
  }

  async function handlePay() {
    if (!showPay || !payAmount) return
    if (payMethod === 'MPESA') {
      await initiateMpesa.mutateAsync({ invoiceId: showPay.id, phone: mpesaPhone, amount: +payAmount })
    } else {
      await recordPayment.mutateAsync({ invoiceId: showPay.id, amount: +payAmount, method: payMethod })
    }
    setShowPay(null)
  }

  const statusBadge: Record<string, string> = {
    PAID:       'bg-green-50 text-green-600',
    PARTIAL:    'bg-amber-50 text-amber-600',
    ISSUED:     'bg-blue-50 text-blue-600',
    OVERDUE:    'bg-red-50 text-red-600',
    CANCELLED:  'bg-gray-100 text-gray-500',
    DRAFT:      'bg-gray-100 text-gray-400',
    REFUNDED:   'bg-purple-50 text-purple-600',
  }

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-sm font-semibold text-gray-900">Billing & payments</h1>
          <p className="text-xs text-gray-400">{meta?.total ?? 0} invoices</p>
        </div>
        <button onClick={() => setShowCreate(true)}
          className="px-3 py-1.5 text-xs bg-teal-600 text-white rounded-lg font-medium hover:bg-teal-700">
          + Create invoice
        </button>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-4">
        {/* Revenue stats */}
        {revenue && (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {[
              { label:'Total invoiced',   value:`KES ${Number(revenue.totalInvoiced).toLocaleString()}`,   color:'text-gray-900' },
              { label:'Collected',        value:`KES ${Number(revenue.totalCollected).toLocaleString()}`,  color:'text-green-600' },
              { label:'Outstanding',      value:`KES ${Number(revenue.outstandingBalance).toLocaleString()}`, color:'text-amber-600' },
              { label:'Paid invoices',    value:revenue.paidCount,                                        color:'text-gray-400' },
            ].map(s => (
              <div key={s.label} className="bg-white rounded-xl border border-gray-100 p-4">
                <p className={`text-xl font-semibold ${s.color}`}>{s.value}</p>
                <p className="text-xs text-gray-400 mt-1">{s.label}</p>
              </div>
            ))}
          </div>
        )}

        {/* Invoice table */}
        <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
          <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between gap-3 flex-wrap">
            <h2 className="text-sm font-medium text-gray-900">Invoices</h2>
            <div className="flex gap-1.5">
              {['', 'ISSUED', 'PARTIAL', 'PAID', 'OVERDUE'].map(s => (
                <button key={s} onClick={() => { setStatusFilter(s); setPage(0) }}
                  className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                    statusFilter === s
                      ? 'border-teal-200 bg-teal-50 text-teal-700 font-medium'
                      : 'border-gray-100 text-gray-500 hover:border-gray-200'
                  }`}>
                  {s || 'All'}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-[1fr_100px_90px_90px_80px_70px] gap-3 px-5 py-2 bg-gray-50 text-xs font-medium text-gray-400 border-b border-gray-100">
            <span>Patient</span><span>Invoice #</span><span>Total</span><span>Balance</span><span>Status</span><span></span>
          </div>

          {isLoading ? (
            <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
          ) : invoices.length === 0 ? (
            <div className="py-12 text-center text-sm text-gray-400">No invoices found</div>
          ) : (
            <div className="divide-y divide-gray-50">
              {invoices.map((inv: any) => (
                <div key={inv.id} className="grid grid-cols-[1fr_100px_90px_90px_80px_70px] gap-3 items-center px-5 py-3 hover:bg-gray-50 text-sm">
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-gray-900 truncate">{inv.patientName}</p>
                    <p className="text-[10px] text-gray-400">{format(new Date(inv.issuedAt), 'MMM d, yyyy')}</p>
                  </div>
                  <span className="text-xs font-mono text-gray-500">{inv.invoiceNumber}</span>
                  <span className="text-xs text-gray-900">KES {Number(inv.total).toLocaleString()}</span>
                  <span className={`text-xs ${inv.balance > 0 ? 'text-amber-600 font-medium' : 'text-gray-400'}`}>
                    {inv.balance > 0 ? `KES ${Number(inv.balance).toLocaleString()}` : '—'}
                  </span>
                  <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-medium ${statusBadge[inv.status] ?? 'bg-gray-100 text-gray-500'}`}>
                    {inv.status}
                  </span>
                  {inv.balance > 0 && (
                    <button onClick={() => { setShowPay(inv); setPayAmount(String(inv.balance)); setMpesaPhone('') }}
                      className="text-xs text-teal-600 font-medium hover:text-teal-700">
                      Pay →
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          {meta && meta.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
              <span>Page {page + 1} of {meta.totalPages}</span>
              <div className="flex gap-2">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1 border rounded-lg disabled:opacity-40">← Prev</button>
                <button disabled={page >= meta.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1 border rounded-lg disabled:opacity-40">Next →</button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create invoice modal */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="p-5 border-b border-gray-100 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-900">Create invoice</h2>
              <button onClick={() => setShowCreate(false)} className="text-gray-400 hover:text-gray-600">✕</button>
            </div>
            <div className="p-5 space-y-4">
              {/* Patient search */}
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Patient</label>
                <input value={patientSearch} onChange={e => setPatientSearch(e.target.value)}
                  placeholder="Search patient…"
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 mb-1.5"
                />
                {patientData?.patients?.slice(0, 4).map((p: any) => (
                  <div key={p.id} onClick={() => { setSelectedPatient(p); setPatientSearch(`${p.firstName} ${p.lastName}`) }}
                    className={`px-3 py-2 rounded-lg cursor-pointer text-sm transition-colors ${selectedPatient?.id === p.id ? 'bg-teal-50 border border-teal-200' : 'hover:bg-gray-50 border border-transparent'}`}>
                    {p.firstName} {p.lastName} <span className="text-xs text-gray-400">{p.phone}</span>
                  </div>
                ))}
              </div>

              {/* Line items */}
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-2">Line items</label>
                <div className="space-y-2">
                  {lines.map((line, i) => (
                    <div key={i} className="grid grid-cols-[1fr_60px_80px_24px] gap-2 items-center">
                      <input value={line.description} onChange={e => setLines(p => p.map((l,j) => j===i ? {...l, description:e.target.value} : l))}
                        placeholder="Description" className="px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-1 focus:ring-teal-500"/>
                      <input type="number" min={1} value={line.quantity} onChange={e => setLines(p => p.map((l,j) => j===i ? {...l, quantity:+e.target.value} : l))}
                        className="px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs text-center focus:outline-none"/>
                      <input type="number" min={0} value={line.unitPrice} onChange={e => setLines(p => p.map((l,j) => j===i ? {...l, unitPrice:+e.target.value} : l))}
                        placeholder="Price" className="px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs text-right focus:outline-none"/>
                      <button onClick={() => removeLine(i)} className="text-gray-300 hover:text-red-400 text-xs">✕</button>
                    </div>
                  ))}
                  <button onClick={addLine} className="text-xs text-teal-600 hover:text-teal-700">+ Add line</button>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <label className="text-xs text-gray-500">Discount (KES)</label>
                <input type="number" min={0} value={discount} onChange={e => setDiscount(+e.target.value)}
                  className="w-28 px-2.5 py-1.5 border border-gray-200 rounded-lg text-xs text-right focus:outline-none"/>
              </div>

              <div className="bg-gray-50 rounded-lg px-4 py-3 text-sm">
                <div className="flex justify-between text-xs text-gray-500"><span>Subtotal</span><span>KES {subtotal.toLocaleString()}</span></div>
                {discount > 0 && <div className="flex justify-between text-xs text-gray-500"><span>Discount</span><span>- KES {discount.toLocaleString()}</span></div>}
                <div className="flex justify-between font-semibold text-gray-900 mt-1.5"><span>Total</span><span>KES {total.toLocaleString()}</span></div>
              </div>

              <button onClick={handleCreateInvoice} disabled={!selectedPatient || createInvoice.isPending || lines.every(l => !l.description)}
                className="w-full py-2.5 bg-teal-600 text-white text-sm font-medium rounded-xl hover:bg-teal-700 disabled:opacity-50">
                {createInvoice.isPending ? 'Creating…' : 'Create invoice'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment modal */}
      {showPay && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-gray-900">Record payment</h2>
              <button onClick={() => setShowPay(null)} className="text-gray-400">✕</button>
            </div>
            <p className="text-xs text-gray-500 mb-4">Invoice {showPay.invoiceNumber} · Balance KES {Number(showPay.balance).toLocaleString()}</p>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Amount (KES)</label>
                <input type="number" value={payAmount} onChange={e => setPayAmount(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"/>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Method</label>
                <select value={payMethod} onChange={e => setPayMethod(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none">
                  {['CASH','MPESA','CARD','INSURANCE','BANK_TRANSFER'].map(m => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </select>
              </div>
              {payMethod === 'MPESA' && (
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">M-Pesa phone</label>
                  <input value={mpesaPhone} onChange={e => setMpesaPhone(e.target.value)}
                    placeholder="e.g. 0712345678"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"/>
                </div>
              )}
              <button onClick={handlePay} disabled={!payAmount || (payMethod === 'MPESA' && !mpesaPhone)}
                className="w-full py-2.5 bg-teal-600 text-white text-sm font-medium rounded-xl hover:bg-teal-700 disabled:opacity-50">
                {payMethod === 'MPESA' ? 'Send M-Pesa prompt' : 'Record payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}


// =============================================
// FILE: src/app/(auth)/register/page.tsx
// Clinic self-registration / onboarding
// =============================================
'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useDispatch } from 'react-redux'
import { setCredentials } from '@/lib/store/slices/authSlice'
import { apiClient } from '@/lib/api/client'

const STEPS = ['Clinic details', 'Admin account', 'Done']

export default function RegisterPage() {
  const [step, setStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({
    clinicName:'', clinicSlug:'', clinicPhone:'',
    firstName:'', lastName:'', email:'', password:'', phone:''
  })
  const dispatch = useDispatch()
  const router   = useRouter()

  function update(k: keyof typeof form, v: string) {
    setForm(p => ({ ...p, [k]: v }))
    if (k === 'clinicName' && step === 0) {
      setForm(p => ({ ...p, clinicSlug: v.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'') }))
    }
  }

  async function handleRegister() {
    setLoading(true); setError('')
    try {
      const { data } = await apiClient.post('/api/v1/auth/register', form, { headers: {} })
      if (!data.success) throw new Error(data.error?.message)
      dispatch(setCredentials({ user: data.data.user, accessToken: data.data.accessToken, refreshToken: data.data.refreshToken }))
      setStep(2)
      setTimeout(() => router.push('/reception'), 2000)
    } catch (e: any) {
      setError(e?.response?.data?.error?.message ?? e?.message ?? 'Registration failed')
    } finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen bg-teal-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-10 h-10 rounded-xl bg-teal-600 flex items-center justify-center">
            <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z"/>
            </svg>
          </div>
          <div>
            <p className="text-xl font-semibold text-gray-900">ClinIQ</p>
            <p className="text-xs text-gray-500">Set up your clinic</p>
          </div>
        </div>

        {/* Steps */}
        <div className="flex items-center justify-center gap-2 mb-6">
          {STEPS.map((s,i) => (
            <div key={s} className="flex items-center gap-2">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                i < step ? 'bg-teal-600 text-white' : i === step ? 'bg-teal-100 text-teal-700 ring-2 ring-teal-400' : 'bg-gray-100 text-gray-400'
              }`}>{i < step ? '✓' : i + 1}</div>
              {i < STEPS.length - 1 && <div className={`w-8 h-px ${i < step ? 'bg-teal-400' : 'bg-gray-200'}`}/>}
            </div>
          ))}
        </div>

        <div className="bg-white rounded-2xl border border-gray-100 p-7">
          {step === 2 ? (
            <div className="text-center py-4">
              <div className="w-14 h-14 bg-green-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <span className="text-2xl">✅</span>
              </div>
              <h2 className="text-lg font-semibold text-gray-900 mb-1">You're all set!</h2>
              <p className="text-sm text-gray-500">Redirecting to your clinic dashboard…</p>
            </div>
          ) : (
            <>
              <h2 className="text-base font-semibold text-gray-900 mb-4">{STEPS[step]}</h2>
              {error && <div className="mb-4 px-3 py-2.5 bg-red-50 border border-red-100 rounded-lg text-xs text-red-600">{error}</div>}

              {step === 0 && (
                <div className="space-y-3">
                  {[
                    { k:'clinicName', label:'Clinic name', ph:'Nairobi Medical Centre' },
                    { k:'clinicSlug', label:'Clinic URL slug', ph:'nairobi-medical', hint:'cliniq.app/nairobi-medical' },
                    { k:'clinicPhone', label:'Clinic phone', ph:'+254700000000' },
                  ].map(f => (
                    <div key={f.k}>
                      <label className="block text-xs font-medium text-gray-700 mb-1">{f.label}</label>
                      <input value={(form as any)[f.k]} onChange={e => update(f.k as keyof typeof form, e.target.value)}
                        placeholder={f.ph} className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"/>
                      {f.hint && <p className="text-[10px] text-gray-400 mt-0.5">{f.hint}</p>}
                    </div>
                  ))}
                  <button onClick={() => setStep(1)} disabled={!form.clinicName || !form.clinicSlug}
                    className="w-full py-2.5 bg-teal-600 text-white text-sm font-medium rounded-xl hover:bg-teal-700 disabled:opacity-50 mt-2">
                    Continue →
                  </button>
                </div>
              )}

              {step === 1 && (
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    {['firstName','lastName'].map(k => (
                      <div key={k}>
                        <label className="block text-xs font-medium text-gray-700 mb-1">
                          {k === 'firstName' ? 'First name' : 'Last name'}
                        </label>
                        <input value={(form as any)[k]} onChange={e => update(k as keyof typeof form, e.target.value)}
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"/>
                      </div>
                    ))}
                  </div>
                  {[
                    { k:'email', label:'Email', type:'email', ph:'you@clinic.com' },
                    { k:'password', label:'Password (min 8 chars)', type:'password', ph:'••••••••' },
                    { k:'phone', label:'Your phone', type:'tel', ph:'+254712345678' },
                  ].map(f => (
                    <div key={f.k}>
                      <label className="block text-xs font-medium text-gray-700 mb-1">{f.label}</label>
                      <input type={f.type} value={(form as any)[f.k]} onChange={e => update(f.k as keyof typeof form, e.target.value)}
                        placeholder={f.ph} className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"/>
                    </div>
                  ))}
                  <div className="flex gap-2 mt-2">
                    <button onClick={() => setStep(0)} className="flex-1 py-2.5 border border-gray-200 text-sm text-gray-600 rounded-xl hover:bg-gray-50">← Back</button>
                    <button onClick={handleRegister}
                      disabled={!form.email || !form.password || form.password.length < 8 || loading}
                      className="flex-1 py-2.5 bg-teal-600 text-white text-sm font-medium rounded-xl hover:bg-teal-700 disabled:opacity-50">
                      {loading ? 'Creating…' : 'Create clinic'}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
        <p className="text-center text-xs text-gray-400 mt-5">
          Already have an account? <a href="/login" className="text-teal-600 hover:underline">Sign in</a>
        </p>
      </div>
    </div>
  )
}
