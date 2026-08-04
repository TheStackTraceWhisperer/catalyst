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
CONTAINER_NAME="ffxi-postgres-e2e"
DB_PORT=55432
FFXI_DB_URL="jdbc:postgresql://localhost:${DB_PORT}/ffxi"

echo "=== Milestone 3 E2E Test Suite ==="

# 1. Spawn Postgres test container
echo "[e2e] starting database container ${CONTAINER_NAME} on port ${DB_PORT}..."
./scripts/up-postgres.sh

# 2. Build code packages
echo "[e2e] package compiling client/server modules..."
"${MVN}" -q -DskipTests clean package

# 3. Spawn server in background
echo "[e2e] booting ffxi-server in background..."
export FFXI_SERVER_PORT="${TEST_PORT}"
export FFXI_DB_URL="${FFXI_DB_URL}"
export FFXI_DB_USER="ffxi"
export FFXI_DB_PASSWORD="ffxi"

SERVER_JAR="${ROOT_DIR}/ffxi-server/target/ffxi-server-1.0-SNAPSHOT.jar"
java -cp "${SERVER_JAR}:${ROOT_DIR}/ffxi-server/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.ffxi.server.ServerApplication > server-e2e.log 2>&1 &
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
CLIENT_JAR="${ROOT_DIR}/ffxi-client/target/ffxi-client-1.0-SNAPSHOT.jar"
java -cp "${CLIENT_JAR}" \
     catalyst.ffxi.client.network.E2EValidationHarness localhost "${TEST_PORT}"

echo "[e2e] E2E validation passed successfully!"
