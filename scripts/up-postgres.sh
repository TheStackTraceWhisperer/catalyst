#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

IMAGE_NAME="${IMAGE_NAME:-ffxi-postgres:17}"
CONTAINER_NAME="${CONTAINER_NAME:-ffxi-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-ffxi}"
DB_USER="${DB_USER:-ffxi}"
DB_PASSWORD="${DB_PASSWORD:-ffxi}"

echo "[postgres] building image ${IMAGE_NAME}..."
docker build -t "${IMAGE_NAME}" docker/postgres

if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "[postgres] removing existing container ${CONTAINER_NAME}..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi

echo "[postgres] starting container ${CONTAINER_NAME} on port ${DB_PORT}..."
docker run -d \
  --name "${CONTAINER_NAME}" \
  -p "${DB_PORT}:5432" \
  -e POSTGRES_DB="${DB_NAME}" \
  -e POSTGRES_USER="${DB_USER}" \
  -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
  "${IMAGE_NAME}" >/dev/null

echo "[postgres] ready: postgres://${DB_USER}:${DB_PASSWORD}@localhost:${DB_PORT}/${DB_NAME}"
