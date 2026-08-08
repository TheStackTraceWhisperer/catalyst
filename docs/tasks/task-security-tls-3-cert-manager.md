# Task: TLS Certificate Management — Cert-Manager Kubernetes Integration

**Priority:** Sooner  
**Area:** Infrastructure / Security  
**Effort:** Small (1-2 days)  
**Parent Task:** task-security-tls-certificates.md  
**Depends On:** task-security-tls-1-server-cert-loading.md

## Purpose

Automate certificate issuance and rotation inside k3d using Cert-Manager so that services never need manual cert renewal and the chain-of-trust is managed entirely by the cluster.

## What Needs to Happen

- Install Cert-Manager into the k3d cluster (`kubectl apply -f https://github.com/cert-manager/cert-manager/releases/...`). Add this step to `scripts/run-e2e.sh` or a dedicated `scripts/setup-cluster.sh`.
- Create a `ClusterIssuer` resource using a self-signed CA (appropriate for local dev).
- Create `Certificate` resources for each service that needs TLS:
  - `gateway` — for the client-facing QUIC endpoint.
  - `login-service`, `lobby-service`, `world-service` — for backend mTLS endpoints.
- Mount the generated Kubernetes `Secret` (containing `tls.crt`, `tls.key`, `ca.crt`) as a volume at `/certs` in each relevant pod's `Deployment`.
- Reference `ca.crt` from the mounted secret as the trust anchor for client-side validation (from task-security-tls-2).

## Acceptance Criteria

- Cert-Manager issues valid certificates for all services on cluster startup.
- Certificates are auto-rotated before expiry without pod restart.
- The `ca.crt` from the mount matches what the client and gateway use for validation.
- E2E tests pass with Cert-Manager-issued certs in place.
