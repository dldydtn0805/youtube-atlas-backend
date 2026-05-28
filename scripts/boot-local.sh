#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/youtube-atlas-backend"
ENV_FILE="$APP_DIR/.env.local"
EXAMPLE_FILE="$APP_DIR/.env.local.example"
LOAD_ENV_LIB="$ROOT_DIR/scripts/lib/load-env.sh"

source "$LOAD_ENV_LIB"

if [[ -f "$ENV_FILE" ]]; then
  env_export_file "$ENV_FILE"
else
  cat <<EOF
$ENV_FILE 파일이 없습니다.

1. 아래 예시 파일을 복사한 뒤
   cp "$EXAMPLE_FILE" "$ENV_FILE"
2. GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, YOUTUBE_API_KEY 를 채워 주세요.
3. 다시 ./scripts/boot-local.sh 를 실행하면 됩니다.
EOF
  exit 1
fi

# Allow a local backend process to target a remote PostgreSQL instance without
# changing the generic DB_* variables used for local development.
if [[ -n "${REMOTE_DB_URL:-}" ]]; then
  export SPRING_DATASOURCE_URL="$REMOTE_DB_URL"
  export SPRING_DATASOURCE_USERNAME="${REMOTE_DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-${DB_USERNAME:-}}}"
  export SPRING_DATASOURCE_PASSWORD="${REMOTE_DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-${DB_PASSWORD:-}}}"
  export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${REMOTE_DB_DRIVER:-org.postgresql.Driver}"
  export SPRING_JPA_HIBERNATE_DDL_AUTO="${SPRING_JPA_HIBERNATE_DDL_AUTO:-validate}"
fi

# Local boot should stay side-effect free unless explicitly overridden.
export GAME_SCHEDULER_ENABLED="${GAME_SCHEDULER_ENABLED:-false}"
export TRENDING_SCHEDULER_ENABLED="${TRENDING_SCHEDULER_ENABLED:-false}"

cd "$APP_DIR"
./gradlew bootRun
