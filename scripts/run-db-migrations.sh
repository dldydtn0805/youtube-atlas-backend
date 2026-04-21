#!/bin/sh

set -eu

if [ "${SKIP_DB_MIGRATIONS:-false}" = "true" ]; then
  echo "Skipping DB migrations because SKIP_DB_MIGRATIONS=true"
  exit 0
fi

DB_URL_CANDIDATE="${SPRING_DATASOURCE_URL:-${DB_URL:-${DB_FALLBACK_URL:-}}}"

if [ -z "${DB_URL_CANDIDATE}" ]; then
  echo "Skipping DB migrations because no datasource URL is configured"
  exit 0
fi

case "$DB_URL_CANDIDATE" in
  jdbc:postgresql://*)
    PSQL_URL="${DB_URL_CANDIDATE#jdbc:}"
    ;;
  postgresql://*)
    PSQL_URL="$DB_URL_CANDIDATE"
    ;;
  *)
    echo "Skipping DB migrations because datasource is not PostgreSQL: $DB_URL_CANDIDATE"
    exit 0
    ;;
esac

MIGRATIONS_DIR="${DB_MIGRATIONS_DIR:-/app/sql}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-${DB_USERNAME:-}}"

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "Skipping DB migrations because migrations directory is missing: $MIGRATIONS_DIR"
  exit 0
fi

set +e
MIGRATION_FILES=$(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -name '*.sql' | sort)
FIND_EXIT=$?
set -e

if [ "$FIND_EXIT" -ne 0 ] || [ -z "$MIGRATION_FILES" ]; then
  echo "Skipping DB migrations because no SQL files were found in $MIGRATIONS_DIR"
  exit 0
fi

export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-${DB_PASSWORD:-}}"

echo "Waiting for PostgreSQL to accept connections..."
ATTEMPT=1
until if [ -n "$DB_USER" ]; then
  psql "$PSQL_URL" -U "$DB_USER" -v ON_ERROR_STOP=1 -c 'select 1' >/dev/null
else
  psql "$PSQL_URL" -v ON_ERROR_STOP=1 -c 'select 1' >/dev/null
fi; do
  if [ "$ATTEMPT" -ge 20 ]; then
    echo "PostgreSQL is still unavailable after $ATTEMPT attempts"
    exit 1
  fi

  ATTEMPT=$((ATTEMPT + 1))
  sleep 3
done

echo "Applying SQL migrations from $MIGRATIONS_DIR"
for migration in $MIGRATION_FILES; do
  echo "Running $(basename "$migration")"
  if [ -n "$DB_USER" ]; then
    psql "$PSQL_URL" -U "$DB_USER" -v ON_ERROR_STOP=1 -f "$migration"
  else
    psql "$PSQL_URL" -v ON_ERROR_STOP=1 -f "$migration"
  fi
done

echo "DB migrations completed"
