
import type { ApiError } from '@/types/auth';

/** Gender values supported by the backend. */
export type Gender =
  | 'MALE'
  | 'FEMALE'
  | 'OTHER'
  | 'PREFER_NOT_TO_SAY';

/** Blood group values supported by the backend. */
export type BloodGroup =
  | 'A_POS'
  | 'A_NEG'
  | 'B_POS'
  | 'B_NEG'
  | 'AB_POS'
  | 'AB_NEG'
  | 'O_POS'
  | 'O_NEG'
  | 'UNKNOWN';

/** Patient data returned by the backend. */
export interface PatientResponse {
  id: string;
  patientNumber: string | null;
  firstName: string;
  lastName: string;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  gender: Gender | null;
  bloodGroup: BloodGroup;
  allergies: string[];
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  insuranceProvider: string | null;
  insuranceNumber: string | null;
  address: string | null;
  notes: string | null;
  tags: string[];
  createdAt: string;
}

/** Pagination information returned by the backend. */
export interface PatientListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

/** Response envelope for the patient-list endpoint. */
export type PatientListEnvelope =
  | {
      success: true;
      data: PatientResponse[];
      error: null;
      meta: PatientListMeta;
    }
  | {
      success: false;
      data: null;
      error: ApiError;
      meta: null;
    };
