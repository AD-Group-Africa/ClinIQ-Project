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

export interface Appointment {
  id: string;
  patientId: string;
  patientName: string;
  doctorId?: string;
  doctorName?: string;
  appointmentDate: string;
  startTime: string;
  endTime?: string;
  status: string;
  queueNumber?: number;
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

export interface DrugStockResponse {
  drugId: string;
  name: string;
  genericName?: string;
  category: string;
  unit: string;
  totalStock: number;
  reorderLevel: number;
  sellingPrice: number;
  stockStatus: string;
  batchesExpiringSoon: number;
  nearestExpiry?: string;
}

export interface LabTest {
  id: string;
  name: string;
  code?: string;
  category?: string;
  price: number;
}

export interface LabRequest {
  id: string;
  requestNumber?: string;
  patientName: string;
  status: string;
  priority: string;
  tests: any[];
  dueAt?: string;
}
