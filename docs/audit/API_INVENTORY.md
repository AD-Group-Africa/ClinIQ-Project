# ClinIQ Backend — API Inventory (v1)

**Version**: v1
**Base path**: `/api/v1/`
**Response wrapper**: `ApiResponse<T>` with `success`, `data`, `meta`, `errors`
**Error wrapper**: `ApiError` with `code`, `message`, `timestamp`, `path`, `details`
**Status codes**: 200, 201, 400, 401, 403, 404, 409, 422, 500
**Pagination**: Spring `Pageable` (`?page=0&size=20&sort=field,asc|desc`)
**Auth**: Bearer JWT in `Authorization` header

---

## Auth (`/api/v1/auth`)

| Method | Path | Description | Auth | Controller |
|---|---|---|---|---|
| POST | `/register` | Register clinic + first admin | Public | `AuthController` |
| POST | `/login` | Login, get access + refresh tokens | Public | `AuthController` |
| POST | `/refresh` | Refresh access token (rotates refresh) | Public | `AuthController` |
| POST | `/logout` | Blacklist current access token, revoke refresh | User | `AuthController` |
| POST | `/staff` | Register staff user | CLINIC_ADMIN, SUPER_ADMIN | `AuthController` |
| PATCH | `/password` | Change own password | User | `AuthController` |
| GET | `/me` | Current user profile | User | `AuthController` |

## Patient (`/api/v1/patients`)

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/` | Create patient | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| GET | `/` | Search + list (paginated, `?q=`) | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, FINANCE, SUPER_ADMIN |
| GET | `/{id}` | Get by ID | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, FINANCE, SUPER_ADMIN |
| PATCH | `/{id}` | Update patient | RECEPTIONIST, NURSE, CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |

## Appointment (`/api/v1/appointments`)

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/` | Create appointment | (to be re-verified) |
| GET | `/` | List appointments (paginated, filterable) | (to be re-verified) |
| GET | `/{id}` | Get by ID | (to be re-verified) |
| PATCH | `/{id}` | Update | (to be re-verified) |
| PATCH | `/{id}/cancel` | Cancel | (to be re-verified) |
| GET | `/doctor/{doctorId}/schedule` | Doctor schedule | (to be re-verified) |

## Billing (`/api/v1/billing`)

| Method | Path | Description | Auth | Controller |
|---|---|---|---|---|
| POST | `/invoices` | Create invoice | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN | `BillingController` |
| GET | `/invoices` | **List invoices (paginated, clinic-scoped)** | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN | `BillingController:140` |
| GET | `/invoices/{id}` | Get by ID | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN | `BillingController:57` |
| POST | `/payments` | Record payment (CASH, CARD, BANK_TRANSFER, INSURANCE, ONLINE) | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN | `BillingController:74` |
| POST | `/mpesa/initiate` | Initiate M-Pesa STK push | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN | `BillingController:91` |
| POST | `/mpesa/callback` | M-Pesa webhook | Webhook (validated by M-Pesa origin) | `BillingController:106` |
| GET | `/reports/revenue` | Monthly revenue report | CLINIC_ADMIN, FINANCE, SUPER_ADMIN | `BillingController:158` |

> **Payment-success rule**: The frontend can never mark a payment as successful. Only `recordPayment` (for manual methods) and `handleMpesaCallback` (for M-Pesa, only when `resultCode == 0`) can transition an invoice to `PAID` or `PARTIAL`.

## Lab, Pharmacy, Consultation, CRM

Inventory in these modules follows the same pattern (POST/GET list/GET by id/PATCH). See the relevant controller for exact role requirements.

## Standard error response

```json
{
  "code": "PATIENT_NOT_FOUND",
  "message": "Patient could not be found",
  "timestamp": "2026-08-28T12:00:00.000+00:00",
  "path": "/api/v1/patients/abc",
  "details": []
}
```

## Pagination

- `?page=0` (default 0)
- `?size=20` (default 20)
- `?sort=fieldName,asc|desc` (default asc, by `id` if not specified)

## See also
- Swagger UI: `http://localhost:8080/swagger-ui.html` (when running with `dev` profile)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
