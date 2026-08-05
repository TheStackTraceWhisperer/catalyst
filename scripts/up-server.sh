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
export CATALYST_SERVER_PORT="${PORT}"
export CATALYST_SERVER_DB_URL="${CATALYST_DB_URL:-jdbc:postgresql://localhost:5432/catalyst}"
export CATALYST_SERVER_DB_USER="${CATALYST_DB_USER:-catalyst}"
export CATALYST_SERVER_DB_PASSWORD="${CATALYST_DB_PASSWORD:-catalyst}"

# Legacy/fallback environment variables
export CATALYST_DB_URL="${CATALYST_SERVER_DB_URL}"
export CATALYST_DB_USER="${CATALYST_SERVER_DB_USER}"
export CATALYST_DB_PASSWORD="${CATALYST_SERVER_DB_PASSWORD}"

echo "[server] building modules (Java $(java -version 2>&1 | head -1))..."
"${MVN}" -q -DskipTests package -pl server -am

echo "[server] starting on port ${PORT}..."
SERVER_JAR="${ROOT_DIR}/server/target/catalyst-server-1.0-SNAPSHOT.jar"
if [[ ! -f "${SERVER_JAR}" ]]; then
  echo "[server] missing jar: ${SERVER_JAR}" >&2
  exit 1
fi
echo "[server] db=${CATALYST_SERVER_DB_URL} user=${CATALYST_SERVER_DB_USER}"
java -cp "${SERVER_JAR}:${ROOT_DIR}/server/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.server.ServerApplication
