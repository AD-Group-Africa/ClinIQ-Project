# Deployment — ClinIQ Backend

## Docker Deployment (Production)

### Using Docker Compose
```bash
# From the project root
docker compose up -d --build
```

This brings up:
- **db** — PostgreSQL (port 5433)
- **redis** — Redis (port 6379) 
- **backend** — Spring Boot API (port 8080)

### Environment Variables
Ensure these are set in production (via Docker `.env` file or Kubernetes ConfigMap):

| Variable | Required? | Default (dev) | Description |
|----------|-----------|---------------|-------------|
| `JWT_SECRET` | ✅ Yes | `cliniq_jwt_secret_64chars_minimum_change_in_production_now` | HS256 signing key — must be 64+ chars |
| `DATABASE_PASSWORD` | ✅ Yes | `cliniq_dev_password` | PostgreSQL password |
| `REDIS_PASSWORD` | ✅ Yes | `redis_dev_password` | Redis authentication |
| `AT_API_KEY` | ✅ Yes (for M-Pesa) | `sandbox` | Africa's Talking API key |
| `MPESA_SANDBOX` | ✅ Yes | `true` | Set `false` for live M-Pesa |
| `SPRING_PROFILES_ACTIVE` | ❌ No | `prod` | `prod`, `dev`, or `test` |
| `DATABASE_URL` | ❌ No | auto-derived | Override JDBC URL |
| `CLINIC_BASE_URL` | ❌ No | `http://localhost:8080` | Base URL for API calls |

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cliniq-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: cliniq-backend
  template:
    metadata:
      labels:
        app: cliniq-backend
    spec:
      containers:
      - name: backend
        image: ghcr.io/your-org/cliniq-backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: cliniq-secrets
              key: jwt_secret
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: cliniq-secrets
              key: database_password
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: cliniq-secrets
              key: redis_password
        resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
```

### Health Checks
The Docker Compose and Kubernetes configs include health checks:
- **PostgreSQL**: `pg_isready` every 10s
- **Redis**: `redis-cli ping` every 10s
- **Backend**: `GET /actuator/health` every 30s

### Database Migration Management
Flyway is configured to auto-migrate on startup (`spring.flyway.enabled=true`). For production:
- Ensure migration scripts are in `backend/src/main/resources/db/migration/`
- Run manual migration if needed: `./gradlew flywayMigrate`
- To reset: `./gradlew flywayReset` (destructive — use with caution)

### Backup Procedure (Production)
1. **PostgreSQL backup**:
   ```bash
   docker exec cliniq_db pg_dump -U cliniq -d cliniq --no-owner --no-acl > backup_$(date +%Y%m%d).sql
   ```
2. **Redis backup**:
   ```bash
   docker exec cliniq_db redis-cli SAVE
   docker cp cliniq_redis:/data/dump.rdb redis_backup_$(date +%Y%m%d).rdb
   ```
3. Store backups in separate location/retention policy

### Rollback Procedure
1. Stop services: `docker compose stop`
2. Restore PostgreSQL: `pg_restore -U cliniq -d cliniq backup.sql`
3. Restore Redis: `docker cp dump.rdb cliniq_redis:/data/dump.rdb && docker compose restart redis`
4. Start services: `docker compose start`
5. Verify: `curl http://localhost:8080/actuator/health`

### Environment Profiles
- **dev**: Full debug, Flyway auto-migrate, test data seeded
- **test**: Test database, no production data seeding
- **prod**: Strict security, no debug endpoints, production-ready headers

### Monitoring & Observability
- **Actuator endpoints**: `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- **Prometheus metrics**: available at `/actuator/prometheus`
- **Logging**: SLF4J with application-level log files in `backend/logs/`
- **Dashboards**: Consider Grafana + Prometheus for production monitoring

### Common Deployment Issues
1. **Port conflicts**: Ensure ports 5433, 6379, 8080 are free
2. **Database connection timeout**: Check `DB_HOST` resolves correctly in Docker network
3. **Redis connection refused**: Verify `REDIS_PASSWORD` matches between app and Redis container
4. **JWT signature mismatch**: Regenerate `JWT_SECRET` and reissue all tokens
5. **Clinic isolation failures**: Ensure `X-Clinic-ID` header is sent with every request