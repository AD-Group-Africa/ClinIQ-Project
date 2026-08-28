# Development — ClinIQ Backend

## Quick Start

### Prerequisites
- Java 21 (JDK)
- Gradle 8.x
- PostgreSQL 16 (or Docker)
- Redis 7 (or Docker)
- Node.js 20+ (for frontend)

### Environment Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/cliniq.git
   cd clinciq
   ```

2. **Configure environment variables**
   Copy `.env.example` to `.env` and set required values:
   ```bash
   # Backend .env
   JWT_SECRET=cliniq_jwt_secret_64chars_minimum_change_in_production_now
   DATABASE_PASSWORD=cliniq_dev_password
   REDIS_PASSWORD=redis_dev_password
   AT_API_KEY=sandbox
   MPESA_SANDBOX=true
   SPRING_PROFILES_ACTIVE=dev
   ```

   ```bash
   # Frontend .env (if needed)
   REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
   ```

3. **Start infrastructure (Docker Compose)**
   ```bash
   docker compose up -d
   ```
   This starts:
   - `cliniq_db` — PostgreSQL on port 5433
   - `cliniq_redis` — Redis on port 6379
   - `cliniq_backend` — Spring Boot on port 8080

4. **Build and run the backend**
   ```bash
   # From the backend directory
   ./gradlew bootRun
   ```
   Or using Docker:
   ```bash
   docker compose up --build backend
   ```

5. **Access the application**
   - API: `http://localhost:8080/api/v1/...`
  . Swagger UI: `http://localhost:8080/swagger-ui.html`
   - OpenAPI JSON: `http://localhost:8080/v3/api-docs`
   - Redis CLI: `redis-cli -a <password> ping`

### Running Tests

```bash
# Run all unit/integration tests
./gradlew test

# Run with specific profile
./gradlew test -Dspring.profiles.active=test

# Check test coverage (if JaCoCo configured)
./gradlew jacocoReport
```

### Code Style & Lint

```bash
# Kotlin code formatting
./gradlew ktm

# Run detekt static analysis
./gradlew detekt

# Check for compile errors
./gradlew build --quiet
```

### Adding New Domain

1. Create entity class in `domain/<name>/entity/`
2. Create repository interface in `domain/<name>/repository/`
3. Create DTOs in `domain/<name>/dto/`
4. Create service in `domain/<name>/service/`
5. Create controller in `domain/<name>/controller/`
6. Add Swagger `@Operation`/`@Schema` annotations
7. Add migration script if DB changes
8. Run `./gradlew build` to verify

### IDE Setup (IntelliJ IDEA)
1. Open the project root
2. Import as Gradle project
3. Ensure JDK 21 is selected
4. Enable "Annotation Processing" if using Lombok/Swagger
5. Run `gradlew genIdeal` if needed

### Debug Mode
```bash
./gradlew bootRun --args='--spring.profiles.active=debug'
```
Or via IntelliJ debug configuration attached to port 5005.

## Directory Structure (Backend)
```
src/main/kotlin/com/cliniq/
├── config/          # SecurityConfig, WebConfig
├── domain/          # All domain modules
│   ├── auth/        # Authentication/authorization
│   ├── patient/     # Patient management
│   ├── appointment/ # Appointment scheduling
│   ├── consultation/ # Clinical consultations
│   ├── pharmacy/    # Drug dispensing
│   ├── lab/         # Laboratory tests
│   ├── billing/     # Billing/invoicing
│   └── ...          # Other domains
├── shared/          # Shared utilities (exceptions, audit, DTOs)
└── ...             
src/main/resources/
├── db/migration/    # Flyway V1/V2/V3 scripts
├application*.yml   # Config per profile
└── application-test.yml
```

## Git Workflow
- Feature branches: `feature/xyz`
- Pull requests target `main`
- All changes must pass `./gradlew build` and tests
- Commit messages follow Conventional Commits format
- Tag releases as `v1.0.0`, `v1.1.0`, etc.