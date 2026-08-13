#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLUSTER_NAME="catalyst"

echo "=== Purging Catalyst k3d Cluster & Container Cache ==="

# 1. Delete k3d cluster if it exists
if command -v k3d &> /dev/null; then
  if k3d cluster list | grep -q "${CLUSTER_NAME}"; then
    echo "[purge] Deleting k3d cluster '${CLUSTER_NAME}'..."
    k3d cluster delete "${CLUSTER_NAME}"
  else
    echo "[purge] No active k3d cluster named '${CLUSTER_NAME}' found."
  fi
else
  echo "[purge] k3d binary not found, skipping cluster deletion."
fi

# 2. Delete local Docker images for catalyst services
echo "[purge] Cleaning up local Catalyst Docker images..."
docker image rm -f \
  catalyst-postgres:17 \
  catalyst-login-service:latest \
  catalyst-lobby-service:latest \
  catalyst-world-service:latest \
  catalyst-gateway:latest \
  catalyst-tests:latest \
  2>/dev/null || true

# 3. Prune dangling builder layers
echo "[purge] Pruning dangling Docker system cache..."
docker system prune -f --volumes || true

echo "=== Purge Complete ==="