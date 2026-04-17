#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/youtube-atlas-backend/.env.local"
PG_BIN="${PG_BIN:-/opt/homebrew/opt/postgresql@18/bin}"
BACKUP_DIR="${BACKUP_DIR:-/tmp/youtube-atlas-db-backups}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "$ENV_FILE 파일이 없습니다."
  exit 1
fi

read_env() {
  local key="$1"
  awk -v key="$key" 'BEGIN { FS = "=" } $1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ENV_FILE" \
    | sed 's/^"//; s/"$//'
}

AWS_DB_URL="$(read_env AWS_DB_URL)"
AWS_DB_USERNAME="$(read_env AWS_DB_USERNAME)"
AWS_DB_PASSWORD="$(read_env AWS_DB_PASSWORD)"
DUMP_FILE="${1:-$(find "$BACKUP_DIR" -name '*.dump' -size +0 -print | sort | tail -1)}"

if [[ -z "$AWS_DB_URL" || -z "$AWS_DB_USERNAME" || -z "$AWS_DB_PASSWORD" ]]; then
  cat <<EOF
AWS_DB_URL, AWS_DB_USERNAME, AWS_DB_PASSWORD 설정이 필요합니다.

예:
AWS_DB_URL=postgresql://your-rds-endpoint:5432/youtube_atlas?sslmode=require
AWS_DB_USERNAME=postgres
AWS_DB_PASSWORD=your_password
EOF
  exit 1
fi

if [[ -z "$DUMP_FILE" || ! -f "$DUMP_FILE" ]]; then
  echo "복원할 dump 파일을 찾지 못했습니다: $DUMP_FILE"
  exit 1
fi

PSQL="$PG_BIN/psql"
PG_RESTORE="$PG_BIN/pg_restore"

if [[ ! -x "$PSQL" || ! -x "$PG_RESTORE" ]]; then
  echo "PostgreSQL 18 클라이언트를 찾지 못했습니다: $PG_BIN"
  exit 1
fi

echo "AWS DB 연결 확인 중..."
PGPASSWORD="$AWS_DB_PASSWORD" "$PSQL" "$AWS_DB_URL" -U "$AWS_DB_USERNAME" -v ON_ERROR_STOP=1 -Atqc \
  "select current_database() || ' / PostgreSQL ' || current_setting('server_version');"

echo "복원 시작: $DUMP_FILE"
PGPASSWORD="$AWS_DB_PASSWORD" "$PG_RESTORE" \
  --dbname "$AWS_DB_URL" \
  -U "$AWS_DB_USERNAME" \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  --exit-on-error \
  "$DUMP_FILE"

echo "복원 검증 중..."
PGPASSWORD="$AWS_DB_PASSWORD" "$PSQL" "$AWS_DB_URL" -U "$AWS_DB_USERNAME" -v ON_ERROR_STOP=1 -Atqc \
  "select count(*) || ' public tables' from information_schema.tables where table_schema = 'public';"

PGPASSWORD="$AWS_DB_PASSWORD" "$PSQL" "$AWS_DB_URL" -U "$AWS_DB_USERNAME" -v ON_ERROR_STOP=1 -Atqc \
  "select relname || '=' || n_live_tup from pg_stat_user_tables order by relname;"

echo "AWS DB restore complete."
