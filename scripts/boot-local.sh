#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/youtube-atlas-backend"
ENV_FILE="$APP_DIR/.env.local"
EXAMPLE_FILE="$APP_DIR/.env.local.example"

if [[ -f "$ENV_FILE" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue

    key="${line%%=*}"
    value="${line#*=}"

    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"

    if [[ "$value" =~ ^\".*\"$ ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "$key=$value"
  done < "$ENV_FILE"
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

cd "$APP_DIR"
./gradlew bootRun
