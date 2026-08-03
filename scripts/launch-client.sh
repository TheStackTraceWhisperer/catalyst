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

echo "[client] building modules (Java $(java -version 2>&1 | head -1))..."
"${MVN}" -q -DskipTests package -pl ffxi-client -am

echo "[client] launching LWJGL + Dear ImGui client..."
CLIENT_JAR="${ROOT_DIR}/ffxi-client/target/ffxi-client-1.0-SNAPSHOT.jar"
if [[ ! -f "${CLIENT_JAR}" ]]; then
  echo "[client] missing jar: ${CLIENT_JAR}" >&2
  exit 1
fi
java -cp "${CLIENT_JAR}:${ROOT_DIR}/ffxi-client/target/lib/*" \
     --enable-native-access=ALL-UNNAMED \
     catalyst.ffxi.client.ClientApplication
