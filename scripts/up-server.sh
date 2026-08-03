#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Use SDKMAN Java 25 if available, otherwise fall back to JAVA_HOME or PATH
SDKMAN_JAVA_25="${HOME}/.sdkman/candidates/java/current"
if [[ -d "${SDKMAN_JAVA_25}" ]]; then
  export JAVA_HOME="${SDKMAN_JAVA_25}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi
MVN="${HOME}/.sdkman/candidates/maven/current/bin/mvn"
if [[ ! -x "${MVN}" ]]; then
  MVN="mvn"
fi

PORT="${1:-35555}"
export FFXI_SERVER_PORT="${PORT}"
export FFXI_DB_URL="${FFXI_DB_URL:-jdbc:postgresql://localhost:5432/ffxi}"
export FFXI_DB_USER="${FFXI_DB_USER:-ffxi}"
export FFXI_DB_PASSWORD="${FFXI_DB_PASSWORD:-ffxi}"

echo "[server] building modules (Java $(java -version 2>&1 | head -1))..."
"${MVN}" -q -DskipTests package -pl ffxi-server -am

echo "[server] starting on port ${PORT}..."
SERVER_JAR="${ROOT_DIR}/ffxi-server/target/ffxi-server-1.0-SNAPSHOT.jar"
if [[ ! -f "${SERVER_JAR}" ]]; then
  echo "[server] missing jar: ${SERVER_JAR}" >&2
  exit 1
fi
echo "[server] db=${FFXI_DB_URL} user=${FFXI_DB_USER}"
java -cp "${SERVER_JAR}:${ROOT_DIR}/ffxi-server/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.ffxi.server.ServerApplication
