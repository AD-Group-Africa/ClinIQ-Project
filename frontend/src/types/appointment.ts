
export type AppointmentStatus =
  | 'SCHEDULED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_CONSULTATION'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'RESCHEDULED';

export type AppointmentType =
  | 'CONSULTATION'
  | 'FOLLOW_UP'
  | 'PROCEDURE'
  | 'CHECK_UP'
  | 'WALK_IN'
  | 'TELECONSULTATION';

export interface AppointmentResponse {
  id: string;
  patientId: string;
  patientName: string;
  patientPhone: string;
  doctorId: string | null;
  doctorName: string | null;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  type: AppointmentType;
  queueNumber: number | null;
  chiefComplaint: string | null;
  createdAt: string;
}

export interface AppointmentListEnvelope {
  success: boolean;
  data: AppointmentResponse[];
  error: {
    message: string;
  } | null;
}
