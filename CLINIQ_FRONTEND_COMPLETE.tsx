// =============================================================
// ClinIQ — Complete Frontend (Next.js 14, TypeScript)
// Copy each section into the file path shown above it.
// Run: npm install && npm run dev
// =============================================================
// package.json dependencies to add:
// {
//   "dependencies": {
//     "next": "14.2.5", "react": "^18", "react-dom": "^18",
//     "@reduxjs/toolkit": "^2.2.6", "react-redux": "^9.1.2",
//     "@tanstack/react-query": "^5.51.1", "axios": "^1.7.2",
//     "clsx": "^2.1.1", "tailwind-merge": "^2.4.0",
//     "lucide-react": "^0.414.0", "react-hot-toast": "^2.4.1",
//     "date-fns": "^3.6.0"
//   },
//   "devDependencies": {
//     "typescript": "^5", "@types/node": "^20", "@types/react": "^18",
//     "tailwindcss": "^3.4.6", "postcss": "^8", "autoprefixer": "^10"
//   }
// }
// =============================================================


// =============================================
// FILE: src/types/index.ts
// =============================================
export type UserRole =
  | 'SUPER_ADMIN' | 'CLINIC_ADMIN' | 'DOCTOR'
  | 'NURSE' | 'RECEPTIONIST' | 'FINANCE' | 'PATIENT'

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  clinicId: string | null
  avatarUrl: string | null
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export type AppointmentStatus =
  | 'SCHEDULED' | 'CONFIRMED' | 'CHECKED_IN'
  | 'IN_CONSULTATION' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export type AppointmentType =
  | 'CONSULTATION' | 'FOLLOW_UP' | 'PROCEDURE' | 'CHECK_UP' | 'WALK_IN'

export interface Appointment {
  id: string
  patientName: string
  patientPhone: string
  doctorName: string | null
  appointmentDate: string
  startTime: string
  endTime: string
  status: AppointmentStatus
  type: AppointmentType
  queueNumber: number | null
  chiefComplaint: string | null
  createdAt: string
}

export interface Patient {
  id: string
  patientNumber: string | null
  firstName: string
  lastName: string
  phone: string
  email: string | null
  dateOfBirth: string | null
  gender: string | null
  bloodGroup: string
  allergies: string[]
  createdAt: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string } | null
  meta: { page: number; size: number; total: number; totalPages: number } | null
}


// =============================================
// FILE: src/lib/api/client.ts
// =============================================
import axios from 'axios'

export const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 12_000,
})

apiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('cliniq_access')
    const clinicId = localStorage.getItem('cliniq_clinic_id')
    if (token)    config.headers['Authorization'] = `Bearer ${token}`
    if (clinicId) config.headers['X-Clinic-ID']   = clinicId
  }
  return config
})

apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.clear()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)


// =============================================
// FILE: src/lib/api/auth.ts
// =============================================
import { apiClient } from './client'
import type { TokenResponse } from '@/types'

export const authApi = {
  login: async (email: string, password: string): Promise<TokenResponse> => {
    const { data } = await apiClient.post<{ success: boolean; data: TokenResponse }>(
      '/api/v1/auth/login', { email, password }
    )
    if (!data.success || !data.data) throw new Error('Login failed')
    return data.data
  },
  logout: async () => {
    try { await apiClient.post('/api/v1/auth/logout') } catch {}
    localStorage.clear()
    document.cookie = 'cliniq_access=; path=/; max-age=0'
  }
}


// =============================================
// FILE: src/lib/api/appointments.ts
// =============================================
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Appointment, ApiResponse } from '@/types'
import toast from 'react-hot-toast'

const keys = {
  today: ['appointments', 'today'] as const,
  all:   ['appointments'] as const,
}

export function useTodayQueue() {
  return useQuery({
    queryKey: keys.today,
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Appointment[]>>('/api/v1/appointments/today')
      return data.data ?? []
    },
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

export function useCheckIn() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.patch<ApiResponse<Appointment>>(
        `/api/v1/appointments/${id}/check-in`
      )
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all })
      toast.success('Patient checked in')
    },
    onError: () => toast.error('Check-in failed'),
  })
}

export function useAvailableSlots(doctorId: string, date: string) {
  return useQuery({
    queryKey: ['slots', doctorId, date],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<{ startTime: string; endTime: string }[]>>(
        `/api/v1/schedule/slots?doctorId=${doctorId}&date=${date}`
      )
      return data.data ?? []
    },
    enabled: Boolean(doctorId && date),
  })
}


// =============================================
// FILE: src/lib/api/patients.ts
// =============================================
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Patient, ApiResponse } from '@/types'
import toast from 'react-hot-toast'

export function usePatients(query = '', page = 0) {
  return useQuery({
    queryKey: ['patients', query, page],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Patient[]>>(
        `/api/v1/patients?q=${encodeURIComponent(query)}&page=${page}&size=20`
      )
      return { patients: data.data ?? [], meta: data.meta }
    },
    staleTime: 30_000,
  })
}

export function useCreatePatient() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (payload: Partial<Patient>) => {
      const { data } = await apiClient.post<ApiResponse<Patient>>('/api/v1/patients', payload)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patients'] })
      toast.success('Patient registered')
    },
    onError: (err: any) => toast.error(err?.response?.data?.error?.message ?? 'Failed'),
  })
}


// =============================================
// FILE: src/lib/store/slices/authSlice.ts
// =============================================
import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import type { User } from '@/types'

interface AuthState {
  user: User | null
  accessToken: string | null
  clinicId: string | null
  isAuthenticated: boolean
}

function loadState(): AuthState {
  if (typeof window === 'undefined')
    return { user: null, accessToken: null, clinicId: null, isAuthenticated: false }
  try {
    const token = localStorage.getItem('cliniq_access')
    const user  = JSON.parse(localStorage.getItem('cliniq_user') || 'null')
    const cid   = localStorage.getItem('cliniq_clinic_id')
    return { user, accessToken: token, clinicId: cid, isAuthenticated: !!token }
  } catch {
    return { user: null, accessToken: null, clinicId: null, isAuthenticated: false }
  }
}

const authSlice = createSlice({
  name: 'auth',
  initialState: loadState,
  reducers: {
    setCredentials(state, action: PayloadAction<{ user: User; accessToken: string; refreshToken: string }>) {
      const { user, accessToken, refreshToken } = action.payload
      state.user            = user
      state.accessToken     = accessToken
      state.clinicId        = user.clinicId
      state.isAuthenticated = true
      localStorage.setItem('cliniq_access',    accessToken)
      localStorage.setItem('cliniq_refresh',   refreshToken)
      localStorage.setItem('cliniq_clinic_id', user.clinicId ?? '')
      localStorage.setItem('cliniq_user',      JSON.stringify(user))
      document.cookie = `cliniq_access=${accessToken}; path=/; max-age=${14 * 60}; SameSite=Strict`
    },
    clearCredentials(state) {
      Object.assign(state, { user: null, accessToken: null, clinicId: null, isAuthenticated: false })
      localStorage.clear()
      document.cookie = 'cliniq_access=; path=/; max-age=0'
    },
  },
})
export const { setCredentials, clearCredentials } = authSlice.actions
export default authSlice.reducer
export const selectUser      = (s: any) => s.auth.user as User | null
export const selectClinicId  = (s: any) => s.auth.clinicId as string | null
export const selectIsAuthed  = (s: any) => s.auth.isAuthenticated as boolean


// =============================================
// FILE: src/lib/store/index.ts
// =============================================
import { configureStore } from '@reduxjs/toolkit'
import authReducer from './slices/authSlice'

export const store = configureStore({
  reducer: { auth: authReducer },
  middleware: (def) => def({ serializableCheck: false }),
})
export type RootState   = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch


// =============================================
// FILE: src/middleware.ts
// =============================================
import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const PUBLIC_PATHS = ['/login', '/api', '/_next', '/favicon.ico']

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) return NextResponse.next()

  const token = req.cookies.get('cliniq_access')?.value
  if (!token) return NextResponse.redirect(new URL('/login', req.url))
  return NextResponse.next()
}

export const config = { matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'] }


// =============================================
// FILE: src/app/layout.tsx
// =============================================
'use client'
import { Geist, Geist_Mono } from 'next/font/google'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { store } from '@/lib/store'
import './globals.css'

const geistSans = Geist({ subsets: ['latin'], variable: '--font-geist-sans' })
const geistMono = Geist_Mono({ subsets: ['latin'], variable: '--font-geist-mono' })
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1 } } })

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable}`}>
      <head><title>ClinIQ</title></head>
      <body>
        <Provider store={store}>
          <QueryClientProvider client={queryClient}>
            {children}
            <Toaster position="bottom-right" toastOptions={{ duration: 3000 }} />
          </QueryClientProvider>
        </Provider>
      </body>
    </html>
  )
}


// =============================================
// FILE: src/app/(auth)/login/page.tsx
// =============================================
'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useDispatch } from 'react-redux'
import { setCredentials } from '@/lib/store/slices/authSlice'
import { authApi } from '@/lib/api/auth'

export default function LoginPage() {
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')
  const dispatch = useDispatch()
  const router   = useRouter()

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const resp = await authApi.login(email, password)
      dispatch(setCredentials({
        user: resp.user,
        accessToken: resp.accessToken,
        refreshToken: resp.refreshToken,
      }))
      router.push('/reception')
    } catch (err: any) {
      setError(err?.response?.data?.error?.message ?? 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-teal-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">

        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-10 h-10 rounded-xl bg-teal-600 flex items-center justify-center">
            <svg className="w-6 h-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
            </svg>
          </div>
          <div>
            <p className="text-xl font-semibold text-gray-900">ClinIQ</p>
            <p className="text-xs text-gray-500">Clinic Management</p>
          </div>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8">
          <h1 className="text-lg font-semibold text-gray-900 mb-1">Welcome back</h1>
          <p className="text-sm text-gray-500 mb-6">Sign in to your clinic portal</p>

          {error && (
            <div className="mb-4 px-4 py-3 bg-red-50 border border-red-100 rounded-lg text-sm text-red-600">
              {error}
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email" required value={email} onChange={e => setEmail(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-lg text-sm
                           focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent
                           placeholder-gray-400"
                placeholder="you@clinic.com"
                autoComplete="email"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <input
                type="password" required value={password} onChange={e => setPassword(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-lg text-sm
                           focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent"
                placeholder="••••••••"
                autoComplete="current-password"
              />
            </div>

            <button
              type="submit" disabled={loading}
              className="w-full py-2.5 bg-teal-600 hover:bg-teal-700 disabled:opacity-60
                         text-white text-sm font-medium rounded-lg transition-colors"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>
        <p className="text-center text-xs text-gray-400 mt-6">ClinIQ · Secure clinic platform</p>
      </div>
    </div>
  )
}


// =============================================
// FILE: src/app/(dashboard)/layout.tsx
// =============================================
'use client'
import { useRouter } from 'next/navigation'
import { useSelector, useDispatch } from 'react-redux'
import { selectUser, clearCredentials } from '@/lib/store/slices/authSlice'
import { authApi } from '@/lib/api/auth'

const NAV = [
  { href: '/reception', label: 'Reception',    icon: '⊞' },
  { href: '/patients',  label: 'Patients',     icon: '♥' },
  { href: '/billing',   label: 'Billing',      icon: '₦' },
  { href: '/pharmacy',  label: 'Pharmacy',     icon: '⊕' },
  { href: '/lab',       label: 'Laboratory',   icon: '⊘' },
  { href: '/analytics', label: 'Analytics',    icon: '↗' },
]

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const user     = useSelector(selectUser)
  const dispatch = useDispatch()
  const router   = useRouter()

  async function handleLogout() {
    await authApi.logout()
    dispatch(clearCredentials())
    router.push('/login')
  }

  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-56 bg-white border-r border-gray-100 flex flex-col">
        {/* Logo */}
        <div className="px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-lg bg-teal-600 flex items-center justify-center">
              <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" />
              </svg>
            </div>
            <div>
              <p className="text-sm font-semibold text-gray-900 leading-none">ClinIQ</p>
              <p className="text-[10px] text-gray-400 mt-0.5 truncate max-w-[100px]">
                {user?.firstName} {user?.lastName}
              </p>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-3 space-y-0.5 overflow-y-auto">
          {NAV.map(({ href, label }) => {
            const active = typeof window !== 'undefined' && window.location.pathname === href
            return (
              <a key={href} href={href}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors ${
                  active
                    ? 'bg-teal-50 text-teal-700 font-medium'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                }`}
              >
                {label}
              </a>
            )
          })}
        </nav>

        {/* User */}
        <div className="px-3 py-3 border-t border-gray-100">
          <div className="flex items-center gap-2.5 px-3 py-2">
            <div className="w-7 h-7 rounded-full bg-teal-100 flex items-center justify-center
                            text-teal-700 text-xs font-semibold shrink-0">
              {user?.firstName?.[0]}{user?.lastName?.[0]}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-gray-900 truncate">{user?.firstName} {user?.lastName}</p>
              <p className="text-[10px] text-gray-400">{user?.role?.replace('_', ' ')}</p>
            </div>
            <button onClick={handleLogout} className="text-gray-400 hover:text-gray-600 text-xs" title="Logout">
              ⎋
            </button>
          </div>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {children}
      </main>
    </div>
  )
}


// =============================================
// FILE: src/app/(dashboard)/reception/page.tsx
// =============================================
'use client'
import { useState } from 'react'
import { format } from 'date-fns'
import { useTodayQueue, useCheckIn } from '@/lib/api/appointments'
import type { Appointment, AppointmentStatus } from '@/types'

const STATUS_STYLES: Record<AppointmentStatus, { bg: string; text: string; label: string }> = {
  SCHEDULED:       { bg: 'bg-blue-50',   text: 'text-blue-600',  label: 'Scheduled'   },
  CONFIRMED:       { bg: 'bg-blue-50',   text: 'text-blue-600',  label: 'Confirmed'   },
  CHECKED_IN:      { bg: 'bg-amber-50',  text: 'text-amber-600', label: 'Waiting'     },
  IN_CONSULTATION: { bg: 'bg-green-50',  text: 'text-green-600', label: 'In consult'  },
  COMPLETED:       { bg: 'bg-gray-50',   text: 'text-gray-500',  label: 'Completed'   },
  CANCELLED:       { bg: 'bg-red-50',    text: 'text-red-500',   label: 'Cancelled'   },
  NO_SHOW:         { bg: 'bg-red-50',    text: 'text-red-500',   label: 'No show'     },
}

type Filter = 'ALL' | AppointmentStatus

export default function ReceptionPage() {
  const [filter, setFilter]   = useState<Filter>('ALL')
  const { data: queue = [], isLoading, refetch } = useTodayQueue()
  const checkIn = useCheckIn()

  const stats = {
    total:    queue.length,
    waiting:  queue.filter(a => a.status === 'CHECKED_IN').length,
    active:   queue.filter(a => a.status === 'IN_CONSULTATION').length,
    done:     queue.filter(a => a.status === 'COMPLETED').length,
    upcoming: queue.filter(a => a.status === 'SCHEDULED').length,
  }

  const filtered = filter === 'ALL' ? queue : queue.filter(a => a.status === filter)
  const today = format(new Date(), 'EEEE, MMMM d')

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-sm font-semibold text-gray-900">Reception</h1>
          <p className="text-xs text-gray-400">{today}</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => refetch()} className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50 transition-colors">
            Refresh
          </button>
          <button className="px-3 py-1.5 text-xs bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors font-medium">
            + New appointment
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-4">
        {/* Stats */}
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {[
            { label: 'Total today',     value: stats.total,    color: 'text-gray-900'   },
            { label: 'Waiting',         value: stats.waiting,  color: 'text-amber-600'  },
            { label: 'In consultation', value: stats.active,   color: 'text-green-600'  },
            { label: 'Completed',       value: stats.done,     color: 'text-gray-400'   },
          ].map(s => (
            <div key={s.label} className="bg-white rounded-xl border border-gray-100 p-4">
              <p className={`text-2xl font-semibold ${s.color}`}>{s.value}</p>
              <p className="text-xs text-gray-400 mt-1">{s.label}</p>
            </div>
          ))}
        </div>

        {/* Queue table */}
        <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
          {/* Table header */}
          <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between gap-3 flex-wrap">
            <h2 className="text-sm font-medium text-gray-900">Today's queue</h2>
            <div className="flex items-center gap-1.5 flex-wrap">
              {(['ALL', 'CHECKED_IN', 'IN_CONSULTATION', 'SCHEDULED', 'COMPLETED'] as const).map(f => (
                <button key={f} onClick={() => setFilter(f)}
                  className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                    filter === f
                      ? 'border-teal-200 bg-teal-50 text-teal-700 font-medium'
                      : 'border-gray-100 text-gray-500 hover:border-gray-200'
                  }`}>
                  {f === 'ALL' ? 'All' : f === 'CHECKED_IN' ? 'Waiting' : f === 'IN_CONSULTATION' ? 'Active' : f === 'COMPLETED' ? 'Done' : 'Upcoming'}
                </button>
              ))}
            </div>
          </div>

          {/* Column headers */}
          <div className="grid grid-cols-[32px_1fr_130px_70px_100px_90px_80px] gap-3 px-5 py-2
                          bg-gray-50 text-xs font-medium text-gray-400 border-b border-gray-100">
            <span>#</span>
            <span>Patient</span>
            <span>Doctor</span>
            <span>Time</span>
            <span>Type</span>
            <span>Status</span>
            <span></span>
          </div>

          {/* Rows */}
          {isLoading ? (
            <div className="py-12 text-center text-sm text-gray-400">Loading queue…</div>
          ) : filtered.length === 0 ? (
            <div className="py-12 text-center text-sm text-gray-400">
              No appointments in this category
            </div>
          ) : (
            <div className="divide-y divide-gray-50">
              {filtered.map((appt, idx) => {
                const s = STATUS_STYLES[appt.status] ?? STATUS_STYLES.SCHEDULED
                const initials = appt.patientName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()
                return (
                  <div key={appt.id}
                    className="grid grid-cols-[32px_1fr_130px_70px_100px_90px_80px] gap-3
                               items-center px-5 py-3 hover:bg-gray-50 transition-colors text-sm">
                    <span className="text-xs font-medium text-gray-400">{appt.queueNumber ?? idx + 1}</span>

                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-7 h-7 rounded-full bg-teal-50 flex items-center justify-center
                                      text-teal-600 text-[10px] font-semibold shrink-0">
                        {initials}
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-medium text-gray-900 truncate">{appt.patientName}</p>
                        <p className="text-[10px] text-gray-400">{appt.patientPhone}</p>
                      </div>
                    </div>

                    <span className="text-xs text-gray-500 truncate">{appt.doctorName ?? '—'}</span>
                    <span className="text-xs tabular-nums text-gray-900">{appt.startTime}</span>
                    <span className="text-xs text-gray-500">{appt.type.replace('_', ' ')}</span>

                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium ${s.bg} ${s.text}`}>
                      {s.label}
                    </span>

                    <div>
                      {appt.status === 'SCHEDULED' && (
                        <button
                          onClick={() => checkIn.mutate(appt.id)}
                          disabled={checkIn.isPending}
                          className="px-2.5 py-1 text-[10px] font-medium rounded-lg
                                     bg-teal-50 border border-teal-200 text-teal-700
                                     hover:bg-teal-100 transition-colors disabled:opacity-50"
                        >
                          Check in
                        </button>
                      )}
                      {appt.status === 'CHECKED_IN' && (
                        <button className="px-2.5 py-1 text-[10px] border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50">
                          View
                        </button>
                      )}
                      {appt.status === 'IN_CONSULTATION' && (
                        <span className="text-[10px] text-green-600 font-medium">● Active</span>
                      )}
                      {appt.status === 'COMPLETED' && (
                        <button className="px-2.5 py-1 text-[10px] border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50">
                          Notes
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}


// =============================================
// FILE: src/app/(dashboard)/patients/page.tsx
// =============================================
'use client'
import { useState } from 'react'
import { usePatients } from '@/lib/api/patients'
import { format } from 'date-fns'

export default function PatientsPage() {
  const [query, setQuery] = useState('')
  const [page,  setPage]  = useState(0)
  const { data, isLoading } = usePatients(query, page)
  const { patients = [], meta } = data ?? {}

  return (
    <div className="flex flex-col h-full">
      <div className="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-sm font-semibold text-gray-900">Patients</h1>
          <p className="text-xs text-gray-400">{meta?.total ?? '—'} registered</p>
        </div>
        <button className="px-3 py-1.5 text-xs bg-teal-600 text-white rounded-lg hover:bg-teal-700 font-medium">
          + Register patient
        </button>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-4">
        <input
          value={query} onChange={e => { setQuery(e.target.value); setPage(0) }}
          placeholder="Search by name, phone, or patient number…"
          className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm
                     focus:outline-none focus:ring-2 focus:ring-teal-500 max-w-lg"
        />

        <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
          <div className="grid grid-cols-[1fr_120px_100px_100px_80px] gap-3 px-5 py-2
                          bg-gray-50 text-xs font-medium text-gray-400 border-b border-gray-100">
            <span>Patient</span><span>Phone</span><span>Patient #</span><span>Registered</span><span></span>
          </div>

          {isLoading ? (
            <div className="py-12 text-center text-sm text-gray-400">Searching…</div>
          ) : patients.length === 0 ? (
            <div className="py-12 text-center text-sm text-gray-400">No patients found</div>
          ) : (
            <div className="divide-y divide-gray-50">
              {patients.map(p => {
                const initials = `${p.firstName[0]}${p.lastName[0]}`.toUpperCase()
                return (
                  <div key={p.id} className="grid grid-cols-[1fr_120px_100px_100px_80px] gap-3
                                              items-center px-5 py-3 hover:bg-gray-50 transition-colors">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-7 h-7 rounded-full bg-teal-50 flex items-center justify-center
                                      text-teal-600 text-[10px] font-semibold shrink-0">
                        {initials}
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-medium text-gray-900 truncate">{p.firstName} {p.lastName}</p>
                        <p className="text-[10px] text-gray-400">{p.gender ?? '—'}</p>
                      </div>
                    </div>
                    <span className="text-xs text-gray-600">{p.phone}</span>
                    <span className="text-xs text-gray-400 font-mono">{p.patientNumber ?? '—'}</span>
                    <span className="text-xs text-gray-400">
                      {format(new Date(p.createdAt), 'MMM d, yyyy')}
                    </span>
                    <button className="text-xs text-teal-600 hover:text-teal-700 font-medium">
                      View →
                    </button>
                  </div>
                )
              })}
            </div>
          )}

          {meta && meta.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
              <span>Page {page + 1} of {meta.totalPages}</span>
              <div className="flex gap-2">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                  className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">← Prev</button>
                <button disabled={page >= meta.totalPages - 1} onClick={() => setPage(p => p + 1)}
                  className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">Next →</button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}


// =============================================
// FILE: src/app/page.tsx  (root redirect)
// =============================================
import { redirect } from 'next/navigation'
export default function RootPage() { redirect('/reception') }


// =============================================
// FILE: src/app/globals.css
// =============================================
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root { --brand: #0d9488; }
  body  { @apply text-gray-900 antialiased; }
}


// =============================================
// FILE: tailwind.config.ts
// =============================================
import type { Config } from 'tailwindcss'

export default {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        teal: {
          50:  '#f0fdfa', 100: '#ccfbf1', 200: '#99f6e4',
          400: '#2dd4bf', 500: '#14b8a6', 600: '#0d9488',
          700: '#0f766e', 800: '#115e59', 900: '#134e4a',
        },
      },
      fontFamily: { sans: ['var(--font-geist-sans)', 'system-ui'] },
    },
  },
  plugins: [],
} satisfies Config


// =============================================
// FILE: next.config.ts
// =============================================
import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_API_URL}/api/:path*`,
      },
    ]
  },
}

export default nextConfig
