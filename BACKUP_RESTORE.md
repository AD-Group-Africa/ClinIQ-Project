# Backup & Restore Guide

## PostgreSQL Backup & Restore

### Taking a Backup

```bash
# Using pg_dump with environment variables from docker-compose.yml
docker exec cliniq_db pg_dump -U cliniq -d cliniq --no-owner --no-acl > backup_$(date +%Y%m%d_%H%M%S).sql
```

Or via docker-compose:

```bash
docker compose exec db pg_dump -U ${DB_USER:-cliniq} -d ${DB_NAME:-cliniq} --no-owner --no-acl > backup.sql
```

**Important:** Ensure the database is not under heavy write load during the backup. For production systems, consider using PostgreSQL continuous archiving or pgBackups.

### Restoring a Backup

```bash
# Create a new database and restore
docker exec -i cliniq_db psql -U cliniq -d cliniq < backup.sql

# Or restore to a different database name
docker exec -i cliniq_db psql -U cliniq -c "CREATE DATABASE new_cliniq OWNER cliniq" 
docker exec -i cliniq_db psql -U cliniq -d new_cliniq < backup.sql
```

### With Flyway Migrations

Since this project uses Flyway for schema management, ensure migrations are up-to-date before restoring:

```bash
# From the backend directory
./gradlew.migrateMockDb flywayInfo  # Check migration status
./gradlew.migrateMockDb migrate     # Apply pending migrations
```

Then restore the backup:

```bash
docker exec -i cliniq_db psql -U cliniq -d cliniq < backup.sql
```

### Backup Rotation

- Keep at least 7 daily backups
- Keep at least 3 weekly backups
- Keep at least 1 monthly backup
- Store backups in a separate location/container from the production database

## Redis Backup & Restore

### Redis Data Persistence

Redis data is persisted via a Docker volume (`redis_data` in docker-compose.yml). To back up Redis data:

```bash
# Export Redis data using RDB format
docker exec cliniq_db redis-cli SAVE

# Copy the RDB file from the container
docker cp cliniq_redis:/data/dump.rdb ./dump_$(date +%Y%m%d_%H%M%S).rdb
```

### Redis Restore

```bash
# Stop the Redis service briefly and replace the data file
docker cp dump.rdb cliniq_redis:/data/dump.rdb

# Restart Redis to load the restored data
docker compose restart redis
```

## Verification

After restoring:

1. **PostgreSQL**: Verify data integrity by checking key tables (users, clinics, patients, appointments)
2. **Redis**: Verify token blacklist and cache entries are accessible
3. **Application**: Boot the application and verify it connects to the restored database

## Emergency Rollback Procedure

1. Stop the application: `docker compose stop backend`
2. Restore PostgreSQL backup: `pg_restore ...`
3. Restore Redis data: `docker cp dump.rdb ...`
4. Start the application: `docker compose start`
5. Verify all services are functional

## Automated Backup Script (Optional)

For production environments, consider implementing an automated backup script:

```bash
#!/bin/bash
# scripts/backup.sh
# Usage: ./scripts/backup.sh [--postgres] [--redis]

set -e

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="backups/${TIMESTAMP}"
mkdir -p "$BACKUP_DIR"

# PostgreSQL backup
if [[ "${1:-}" == "--postgres" ]] || [[ -z "${1:-}" ]]; then
    docker exec cliniq_db pg_dump -U ${DB_USER:-cliniq} -d ${DB_NAME:-cliniq} --no-owner --no-acl > "$BACKUP_DIR/postgresql.sql"
    echo "PostgreSQL backup completed: $BACKUP_DIR/postgresql.sql"
fi

# Redis backup
if [[ "${1:-}" == "--redis" ]] || [[ -z "${1:-}" ]]; then
    docker exec cliniq_db redis-cli SAVE
    docker cp cliniq_redis:/data/dump.rdb "$BACKUP_DIR/redis.rdb"
    echo "Redis backup completed: $BACKUP_DIR/redis.rdb"
fi

echo "Backup complete: $BACKUP_DIR"