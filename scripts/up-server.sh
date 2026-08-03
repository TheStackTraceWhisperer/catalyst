#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${1:-35555}"

echo "[server] building modules..."
mvn -q -DskipTests package -pl ffxi-server -am

echo "[server] starting on port ${PORT}..."
SERVER_JAR="${ROOT_DIR}/ffxi-server/target/ffxi-server-1.0-SNAPSHOT.jar"
if [[ ! -f "${SERVER_JAR}" ]]; then
  echo "[server] missing jar: ${SERVER_JAR}" >&2
  exit 1
fi
java -jar "${SERVER_JAR}" "${PORT}"
