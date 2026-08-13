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
"${MVN}" -f catalyst/pom.xml -q -DskipTests package -pl client/application -am

# Extract the cert-manager CA certificate from k3d catalyst cluster if available
LOCAL_CA_CERT="${ROOT_DIR}/catalyst/client/application/target/ca.crt"
if command -v kubectl &>/dev/null; then
  if kubectl get secret gateway-tls-secret -n default &>/dev/null; then
    echo "[client] Extracting cert-manager CA certificate from gateway-tls-secret..."
    kubectl get secret gateway-tls-secret -n default -o jsonpath='{.data.ca\.crt}' | base64 --decode > "${LOCAL_CA_CERT}"
  elif kubectl get secret catalyst-ca-secret -n cert-manager &>/dev/null; then
    echo "[client] Extracting cert-manager CA certificate from cert-manager namespace..."
    kubectl get secret catalyst-ca-secret -n cert-manager -o jsonpath='{.data.tls\.crt}' | base64 --decode > "${LOCAL_CA_CERT}"
  fi
fi

echo "[client] launching LWJGL + Dear ImGui client (connecting to k3d gateway on localhost:35555)..."
CLIENT_JAR="${ROOT_DIR}/catalyst/client/application/target/catalyst-client-application-1.0-SNAPSHOT.jar"
if [[ ! -f "${CLIENT_JAR}" ]]; then
  echo "[client] missing jar: ${CLIENT_JAR}" >&2
  exit 1
fi

JVM_ARGS=(
  "--enable-native-access=ALL-UNNAMED"
)
if [[ -f "${LOCAL_CA_CERT}" ]]; then
  JVM_ARGS+=(
    "-Dcatalyst.tls.ca-path=${LOCAL_CA_CERT}"
    "-Dcatalyst.tls.caPath=${LOCAL_CA_CERT}"
  )
fi

java "${JVM_ARGS[@]}" \
     -cp "${CLIENT_JAR}:${ROOT_DIR}/catalyst/client/application/target/lib/*" \
     catalyst.client.application.ClientApplication
