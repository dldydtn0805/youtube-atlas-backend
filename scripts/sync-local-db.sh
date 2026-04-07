#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/youtube-atlas-backend/.env.local"
DUMP_FILE="/tmp/youtube_atlas_remote.sql"
RESTORE_LOG="/tmp/youtube_atlas_restore.log"
LOCAL_DB_NAME="youtube_atlas_local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "$ENV_FILE 파일이 없습니다."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

REMOTE_SOURCE_URL="${REMOTE_DB_URL:-}"
REMOTE_SOURCE_USER="${REMOTE_DB_USERNAME:-}"
REMOTE_SOURCE_PASSWORD="${REMOTE_DB_PASSWORD:-}"

if [[ -z "$REMOTE_SOURCE_URL" || -z "$REMOTE_SOURCE_USER" || -z "$REMOTE_SOURCE_PASSWORD" ]]; then
  echo "REMOTE_DB_URL, REMOTE_DB_USERNAME, REMOTE_DB_PASSWORD 설정이 필요합니다."
  exit 1
fi

REMOTE_NO_JDBC="${REMOTE_SOURCE_URL#jdbc:postgresql://}"
REMOTE_HOSTPORT="${REMOTE_NO_JDBC%%/*}"
REMOTE_DB_QUERY="${REMOTE_NO_JDBC#*/}"
REMOTE_DB_NAME="${REMOTE_DB_QUERY%%\?*}"
REMOTE_HOST="${REMOTE_HOSTPORT%%:*}"
REMOTE_PORT="${REMOTE_HOSTPORT##*:}"

export PGPASSWORD="$REMOTE_SOURCE_PASSWORD"
/opt/homebrew/opt/postgresql@18/bin/pg_dump \
  "host=$REMOTE_HOST port=$REMOTE_PORT dbname=$REMOTE_DB_NAME user=$REMOTE_SOURCE_USER sslmode=require" \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  --file "$DUMP_FILE"

unset PGPASSWORD

psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$LOCAL_DB_NAME' AND pid <> pg_backend_pid();" >/dev/null || true
psql -d postgres -c "DROP DATABASE IF EXISTS $LOCAL_DB_NAME;"
createdb "$LOCAL_DB_NAME"
psql -d "$LOCAL_DB_NAME" -f "$DUMP_FILE" >"$RESTORE_LOG"

echo "Local database sync complete: $LOCAL_DB_NAME"
