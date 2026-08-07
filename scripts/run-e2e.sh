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

CLUSTER_NAME="catalyst"
TEST_PORT=35555

# Register trap and cleanup at start to protect build/run stages
function cleanup {
  local exit_code=$?
  if [ $exit_code -ne 0 ]; then
    echo "[e2e] Test failed with exit code $exit_code. Dumping pod logs:"
    echo "=== KUBERNETES PODS STATUS ==="
    kubectl get pods || true
    echo "=== GATEWAY LOGS ==="
    kubectl logs deployment/gateway --tail=100 || true
    echo "=== LOGIN SERVICE LOGS ==="
    kubectl logs deployment/login-service --tail=100 || true
    echo "=== LOBBY SERVICE LOGS ==="
    kubectl logs deployment/lobby-service --tail=100 || true
    echo "=== WORLD SERVICE LOGS ==="
    kubectl logs deployment/world-service --tail=100 || true
    echo "=== POSTGRES LOGS ==="
    kubectl logs deployment/postgres --tail=100 || true
  fi

  if [[ "${CI:-false}" == "true" ]]; then
    echo "[e2e] CI detected. Deleting cluster..."
    k3d cluster delete "${CLUSTER_NAME}" || true
  fi
}
trap cleanup EXIT

echo "=== Kubernetes k3d-based E2E Test Suite ==="

# 1. Check if k3d is installed, otherwise install it (especially for CI)
if ! command -v k3d &> /dev/null; then
  echo "[e2e] k3d not found. Installing k3d..."
  curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | TAG=v5.6.0 bash
fi

# 2. Check if kubectl is installed, otherwise install it
if ! command -v kubectl &> /dev/null; then
  echo "[e2e] kubectl not found. Installing kubectl..."
  curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
  chmod +x kubectl
  mkdir -p ~/.local/bin
  mv kubectl ~/.local/bin/
  export PATH="${HOME}/.local/bin:${PATH}"
fi

# 3. Create k3d cluster if it doesn't exist
if ! k3d cluster list | grep -q "${CLUSTER_NAME}"; then
  echo "[e2e] Creating k3d cluster ${CLUSTER_NAME}..."
  k3d cluster create "${CLUSTER_NAME}" --port "${TEST_PORT}:${TEST_PORT}/udp@loadbalancer"
fi

# 4. Build postgres image and microservices
echo "[e2e] Building postgres image..."
docker build -t catalyst-postgres:17 docker/postgres

echo "[e2e] Compiling and containerizing microservices with Jib..."
"${MVN}" -q -DskipTests clean package jib:dockerBuild

# 5. Import images into k3d
echo "[e2e] Importing images into k3d..."
k3d image import catalyst-postgres:17 \
  catalyst-catalyst-login-service:latest \
  catalyst-catalyst-lobby-service:latest \
  catalyst-catalyst-world-service:latest \
  catalyst-catalyst-gateway:latest \
  -c "${CLUSTER_NAME}"

# 6. Apply Kubernetes manifests
echo "[e2e] Applying Kubernetes manifests..."
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/02-microservices.yaml
kubectl apply -f k8s/03-gateway.yaml

# 7. Wait for deployments to be ready
echo "[e2e] Waiting for deployments to rollout..."
kubectl rollout status deployment/postgres --timeout=90s
kubectl rollout status deployment/login-service --timeout=90s
kubectl rollout status deployment/lobby-service --timeout=90s
kubectl rollout status deployment/world-service --timeout=90s
kubectl rollout status deployment/gateway --timeout=90s

# 8. Execute E2E harness
echo "[e2e] Running protocol validation test client..."
"${MVN}" exec:java -pl tests -Dexec.mainClass="catalyst.tests.e2e.E2EValidationHarness" -Dexec.args="localhost ${TEST_PORT}"

echo "[e2e] E2E validation passed successfully!"
