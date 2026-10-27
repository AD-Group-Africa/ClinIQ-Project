export interface Patient {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  patientNumber?: string;
}


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

export interface Appointment {
  id: string;
  patientName: string;
  patientPhone: string;
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

export interface QueueItem {
  id: string;
  queueNumber: number | null;
  patientName: string;
  patientPhone: string;
  doctorName: string | null;
  time: string;
  status: AppointmentStatus;
  type: AppointmentType;
}

export interface QueueSummary {
  waiting: QueueItem[];
  inProgress: QueueItem[];
  completed: number;
  upcoming: number;
  estimatedWaitMinutes: number;
}


export interface Invoice {
  id: string;
  invoiceNumber: string;
  patientId: string;
  patientName: string;
  total: number;
  paidAmount: number;
  status: string;
  issuedAt: string;
}
