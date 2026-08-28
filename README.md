# ClinIQ Backend

## Clinic Management System API

### Quick Start
```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start the backend
cd backend && ./gradlew bootRun

# 3. Access the API
#   - Base: http://localhost:8080/api/v1/
#   - Swagger UI: http://localhost:8080/swagger-ui.html
#   - API Docs: http://localhost:8080/v3/api-docs
```

### Available Scripts
| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile and test |
| `./gradlew test` | Run tests only |
| `./gradlew bootRun` | Run Spring Boot app |
| `./gradlew detekt` | Static code analysis |
| `./gradlew dependencyCheckReport` | Security scan dependencies |
| `docker compose up -d` | Start all services via Docker |
| `docker compose down -v` | Stop and remove volumes |

### Key Features
- **REST API** under `/api/v1/` with pagination and filtering
- **JWT authentication** with `ROLE_` prefixed authorities
- **Clinic isolation** via `X-Clinic-ID` header
- **RBAC**: SUPER_ADMIN, CLINIC_ADMIN, DOCTOR, RECEPTIONIST, PATIENT
- **Flyway database migrations**: V1 → V2 → V3
- **SpringDoc Swagger**: `/swagger-ui.html` and `/v3/api-docs`
- **Redis** for token blacklisting + caching
- **BCrypt password hashing**
- **ApiResponse<T>** wrapper on all endpoints

### Technology Stack
- **Language**: Kotlin 1.9 + Spring Boot 3.3.4
- **Database**: PostgreSQL 16 + Flyway
- **Cache**: Redis 7
- **Security**: JWT + BCrypt + Spring Security
- **Build**: Gradle 8.x
- **Container**: Docker + Docker Compose

### Project Structure
```
clin-iq/
├── backend/          # Spring Boot Kotlin backend
│   ├── src/main/    # Main source set
│   ├── src/test/    # Test code
│   └── docker-compose.yml
├── frontend/         # React/Vue frontend (TBD)
├── docker-compose.yml # Infrastructure services
└── docs/             # Documentation (see docs/ folder)
```

### Documentation
- `docs/architecture.md` — High-level design
- `docs/api.md` — OpenAPI summary
- `docs/security.md` — Auth flows, roles, permissions
- `docs/database.md` — Schema overview
- `docs/development.md` — Local setup, testing
- `docs/deployment.md` — Staging/production deployment
- `docs/backup_restore.md` — Backup/restore procedures
- `docs/frontend_handoff.md` — Frontend integration guide

### License
Proprietary — ClinIQ Healthcare Platform