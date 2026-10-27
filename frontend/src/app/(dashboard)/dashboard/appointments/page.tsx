
'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  IconCalendarEvent,
  IconRefresh,
  IconAlertCircle,
  IconClock,
} from '@tabler/icons-react';

import { getTodaysAppointments } from '@/lib/appointmentApi';
import type {
  AppointmentResponse,
  AppointmentStatus,
} from '@/types/appointment';

const statusStyles: Record<AppointmentStatus, string> = {
  SCHEDULED: 'bg-blue-50 text-blue-700',
  CONFIRMED: 'bg-indigo-50 text-indigo-700',
  CHECKED_IN: 'bg-amber-50 text-amber-700',
  IN_CONSULTATION: 'bg-purple-50 text-purple-700',
  COMPLETED: 'bg-green-50 text-green-700',
  CANCELLED: 'bg-red-50 text-red-700',
  NO_SHOW: 'bg-gray-100 text-gray-600',
  RESCHEDULED: 'bg-orange-50 text-orange-700',
};

function formatTime(time: string): string {
  const [hoursString, minutes] = time.split(':');
  const hours = Number(hoursString);

  if (!Number.isFinite(hours) || !minutes) {
    return time;
  }

  const period = hours >= 12 ? 'PM' : 'AM';
  const displayHours = hours % 12 || 12;

  return `${displayHours}:${minutes} ${period}`;
}

function formatLabel(value: string): string {
  return value
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
}

function getTodayLabel(): string {
  return new Intl.DateTimeFormat('en-KE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());
}

export default function AppointmentsPage() {
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadAppointments = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError(null);

    try {
      const data = await getTodaysAppointments();
      setAppointments(data);
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : "We couldn't load today's appointments. Please try again.";

      setError(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadAppointments();
  }, [loadAppointments]);

  return (
    <main className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <IconCalendarEvent
              size={25}
              stroke={1.8}
              className="text-ink"
              aria-hidden="true"
            />
            <h1 className="text-2xl font-semibold text-ink">
              Appointments
            </h1>
          </div>

          <p className="mt-1 text-sm text-muted">
            Today&apos;s appointments and patient schedule.
          </p>

          <p className="mt-2 text-sm font-medium text-ink">
            {getTodayLabel()}
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadAppointments(true)}
          disabled={loading || refreshing}
          className="inline-flex items-center justify-center gap-2 rounded-lg border border-line bg-white px-4 py-2.5 text-sm font-medium text-ink transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <IconRefresh
            size={18}
            className={refreshing ? 'animate-spin' : ''}
            aria-hidden="true"
          />
          {refreshing ? 'Refreshing...' : 'Refresh'}
        </button>
      </header>

      <section className="rounded-xl border border-line bg-white p-4 sm:p-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-ink">
              Today&apos;s schedule
            </h2>
            <p className="mt-1 text-sm text-muted">
              {loading
                ? 'Loading appointments...'
                : `${appointments.length} appointment${
                    appointments.length === 1 ? '' : 's'
                  } returned`}
            </p>
          </div>

          <IconClock
            size={22}
            className="text-muted"
            aria-hidden="true"
          />
        </div>

        {loading ? (
          <div
            className="flex min-h-48 items-center justify-center gap-3 text-sm text-muted"
            role="status"
          >
            <span className="h-5 w-5 animate-spin rounded-full border-2 border-gray-200 border-t-teal-700" />
            Loading today&apos;s appointments...
          </div>
        ) : error ? (
          <div
            className="mt-5 rounded-lg border border-red-200 bg-red-50 p-5"
            role="alert"
          >
            <div className="flex items-start gap-3">
              <IconAlertCircle
                size={22}
                className="mt-0.5 shrink-0 text-red-700"
                aria-hidden="true"
              />

              <div className="flex-1">
                <h3 className="font-semibold text-red-800">
                  Unable to load appointments
                </h3>
                <p className="mt-1 break-words text-sm text-red-700">
                  {error}
                </p>

                <button
                  type="button"
                  onClick={() => void loadAppointments()}
                  className="mt-3 rounded-md border border-red-300 bg-white px-3 py-2 text-sm font-medium text-red-800 hover:bg-red-100"
                >
                  Try again
                </button>
              </div>
            </div>
          </div>
        ) : appointments.length === 0 ? (
          <div className="flex min-h-48 flex-col items-center justify-center px-4 py-8 text-center">
            <IconCalendarEvent
              size={36}
              stroke={1.5}
              className="text-muted"
              aria-hidden="true"
            />
            <h3 className="mt-3 font-semibold text-ink">
              No appointments found
            </h3>
            <p className="mt-1 max-w-sm text-sm text-muted">
              The backend returned no appointments for today. Appointments
              will appear here when they are available.
            </p>
          </div>
        ) : (
          <div className="mt-5 overflow-x-auto">
            <table className="w-full min-w-[900px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-y border-line bg-gray-50 text-muted">
                  <th scope="col" className="px-4 py-3 font-medium">
                    Queue
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Patient
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Doctor
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Time
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Type
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Status
                  </th>
                </tr>
              </thead>

              <tbody className="divide-y divide-line">
                {appointments.map((appointment) => (
                  <tr
                    key={appointment.id}
                    className="align-top transition hover:bg-gray-50"
                  >
                    <td className="px-4 py-4 font-medium text-ink">
                      {appointment.queueNumber ?? '—'}
                    </td>

                    <td className="px-4 py-4">
                      <p className="font-medium text-ink">
                        {appointment.patientName}
                      </p>
                      <p className="mt-1 text-xs text-muted">
                        {appointment.patientPhone || 'No phone provided'}
                      </p>
                      {appointment.chiefComplaint && (
                        <p className="mt-2 max-w-xs text-xs text-muted">
                          {appointment.chiefComplaint}
                        </p>
                      )}
                    </td>

                    <td className="px-4 py-4 text-ink">
                      {appointment.doctorName ?? 'Not assigned'}
                    </td>

                    <td className="whitespace-nowrap px-4 py-4 text-ink">
                      {formatTime(appointment.startTime)}
                      <span className="text-muted"> – </span>
                      {formatTime(appointment.endTime)}
                    </td>

                    <td className="px-4 py-4 text-ink">
                      {formatLabel(appointment.type)}
                    </td>

                    <td className="px-4 py-4">
                      <span
                        className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium ${
                          statusStyles[appointment.status]
                        }`}
                      >
                        {formatLabel(appointment.status)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
