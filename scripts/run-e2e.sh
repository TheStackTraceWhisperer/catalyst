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

# 3. Spawn microservices in background
echo "[e2e] booting login, lobby, world, and gateway services in background..."
export CATALYST_SERVER_DB_URL="${CATALYST_DB_URL}"
export CATALYST_SERVER_DB_USER="catalyst"
export CATALYST_SERVER_DB_PASSWORD="catalyst"

# Legacy/fallback environment variables
export CATALYST_DB_URL="${CATALYST_DB_URL}"
export CATALYST_DB_USER="catalyst"
export CATALYST_DB_PASSWORD="catalyst"

# Boot Login Service (Port 35561)
LOGIN_JAR="${ROOT_DIR}/server/login-service/target/catalyst-login-service-1.0-SNAPSHOT.jar"
export CATALYST_SERVER_PORT=35561
java -cp "${LOGIN_JAR}:${ROOT_DIR}/server/login-service/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.server.login.LoginServiceApplication > login-e2e.log 2>&1 &
LOGIN_PID=$!

# Boot Lobby Service (Port 35562)
LOBBY_JAR="${ROOT_DIR}/server/lobby-service/target/catalyst-lobby-service-1.0-SNAPSHOT.jar"
export CATALYST_SERVER_PORT=35562
java -cp "${LOBBY_JAR}:${ROOT_DIR}/server/lobby-service/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.server.lobby.LobbyServiceApplication > lobby-e2e.log 2>&1 &
LOBBY_PID=$!

# Boot World Service (Port 35563)
WORLD_JAR="${ROOT_DIR}/server/world-service/target/catalyst-world-service-1.0-SNAPSHOT.jar"
export CATALYST_SERVER_PORT=35563
java -cp "${WORLD_JAR}:${ROOT_DIR}/server/world-service/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.server.world.WorldServiceApplication > world-e2e.log 2>&1 &
WORLD_PID=$!

# Boot Gateway Service (Port TEST_PORT)
GATEWAY_JAR="${ROOT_DIR}/gateway/target/catalyst-gateway-1.0-SNAPSHOT.jar"
export CATALYST_GATEWAY_PORT="${TEST_PORT}"
java -cp "${GATEWAY_JAR}:${ROOT_DIR}/gateway/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.gateway.GatewayApplication > gateway-e2e.log 2>&1 &
GATEWAY_PID=$!

function cleanup {
  echo "[e2e] cleaning up background processes..."
  kill -9 "${LOGIN_PID}" "${LOBBY_PID}" "${WORLD_PID}" "${GATEWAY_PID}" || true
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
