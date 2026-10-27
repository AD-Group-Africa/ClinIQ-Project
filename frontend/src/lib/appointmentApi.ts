
import api from '@/lib/api';
import type {
  AppointmentListEnvelope,
  AppointmentResponse,
} from '@/types/appointment';

/**
 * Fetch today's appointments from the confirmed backend endpoint.
 * Returns real appointment records; it does not create mock data.
 */
export async function getTodaysAppointments(): Promise<AppointmentResponse[]> {
  const response = await api.get<AppointmentListEnvelope>('/appointments/today');

  const envelope = response.data;

  if (!envelope.success) {
    throw new Error(
      envelope.error?.message ?? "Couldn't load today's appointments.",
    );
  }

  return envelope.data;
}
