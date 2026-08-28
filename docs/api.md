# API Reference — ClinIQ Backend

## Base URL
`https://api.cliniq.localhost/api/v1/`

All API endpoints are versioned under `/api/v1/`. The OpenAPI specification is available at:
- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI JSON**: `/v3/api-docs`

## Authentication & Authorization

### Login
```
POST /api/v1/auth/login
```
Request body:
```json
{
  "phone": "string",
  "password": "string"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "token": "jwt-token-string",
    "user": {
      "id": "uuid",
      "email": "string",
      "role": "SUPER_ADMIN|DOCTOR|PATIENT|RECEPTIONIST|CLINIC_ADMIN",
      "clinicId": "uuid"
    }
  },
  "error": null,
  "meta": null
}
```

### Role-Based Access Control
All endpoints use Spring Security `@PreAuthorize`. Valid roles:
- `SUPER_ADMIN` — full cross-clinic access
- `CLINIC_ADMIN` — scoped to `X-Clinic-ID` header
- `DOCTOR` — patient appointments own clinic, can start consultations
- `RECEPTIONIST` — booking, check-in, basic patient management
- `PATIENT` — own appointments only, portal booking

### Clinic Isolation
Non-SUPER_ADMIN requests must include `X-Clinic-ID` header matching the JWT `clinicId`. Violation returns 403 Forbidden.

## Error Response Format
All responses follow `ApiResponse<T>`:
```json
{
  "success": boolean,
  "data": T|null,
  "error": string|null,
  "meta": {page info|null}
}
```

HTTP error examples:
- **401**: `"Unauthenticated - valid JWT token required"`
- **403**: `"Access denied - clinic ID mismatch"` or `"Not your consultation"`
- **422**: `"Validation failed"` with field details
- **409**: `"This time slot is already booked"` or `"Insufficient stock"`
- **404**: `"Patient not found"`, `"Appointment not found"`, `"Consultation not found"`

## OpenAPI / Swagger Documentation
- **UI**: `/swagger-ui.html` — interactive endpoint explorer
- **JSON Spec**: `/v3/api-docs` — machine-readable OpenAPI 3.0 specification
- All `@Operation`, `@Parameter`, `@Schema` annotations are being added incrementally across domains

## Versioning
API is versioned as `/api/v1/`. Future versions will be `/api/v2/`, `/api/v3/`, etc. Backward compatibility is maintained where possible.

## Contact
For API questions or feedback, contact the backend team or open an issue in the repository.