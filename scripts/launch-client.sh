#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[client] building modules..."
mvn -q -DskipTests package -pl ffxi-client -am

echo "[client] launching LWJGL + Dear ImGui client..."
CLIENT_JAR="${ROOT_DIR}/ffxi-client/target/ffxi-client-1.0-SNAPSHOT.jar"
if [[ ! -f "${CLIENT_JAR}" ]]; then
  echo "[client] missing jar: ${CLIENT_JAR}" >&2
  exit 1
fi
java -jar "${CLIENT_JAR}"
