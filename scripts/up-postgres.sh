#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLUSTER_NAME="catalyst"
DB_PORT="${DB_PORT:-5432}"

echo "=== Bringing up PostgreSQL in k3d cluster ==="

# 1. Ensure k3d cluster exists
if ! command -v k3d &> /dev/null; then
  echo "[postgres] k3d not found. Please install k3d first." >&2
  exit 1
fi

if ! k3d cluster list | grep -q "${CLUSTER_NAME}"; then
  echo "[postgres] Creating k3d cluster ${CLUSTER_NAME}..."
  k3d cluster create "${CLUSTER_NAME}" --port "35555:35555/udp@loadbalancer"
fi

# 2. Build and import postgres image
echo "[postgres] Building postgres image..."
docker build -t catalyst-postgres:17 docker/postgres

echo "[postgres] Importing postgres image into k3d..."
k3d image import catalyst-postgres:17 -c "${CLUSTER_NAME}"

# 3. Apply postgres manifest
echo "[postgres] Applying k8s manifest..."
kubectl apply -f k8s/01-postgres.yaml

# 4. Wait for deployment to be ready
echo "[postgres] Waiting for deployment to rollout..."
kubectl rollout status deployment/postgres --timeout=90s

# 5. Start port-forwarding in background if port not in use
if ! nc -z localhost "${DB_PORT}" >/dev/null 2>&1; then
  echo "[postgres] Starting port-forwarding to localhost:${DB_PORT} in background..."
  kubectl port-forward svc/postgres "${DB_PORT}:5432" >/dev/null 2>&1 &
  echo "[postgres] Port-forwarding active."
else
  echo "[postgres] Port ${DB_PORT} is already in use. Assuming port-forwarding is already active or handled."
fi

echo "[postgres] PostgreSQL is ready to accept connections!"
