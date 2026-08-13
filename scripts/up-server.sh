#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLUSTER_NAME="catalyst"
TEST_PORT=35555

echo "=== Bringing up Catalyst Server Environment in k3d cluster ==="

# 1. Check prerequisites
if ! command -v k3d &> /dev/null || ! command -v kubectl &> /dev/null; then
  echo "[server] k3d or kubectl not found. Please ensure they are installed." >&2
  exit 1
fi

# 2. Ensure cluster exists
if ! k3d cluster list | grep -q "${CLUSTER_NAME}"; then
  echo "[server] Creating k3d cluster ${CLUSTER_NAME}..."
  k3d cluster create "${CLUSTER_NAME}" --port "${TEST_PORT}:${TEST_PORT}/udp@loadbalancer"
fi

# 3. Build postgres image and microservices
echo "[server] Building postgres image..."
docker build -t catalyst-postgres:17 docker/postgres

echo "[server] Compiling and containerizing microservices via Micronaut's built-in packaging..."
mvn -f catalyst/pom.xml -DskipTests clean package -Dpackaging=docker

# 4. Import images into k3d
echo "[server] Importing images into k3d..."
k3d image import catalyst-postgres:17 \
  catalyst-login-service:latest \
  catalyst-lobby-service:latest \
  catalyst-world-service:latest \
  catalyst-gateway:latest \
  -c "${CLUSTER_NAME}"

# 5. Apply all Kubernetes manifests
echo "[server] Applying Kubernetes manifests..."
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/02-microservices.yaml
kubectl apply -f k8s/03-gateway.yaml

# 6. Wait for deployments to be ready
echo "[server] Waiting for deployments to rollout..."
kubectl rollout status deployment/postgres --timeout=90s
kubectl rollout status deployment/login-service --timeout=90s
kubectl rollout status deployment/lobby-service --timeout=90s
kubectl rollout status deployment/world-service --timeout=90s
kubectl rollout status deployment/gateway --timeout=90s

echo "=== Pods status ==="
kubectl get pods

echo "[server] Catalyst Server environment successfully booted in k3d cluster!"
echo "[server] Gateway is exposed and listening on UDP port ${TEST_PORT} (QUIC)."
