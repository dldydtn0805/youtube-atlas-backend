#!/bin/sh

set -eu

/app/scripts/run-db-migrations.sh

exec java -Dserver.port="${PORT:-8080}" -jar /app/app.jar
