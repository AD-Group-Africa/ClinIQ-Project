# ClinIQ API

Base path: `/api/v1` · Auth: `Authorization: Bearer <accessToken>` · Live spec: `/v3/api-docs` (Swagger UI: `/swagger-ui.html`)

## Conventions

### Response envelope
Every endpoint returns `ApiResponse<T>`:

```json
{
  "success": true,
  "data": { },
  "meta": { "page": 0, "size": 20, "total": 42, "totalPages": 3 },
  "error": null
}
```

### Error format
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PATIENT_NOT_FOUND",
    "message": "Patient not found",
    "details": null,
    "timestamp": "2026-08-28T12:00:00.000Z"
  },
  "meta": null
}
```

| HTTP | `error.code` | Meaning |
|---|---|---|
| 400 | `BAD_REQUEST`, `VALIDATION_ERROR` | Malformed input / validation failed (`details` maps field → message) |
| 401 | `UNAUTHORIZED` | Missing/invalid/expired/revoked token, bad credentials |
| 403 | `FORBIDDEN` | Authenticated but not allowed (role or clinic mismatch) |
| 404 | `NOT_FOUND` | Resource not found (also returned for cross-clinic access, so existence is not leaked) |
| 409 | `CONFLICT` | Duplicate (email, phone, slug, invoice number) |
| 500 | `INTERNAL_ERROR` | Unexpected server error (no stack traces leaked) |

### Pagination
`?page=0&size=20&sort=field,asc|desc` (page defaults to 0, size to 20). Paginated responses carry `meta`.

### Multi-tenancy
- The tenant (clinic) context is **derived from the signed JWT**, never from client input.
- `X-Clinic-ID` is optional and advisory: for non-`SUPER_ADMIN` tokens it is verified against the JWT's clinic and a mismatch is rejected with `403`. It can never escalate access.
- Cross-clinic resource lookups return `404` (not `403`) so data existence is not leaked.

## Endpoint Inventory

### Auth — `/api/v1/auth`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/register` | Register a new clinic + its first CLINIC_ADMIN | Public |
| POST | `/login` | Authenticate, returns access + refresh tokens | Public |
| POST | `/refresh` | Rotate refresh token (old is revoked) | Public |
| POST | `/logout` | Revoke refresh token + blacklist access token | Authenticated |
| POST | `/staff` | Create a staff user in the caller's clinic | CLINIC_ADMIN, SUPER_ADMIN |
| PATCH | `/password` | Change own password (revokes refresh tokens) | Authenticated |
| GET | `/me` | Current user profile | Authenticated |

### Patients — `/api/v1/patients`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/` | Register patient (auto patient number) | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| GET | `/` | Search + list (`?q=name|phone|email|number`) | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, FINANCE, SUPER_ADMIN |
| GET | `/{id}` | Patient profile | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, FINANCE, SUPER_ADMIN |
| PATCH | `/{id}` | Update patient | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |

### Appointments & Queue — `/api/v1/appointments`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/` | Book appointment | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| GET | `/today` | Today's appointments | RECEPTIONIST, NURSE, DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/queue` | Live queue | RECEPTIONIST, NURSE, DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/patient/{patientId}` | Patient appointment history | RECEPTIONIST, NURSE, DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| PATCH | `/{id}/check-in` | Check in | RECEPTIONIST, NURSE, CLINIC_ADMIN, SUPER_ADMIN |
| PATCH | `/{id}/start-consultation` | Start consultation | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| PATCH | `/{id}/complete` | Complete | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| DELETE | `/{id}` | Cancel (body: `CancelRequest`) | RECEPTIONIST, CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/schedule/slots?doctorId=&date=` | Available slots | Authenticated |

### Consultations — `/api/v1/consultations`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/start` | Start encounter | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| PATCH | `/{id}` | Update notes/diagnosis/plan | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| POST | `/{id}/complete` | Complete consultation | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| POST | `/prescriptions` | Add prescription | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/patient/{patientId}/timeline` | Clinical timeline | DOCTOR, NURSE, CLINIC_ADMIN, SUPER_ADMIN |

### Pharmacy — `/api/v1/pharmacy`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/drugs` | Add drug | CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/drugs` | Search drugs + stock | CLINIC_ADMIN, DOCTOR, NURSE, RECEPTIONIST, SUPER_ADMIN |
| GET | `/drugs/{id}/stock` | Stock summary | CLINIC_ADMIN, DOCTOR, NURSE, SUPER_ADMIN |
| POST | `/stock/add` | Add stock batch | CLINIC_ADMIN, SUPER_ADMIN |
| POST | `/dispense` | Dispense (FEFO, transactional) | CLINIC_ADMIN, NURSE, DOCTOR, SUPER_ADMIN |
| GET | `/alerts` | Low/expiring stock alerts | CLINIC_ADMIN, SUPER_ADMIN |

### Laboratory — `/api/v1/lab`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/tests` | Add test to catalog | CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/tests` | Test catalog | CLINIC_ADMIN, DOCTOR, NURSE, LAB_TECHNICIAN, SUPER_ADMIN |
| POST | `/requests` | Create lab request | DOCTOR, CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/requests/pending` | Pending requests | CLINIC_ADMIN, LAB_TECHNICIAN, SUPER_ADMIN |
| GET | `/requests/patient/{patientId}` | Requests by patient | DOCTOR, CLINIC_ADMIN, LAB_TECHNICIAN, SUPER_ADMIN |
| PATCH | `/requests/{id}/status?status=` | Update status | CLINIC_ADMIN, LAB_TECHNICIAN, SUPER_ADMIN |
| POST | `/results` | Enter results | CLINIC_ADMIN, LAB_TECHNICIAN, SUPER_ADMIN |

### Billing & Payments — `/api/v1/billing`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/invoices` | Create invoice | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| GET | `/invoices` | List invoices (`?status=`, paginated) | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| GET | `/invoices/{id}` | Invoice detail + payments | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| POST | `/payments` | Record manual payment (CASH, CARD, BANK_TRANSFER, INSURANCE) | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| POST | `/mpesa/initiate` | Initiate M-Pesa STK push | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| POST | `/mpesa/callback?invoiceId=` | **M-Pesa webhook** (public, server-to-server) | Webhook |
| GET | `/reports/revenue?year=&month=` | Monthly revenue report | CLINIC_ADMIN, FINANCE, SUPER_ADMIN |

> **Payment success rule:** the frontend can never mark a payment as successful. Only `POST /payments` (manual methods) or the M-Pesa callback with `ResultCode == 0` can transition an invoice to `PAID`/`PARTIAL`. Failed callbacks never change state.

### CRM — `/api/v1/crm`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| POST | `/campaigns` | Create campaign | CLINIC_ADMIN, SUPER_ADMIN |
| GET | `/campaigns` | List campaigns | CLINIC_ADMIN, SUPER_ADMIN |
| POST | `/campaigns/{id}/send` | Send now | CLINIC_ADMIN, SUPER_ADMIN |

### Analytics — `/api/v1/analytics`
| Method | Path | Purpose | Roles |
|---|---|---|---|
| GET | `/dashboard` | Dashboard summary | CLINIC_ADMIN, DOCTOR, FINANCE, SUPER_ADMIN |
| GET | `/trends/weekly?weeks=` | Weekly trends | same |
| GET | `/doctors/utilization` | Doctor utilization | same |
| GET | `/patients/segments` | Patient segments | same |
| GET | `/patients/at-risk?limit=` | At-risk patients | same |
| GET | `/no-shows/by-day` | No-show analysis | same |

## Key DTOs (stable contracts)

- **Auth:** `LoginRequest{email,password}` · `RegisterClinicRequest{clinicName,clinicSlug,...,email,password}` · `TokenResponse{accessToken,refreshToken,expiresIn,user}` · `UserProfile{id,email,firstName,lastName,role,clinicId,avatarUrl}`
- **Patient:** `CreatePatientRequest{firstName,lastName,phone,email?,dateOfBirth?,gender?,bloodGroup?,allergies[],...}` → `PatientResponse`
- **Invoice:** `CreateInvoiceRequest{patientId,lineItems[{description,quantity,unitPrice}],discount?,notes?,dueDate?}` → `InvoiceResponse{...,total,paidAmount,balance,status,payments[]}`
- **Payment:** `RecordPaymentRequest{invoiceId,amount,method,reference?,mpesaPhone?,notes?}`
- **Roles:** `SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, NURSE, RECEPTIONIST, FINANCE, LAB_TECHNICIAN, PHARMACIST, PATIENT`

Full field-level schemas are generated in `/v3/api-docs`.