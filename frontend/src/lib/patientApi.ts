
import api from '@/lib/api';
import type {
  PatientListEnvelope,
  PatientListMeta,
  PatientResponse,
} from '@/types/patient';

export interface PatientSearchResult {
  patients: PatientResponse[];
  meta: PatientListMeta;
}

export interface SearchPatientsParams {
  query?: string;
  page?: number;
  size?: number;
}

/**
 * Search patients using the confirmed backend endpoint.
 * The page index is zero-based.
 */
export async function searchPatients({
  query = '',
  page = 0,
  size = 20,
}: SearchPatientsParams = {}): Promise<PatientSearchResult> {
  const response = await api.get<PatientListEnvelope>('/patients', {
    params: {
      q: query,
      page,
      size,
    },
  });

  const envelope = response.data;

  if (!envelope.success) {
    throw new Error(envelope.error.message);
  }

  return {
    patients: envelope.data,
    meta: envelope.meta,
  };
}
