#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${1:-35555}"
export FFXI_DB_URL="${FFXI_DB_URL:-jdbc:postgresql://localhost:5432/ffxi}"
export FFXI_DB_USER="${FFXI_DB_USER:-ffxi}"
export FFXI_DB_PASSWORD="${FFXI_DB_PASSWORD:-ffxi}"

echo "[server] building modules..."
mvn -q -DskipTests package -pl ffxi-server -am

echo "[server] starting on port ${PORT}..."
SERVER_JAR="${ROOT_DIR}/ffxi-server/target/ffxi-server-1.0-SNAPSHOT.jar"
if [[ ! -f "${SERVER_JAR}" ]]; then
  echo "[server] missing jar: ${SERVER_JAR}" >&2
  exit 1
fi
echo "[server] db=${FFXI_DB_URL} user=${FFXI_DB_USER}"
java -jar "${SERVER_JAR}" "${PORT}"
