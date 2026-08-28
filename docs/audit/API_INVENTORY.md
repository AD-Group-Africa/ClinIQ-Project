# ClinIQ Backend — API Inventory

**Version**: v1
**Base Tag**: `pre-audit`
**Total Endpoints**: ~65 across all controllers

## API Conventions

- **Base path**: `/api/v1/`
- **Response format**: `ApiResponse<T>` wrapper with `data`, `meta`, `errors`
- **Error format**: Standardized `ApiError` with `code`, `message`, `timestamp`, `path`, `details`
- **Status codes**: 200 (OK), 201 (Created), 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden), 404 (Not Found), 422 (Validation Error), 500 (Internal Error)

## Controller Endpoints

### Auth (`/api/v1/auth`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/register` | Register new user | Public |
| POST | `/login` | Login & get JWT | Public |
| POST | `/refresh` | Refresh JWT token | Public |
| POST | `/logout` | Logout, blacklist token | User |
| GET | `/me` | Get current user profile | User |

### Patient (`/api/v1/patients`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/` | Create patient | Patient/Doctor/Receptionist |
| GET | `/` | List patients — with pagination, sorting, filtering | Patient/Doctor/Receptionist |
| GET | `/{id}` | Get patient by ID | Patient (own clinic), Doctor (own clinic) |
| PUT | `/{id}` | Update patient | Patient (own clinic), Doctor (own clinic) |

### Appointment (`/api/v1/appointments`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/` | Create appointment | Doctor/Receptionist |
| GET | `/` | List appointments — with pagination, sorting, filtering by clinic/patient/date | Patient (own), Doctor (own clinic), Receptionist |
| GET | `/{id}` | Get appointment by ID | Patient (own clinic), Doctor (own clinic) |
| PUT | `/{id}` | Update appointment status | Doctor/Receptionist |
| PUT | `/{id}/cancel` | Cancel appointment | Doctor/Receptionist |
| GET | `/doctor/{doctorId}/schedule` | Get doctor schedule | Doctor (own clinic) |

### Billing (`/api/v1/billing`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/invoices` | Create invoice | Staff |
| GET | `/invoices/{id}` | Get invoice by ID | Patient (own clinic), Staff |
| ❌ **GET /invoices** | **List invoices** | **MISSING** | **Staff** |
| POST | `/payments` | Record payment | Staff |
| POST | `/mpesa/initiate` | Initiate M-Pesa STK push | Staff |
| POST | `/mpesa/callback` | Handle M-Pesa webhook | N/A (webhook) |
| GET | `/reports/revenue` | Revenue report | Admin |

**⚠️ MISSING ENDPOINT**: `GET /api/v1/billing/invoices` — needed for clinic invoice listing

### Lab (`/api/v1/lab`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/requests` | Create lab request | Doctor/Receptionist |
| GET | `/` | List lab requests — with pagination, filtering by patient/clinic/status | Patient (own), Doctor (own clinic), Receptionist |
| GET | `/{id}` | Get lab request by ID | Patient (own clinic), Doctor (own clinic) |
| PUT | `/{id}` | Update lab request status | Doctor/Receptionist |
| GET | `/results/{requestId}` | Get lab results | Patient (own clinic), Doctor (own clinic) |

### Pharmacy (`/api/v1/pharmacy`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/drugs` | Create drug entry | Staff |
| GET | `/drugs` | List drugs — with pagination, filtering | Staff |
| GET | `/drugs/{id}` | Get drug by ID | Staff |
| POST | `/batches` | Create drug batch | Staff |
| GET | `/batches/{drugId}` | Get batches for drug | Staff |
| POST | `/dispense` | Dispense medication | Staff |
| GET | `/inventory/low-stock` | Low stock report | Staff |

### Consultation (`/api/v1/consultations`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/` | Create consultation | Doctor |
| GET | `/{id}` | Get consultation by ID | Patient (own clinic), Doctor (own clinic) |
| PUT | `/{id}` | Update consultation | Doctor |
| PUT | `/{id}/complete` | Mark consultation complete | Doctor |

### CRM (`/api/v1/crm`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/campaigns` | Create campaign | Staff |
| GET | `/campaigns` | List campaigns | Staff |
| GET | `/campaigns/{id}` | Get campaign by ID | Staff |
| PUT | `/campaigns/{id}` | Update campaign | Staff |

### Analytics (`/api/v1/analytics`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/` | Analytics overview | Admin |
| GET | `/patients` | Patient statistics | Admin |
| GET | `/appointments` | Appointment statistics | Admin |
| GET | `/revenue` | Revenue trends | Admin |

## Standard Error Response Model

```json
{
  "code": "PATIENT_NOT_FOUND",
  "message": "Patient could not be found",
  "timestamp": "2026-08-22T12:00:00.000+00:00",
  "path": "/api/v1/patients/123e4567-abcd-efgh-ijkl-mno",
  "details": []
}
```

## Pagination Standard (List Endpoints)

- **Page**: `page=0` (default), `page=N` (requested)
- **Size**: `size=20` (default), `size=N` (requested)
- **Sort**: `sort=fieldName` (default), `sort=fieldName,dir` (desc/asc)
- **Example**: `GET /api/v1/patients?page=0&size=20&sort=firstName,asc&filter=clinic:123`

## Filtering Standard (List Endpoints)

- **Clinic filter**: `clinic=UUID`
- **Status filter**: `status=SCHEDULED`
- **Date range**: `date_from=2026-01-01&date_to=2026-12-31`
- **Example**: `GET /api/v1/appointments?clinic=123&status=SCHEDULED&date_from=2026-01-01`

## Sorting Standard (List Endpoints)

- **Direction**: `asc` or `desc` (default `asc`)
- **Example**: `GET /api/v1/patients?sort=lastName,desc`