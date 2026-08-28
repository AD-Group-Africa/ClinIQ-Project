# Frontend Handoff — ClinIQ Backend API

## Base URL
`https://api.cliniq.localhost/api/v1/`

## Authentication
- **Scheme**: Bearer JWT
- **Login**: `POST /api/v1/auth/login` with `{phone, password}` → returns JWT token
- **Token usage**: Include `Authorization: Bearer <token>` header on all API calls
- **Clinic isolation**: Add `X-Clinic-ID: <uuid>` header; enforced for non-SUPER_ADMIN roles

## Roles & RBAC
| Role | Permissions |
|------|-------------|
| `SUPER_ADMIN` | Full access across all clinics |
| `CLINIC_ADMIN` | Manage clinic: patients, appointments, billing, inventory |
| `RECEPTIONIST` | Book appointments, check-in, basic patient data |
| `DOCTOR` | Create appointments, start/complete consultations, prescribe, view lab results |
| `PATIENT` | View own appointments, book via portal, receive SMS reminders |

**Authorisation annotations**: `@PreAuthorize("hasAnyRole('ROLE_CLINIC_ADMIN','ROLE_RECEPTIONIST',...)")`

## API Endpoints (OpenAPI / Swagger)
- **UI**: `/swagger-ui.html`
- **Spec**: `/v3/api-docs`
- All endpoints versioned under `/api/v1/`

### Patient Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/patients` | Create patient (unique phone per clinic) |
| `GET` | `/api/v1/patients` | List patients with pagination & search |
| `GET` | `/api/v1/patients/{id}` | Get patient by ID |
| `PUT` | `/api/v1/patients/{id}` | Update patient |

### Appointment Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/appointments` | Create appointment |
| `GET` | `/api/v1/appointments/queue/today` | Get today's queue |
| `GET` | `/api/v1/appointments/patient/{patientId}` | Patient's appointment history (paginated) |
| `POST` | `/api/v1/appointments/{id}/check-in` | Check in patient |
| `POST` | `/api/v1/appointments/{id}/start-consultation` | Start consultation |
| `POST` | `/api/v1/appointments/{id}/complete` | Mark appointment complete |
| `POST` | `/api/v1/appointments/{id}/cancel` | Cancel appointment |

### Consultation Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/consultations/start` | Start consultation record |
| `POST` | `/api/v1/consultations/{id}` | Update consultation (diagnosis, treatment plan) |
| `POST` | `/api/v1/consultations/{id}/complete` | Complete consultation |
| `POST` | `/api/v1/consultations/{id}/prescription` | Add prescription |

### Pharmacy Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/pharmacy/drugs` | Add new drug |
| `GET` | `/api/v1/pharmacy/drugs` | Search drugs with pagination |
| `POST` | `/api/v1/pharmacy/stock` | Add stock batch |
| `POST` | `/api/v1/pharmacy/dispense` | Dispense medication |
| `GET` | `/api/v1/pharmacy/stock/alerts` | Get low stock / expiring alerts |

### Laboratory Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/lab/tests` | Add test to catalog |
| `POST` | `/api/v1/lab/requests` | Create lab request |
| `POST` | `/api/v1/lab/requests/{id}/status` | Update request status |
| `POST` | `/api/v1/lab/requests/{id}/results` | Enter test results |
| `GET` | `/api/v1/lab/requests/pending` | Get pending requests |

### Billing Module
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/billing/invoices` | Create invoice |
| `GET` | `/api/v1/billing/invoices` | List invoices with pagination, filtering, clinic isolation |
| `GET` | `/api/v1/billing/invoices/{id}` | Get specific invoice |
| `POST` | `/api/v1/billing/payments` | Record payment |
| `POST` | `/api/v1/billing/mpesa/initiate` | Initiate M-Pesa STK push |
| `POST` | `/api/v1/billing/mpesa/callback` | Handle M-Pesa callback |

## Sample Request/Response

### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "phone": "0712345678",
  "password": "Password123!"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "11111111-1111-1111-1111-111111111111",
      "email": "doctor@test.com",
      "role": "DOCTOR",
      "clinicId": "22222222-2222-2222-2222-222222222222"
    }
  },
  "error": null,
  "meta": null
}
```

### Create Patient
```http
POST /api/v1/patients
Authorization: Bearer <token>
X-Clinic-ID: <clinic-uuid>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "phone": "07123456789",
  "email": "john@test.com",
  "dateOfBirth": "1990-01-01",
  "gender": "Male",
  "nationalId": "NHIF001",
  "bloodGroup": "O+",
  "allergies": ["Penicillin"],
  "emergencyContactName": "Jane Doe",
  "emergencyContactPhone": "07987654321",
  "insuranceProvider": "Test Insurance",
  "insuranceNumber": "INS001",
  "address": "1 Test Street, Nairobi",
  "notes": null,
  "tags": []
}
```

Response:
```json
{
  "success": true,
  "data": {
    "id": "33333333-3333-3333-3333-333333333333",
    "patientNumber": "PT-2024-00001",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "07123456789",
    "email": "john@test.com",
    "dateOfBirth": "1990-01-01",
    "gender": "Male",
    "bloodGroup": "O+",
    "emergencyContactName": "Jane Doe",
    "emergencyContactPhone": "07987654321",
    "insuranceProvider": "Test Insurance",
    "insuranceNumber": "INS001",
    "address": "1 Test Street, Nairobi",
    "notes": null,
    "tags": [],
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "error": null,
  "meta": null
}
```

### List Appointments (paginated)
```http
GET /api/v1/appointments/patient/{patientId}?page=0&size=10
Authorization: Bearer <token>
X-Clinic-ID: <clinic-uuid>
```

Response (ApiResponse with Page):
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "44444444-4444-4444-4444-444444444444",
        "patientId": "33333333-3333-3333-3333-333333333333",
        "patientName": "John Doe",
        "doctorId": "11111111-1111-1111-1111-111111111111",
        "doctorName": "Dr. Test Doctor",
        "appointmentDate": "2024-02-20",
        "startTime": "10:00",
        "endTime": "11:00",
        "status": "SCHEDULED",
        "queueNumber": 1,
        "type": "Consultation",
        "chiefComplaint": "Routine check-up",
        "queueNumber": 1,
        "bookedBy": "11111111-1111-1111-1111-111111111111",
        "bookedVia": "PORTAL"
      }
    ],
    "pageMeta": {
      "totalElements": 1,
      "totalPages": 1,
      "size": 10,
      "number": 0
    }
  },
  "error": null,
  "meta": null
}
```

### Create Invoice
```http
POST /api/v1/billing/invoices
Authorization: Bearer <token>
X-Clinic-ID: <clinic-uuid>
Content-Type: application/json

{
  "patientId": "33333333-3333-3333-3333-333333333333",
  "appointmentId": "44444444-4444-4444-4444-444444444444",
  "lineItems": [
    { "description": "Consultation fee", "quantity": 1, "unitPrice": 500 },
    { "description": "Registration fee", "quantity": 1, "unitPrice": 200 }
  ],
  "notes": "Follow-up consultation"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "id": "55555555-5555-5555-5555-555555555555",
    "invoiceNumber": "INV-2024-00001",
    "patientId": "33333333-3333-3333-3333-333333333333",
    "patientName": "John Doe",
    "appointmentId": "44444444-4444-4444-4444-444444444444",
    "lineItems": [...],
    "subtotal": 700,
    "discount": 0,
    "tax": 70,
    "total": 770,
    "paidAmount": 0,
    "balance": 770,
    "status": "PENDING",
    "notes": "Follow-up consultation",
    "dueDate": "2024-02-25",
    "issuedAt": "2024-01-15T10:30:00Z",
    "payments": [],
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "error": null,
  "meta": null
}
```

### M-Pesa STK Initiate
```http
POST /api/v1/billing/mpesa/initiate
Authorization: Bearer <token>
X-Clinic-ID: <clinic-uuid>
Content-Type: application/json

{
  "invoiceId": "55555555-5555-5555-5555-555555555555",
  "phone": "0712345678"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "checkoutRequestId": "ws_COOKBOTW_1234567890",
    "message": "STK push sent successfully"
  },
  "error": null,
  "meta": null
}
```

## Error Responses

All errors follow the `ApiResponse<T>` pattern with `error` field filled:

### 401 Unauthorized
```json
{
  "success": false,
  "error": "Unauthenticated - valid JWT token required",
  "data": null,
  "meta": null
}
```

### 403 Forbidden (cross-clinic access)
```json
{
  "success": false,
  "error": "Access denied - clinic ID mismatch",
  "data": null,
  "meta": null
}
```

### 422 Validation Error
```json
{
  "success": false,
  "error": "Validation failed",
  "data": null,
  "meta": null
}
```

### 409 Conflict
```json
{
  "success": false,
  "error": "This time slot is already booked",
  "data": null,
  "meta": null
}
```

## Known Limitations
- Docker Compose from-zero not fully verified on Windows (gradlew.bat path issue - using `gradlew` for Linux, `gradlew.bat` for Windows)
- Security headers filter: syntax initially caused compile errors; resolved via Spring Security DSL
- No automated backup/restore scripts configured (see BACKUP_RESTORE.md)
- Integration tests not yet written for full clinical flow (login→patient→appointment→consult→prescribe→lab→invoice→payment→dispense)
- OpenAPI docs being synced incrementally with `@Operation`/`@Schema` annotations added per domain

## Test Accounts (for frontend integration)
| Role | Phone | Password | Clinic |
|------|-------|----------|--------|
| SUPER_ADMIN | admin@test.com | Admin123! | Demo clinic |
| DOCTOR | doctor@test.com | Doctor123! | Demo clinic |
| PATIENT | patient@test.com | Patient123! | Demo clinic |

## Integration Notes for Frontend
1. Always include `X-Clinic-ID` header - the backend enforces clinic isolation
2. JWT tokens expire after session; refresh upon 401
3. Use `GET /api/v1/billing/invoices?page=0&size=10` for paginated lists
4. DTOs are used everywhere - never expose JPA entities directly
5. All timestamps are ISO 8601 format in UTC
6. Phone numbers are stored in E.164 format (+254712345678)