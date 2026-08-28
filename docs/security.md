# Security — ClinIQ Backend

## Authentication Flow

### JWT-Based Authentication
- **Scheme**: Bearer token via `Authorization: Bearer <jwt>` header
- **Token issuance**: `POST /api/v1/auth/login` with phone+password
- **Token validation**: Spring Security JwtAuthenticationFilter validates token on each request
- **Token lifecycle**: 
  - Access tokens are stateless (signed with HS256 using `JWT_SECRET` from env)
  - Refresh tokens are stored in Redis blacklist for logout/invalidation
  - Token rotation: each refresh invalidates the previous token

### Password Hashing
- **Algorithm**: BCrypt with 12 rounds (`BCryptPasswordEncoder(12)`)
- **Default admin password**: `Admin123!` (hash stored in V3 seed migration)
- **User passwords**: Never stored in plaintext; always hashed on registration

### Role Model
Roles are defined as a `UserRole` enum with `ROME_` prefix for Spring Security compatibility:
- `SUPER_ADMIN` — full access across all clinics
- `CLINIC_ADMIN` — manage own clinic
- `DOCTOR` — clinical operations within own clinic
- `RECEPTIONIST` — administrative operations within own clinic
- `PATIENT` — patient-facing operations (booking, own records)

The `UserDetailsServiceImpl` maps roles as `ROLE_${u.role.name}`, e.g., `ROLE_SUPER_ADMIN`, `ROLE_DOCTOR`.

## Authorization (RBAC)

### Clinic Isolation
- **Mechanism**: `ClinicIsolationFilter` checks `X-Clinic-ID` header against JWT `clinicId`
- **Enforcement**: All database queries are scoped by `clinicId` via repository methods (`findByIdAndClinicId`, `findByClinicIdAnd...`)
- **Cross-clinic access**: Returns 403 Forbidden for non-SUPER_ADMIN users

### Endpoint-Level Permissions
| Endpoint | Required Role |
|----------|-------------|
| `/api/v1/auth/**` | permitAll |
| `/api/v1/public/**` | permitAll |
| `/api/v1/patients` | CLINIC_ADMIN, RECEPTIONIST, SUPER_ADMIN |
| `/api/v1/appointments` | CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| `/api/v1/consultations` | DOCTOR, SUPER_ADMIN |
| `/api/v1/pharmacy` | CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| `/api/v1/lab` | CLINIC_ADMIN, DOCTOR, SUPER_ADMIN |
| `/api/v1/billing` | CLINIC_ADMIN, RECEPTIONIST, FINANCE, SUPER_ADMIN |
| `/api/v1/admin/**` | SUPER_ADMIN |

### Security Headers (implemented)
All responses include the following headers set via Spring Security DSL:
- `Content-Security-Policy`: `default-src 'self'`
- `Strict-Transport-Security`: max-age=31536000, includeSubDomains
- `X-Content-Type-Options`: `nosniff`
- `X-Frame-Options`: `DENY` (prevents clickjacking)
- `Referrer-Policy`: `default`

## Sensitive Configuration

All sensitive values come from environment variables (no hardcoded secrets):

| Variable | Purpose | Placeholder |
|----------|---------|-------------|
| `JWT_SECRET` | JWT signing key | `${JWT_SECRET:...}` |
| `DATABASE_PASSWORD` | PostgreSQL password | `${DB_PASSWORD:...}` |
| `REDIS_PASSWORD` | Redis password | `${REDIS_PASSWORD:...}` |
| `AT_API_KEY` | Africa's Talking API key | `${AT_API_KEY:--}` |
| `MPESA_SANDBOX` | M-Pesa sandbox flag | `${MPESA_SANDBOX:true}` |

### Never committed to repository
The `.env` file (if used locally) must be listed in `.gitignore`. The Docker Compose configuration uses `${VAR:-default}` syntax for safe defaults.

## Security Considerations

### SQL Injection
- All JPA/repository queries use parameterized queries
- No raw SQL concatenation in repository methods (verified in code review)

### XSS Prevention
- Output is escaped in Thymeleaf/Templates (if used)
- JSON responses do not directly inject user-controlled data without validation

### CSRF Protection
- Disabled for stateless JWT API (`http.csrf { it.disable() }`)
- State maintained via JWT token in `Authorization` header

### Rate Limiting
- Not yet implemented; consider adding Spring Gateway/Zuul rate limiting for production

### Dependency Security
- GitHub Actions job `security-scan` runs `./gradlew dependencyCheckReport` to detect known vulnerabilities
- See `.github/workflows/ci-cd.yml`