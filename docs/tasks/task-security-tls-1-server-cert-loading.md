# Task: TLS Certificate Management — Server-Side Certificate Loading

**Priority:** Sooner  
**Area:** Infrastructure / Security  
**Effort:** Small (1-2 days)  
**Parent Task:** task-security-tls-certificates.md

## Purpose

Replace the ephemeral `SelfSignedCertificate()` generated at startup in the gateway and all backend services with a persistent certificate loaded from a mounted file path, making identity stable across restarts.

## What Needs to Happen

- In `GatewayServer.java`, `QuicServerTransport.java` (world service) and any equivalent in login/lobby services:
  - Replace `new SelfSignedCertificate()` with a cert+key loaded from configurable file paths (e.g. `/certs/tls.crt` and `/certs/tls.key`).
  - Expose `catalyst.tls.cert-path` and `catalyst.tls.key-path` configuration properties via the existing `@ConfigurationProperties` pattern.
- Create a `TlsConfig` `@ConfigurationProperties` interface returning the two paths.
- Use `QuicSslContextBuilder.forServer(new File(keyPath), null, new File(certPath))` in place of the current `SelfSignedCertificate` construction.
- Update `application.yml` for each service to supply the file path config keys (can point to test certs initially).
- Update Kubernetes manifests to mount a `tls-secret` volume at `/certs` for each affected pod.

## Acceptance Criteria

- No service generates a `SelfSignedCertificate` at startup.
- Restarting a service presents the same certificate without code change.
- E2E tests still pass with the file-based certs.
