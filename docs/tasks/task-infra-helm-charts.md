# Task: Helm Charts

**Priority:** Much Later  
**Area:** Infrastructure / Deployment

## Purpose

Replace the raw Kubernetes YAML manifests under `k8s/` with a proper Helm chart so the deployment
can be parameterized across environments (local k3d, staging, production) without duplicating files.

## What Needs to Happen
- Create a `charts/catalyst/` Helm chart with `Chart.yaml`, `values.yaml`, and templates for:
  - PostgreSQL (or delegate to the `bitnami/postgresql` sub-chart)
  - Redis (or delegate to the `bitnami/redis` sub-chart)
  - `gateway` deployment + LoadBalancer service
  - `login-service` deployment + ClusterIP service
  - `lobby-service` deployment + ClusterIP service
  - `world-service` deployment + ClusterIP service (with zone affinity labels)
- Parameterize image tags, replica counts, resource limits, and environment variables through
  `values.yaml`.
- Create environment-specific value overrides: `values.local.yaml`, `values.staging.yaml`.
- Update the `scripts/` deploy scripts to use `helm upgrade --install` instead of `kubectl apply`.

## Acceptance Criteria
- `helm install catalyst ./charts/catalyst -f values.local.yaml` deploys the full stack into k3d.
- Image tag, replica count, and service ports are all overridable via values.
- The existing `scripts/run-e2e.sh` still passes after Helm-based deployment.
