#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# SDKMAN path setup if java/maven binaries exist
SDKMAN_JAVA_25="${HOME}/.sdkman/candidates/java/current"
if [[ -d "${SDKMAN_JAVA_25}" ]]; then
  export JAVA_HOME="${SDKMAN_JAVA_25}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi
MVN="${HOME}/.sdkman/candidates/maven/current/bin/mvn"
if [[ ! -x "${MVN}" ]]; then
  MVN="mvn"
fi

TEST_PORT=35559
CONTAINER_NAME="catalyst-postgres-e2e"
DB_PORT=55432
CATALYST_DB_URL="jdbc:postgresql://localhost:${DB_PORT}/catalyst"

echo "=== Milestone 5 E2E Test Suite ==="

# 1. Spawn Postgres test container
echo "[e2e] starting database container ${CONTAINER_NAME} on port ${DB_PORT}..."
CONTAINER_NAME="${CONTAINER_NAME}" DB_PORT="${DB_PORT}" DB_NAME="catalyst" DB_USER="catalyst" DB_PASSWORD="catalyst" ./scripts/up-postgres.sh

# 2. Build code packages
echo "[e2e] package compiling client/server modules..."
"${MVN}" -q -DskipTests clean package

# 3. Spawn server in background
echo "[e2e] booting catalyst-server in background..."
export CATALYST_SERVER_PORT="${TEST_PORT}"
export CATALYST_SERVER_DB_URL="${CATALYST_DB_URL}"
export CATALYST_SERVER_DB_USER="catalyst"
export CATALYST_SERVER_DB_PASSWORD="catalyst"

# Legacy/fallback environment variables
export CATALYST_DB_URL="${CATALYST_DB_URL}"
export CATALYST_DB_USER="catalyst"
export CATALYST_DB_PASSWORD="catalyst"

SERVER_JAR="${ROOT_DIR}/server/target/catalyst-server-1.0-SNAPSHOT.jar"
java -cp "${SERVER_JAR}:${ROOT_DIR}/server/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.server.ServerApplication > server-e2e.log 2>&1 &
SERVER_PID=$!

function cleanup {
  echo "[e2e] cleaning up background processes..."
  kill -9 "${SERVER_PID}" || true
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Wait for server to boot up by monitoring output logs or trying to connect
echo "[e2e] waiting for server to launch..."
sleep 5

# 4. Execute E2E harness
echo "[e2e] running protocol validation test client..."
TESTS_JAR="${ROOT_DIR}/tests/target/catalyst-tests-1.0-SNAPSHOT.jar"
java -cp "${TESTS_JAR}:${ROOT_DIR}/tests/target/lib/*" \
     catalyst.tests.e2e.E2EValidationHarness localhost "${TEST_PORT}"

echo "[e2e] E2E validation passed successfully!"
