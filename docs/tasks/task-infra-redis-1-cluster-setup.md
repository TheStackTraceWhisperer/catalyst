# Task: Redis Integration — Infrastructure Setup

**Priority:** Later  
**Area:** Infrastructure / Caching / Session Management  
**Effort:** Small (1-2 days)  
**Parent Task:** task-infra-redis.md

## Purpose

Stand up Redis inside the local k3d development cluster and verify that it is reachable by the backend services before any application-layer code depends on it.

## What Needs to Happen

- Add a Redis `Deployment` and `Service` to `k8s/02-microservices.yaml` (or a new `k8s/04-redis.yaml`):
  - Use the official `redis:7-alpine` image.
  - `Service` name: `redis`, port `6379`, ClusterIP-only (internal traffic only).
- Add the `lettuce-core` (or `jedis`) dependency to the relevant modules in `pom.xml`.
- Add Micronaut Redis configuration stub to `application.yml` of affected services (`world-service`, `lobby-service`), pointing to `redis://redis:6379`.
- Verify the pod starts cleanly and services can resolve `redis:6379` from within the cluster using a simple connection smoke test.

## Acceptance Criteria

- Redis pod is `Running` inside k3d.
- Backend services start without Redis connection errors.
- A `redis-cli ping` from inside the cluster returns `PONG`.
