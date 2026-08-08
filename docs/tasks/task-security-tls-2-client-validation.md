# Task: TLS Certificate Management — Client-Side Certificate Validation

**Priority:** Sooner  
**Area:** Infrastructure / Security  
**Effort:** Small (1-2 days)  
**Parent Task:** task-security-tls-certificates.md  
**Depends On:** task-security-tls-1-server-cert-loading.md

## Purpose

Remove the insecure trust manager from the client and the internal gateway backend clients so that all QUIC connections validate the certificate chain of the server they connect to.

## What Needs to Happen

- In `QuicGateway.java` (client-network):
  - Remove `InsecureTrustManagerFactory.INSTANCE`.
  - Load a `TrustManagerFactory` from a CA certificate bundle resource (bundled at `src/main/resources/ca.crt` or a configurable file path).
  - Validate server certificate CN/SAN against the gateway hostname.
- In `BackendClient.java` (gateway):
  - Remove `InsecureTrustManagerFactory.INSTANCE`.
  - Load the CA bundle similarly — the gateway acts as the mTLS client on backend connections.
- For mTLS on backend connections, load a client key/cert pair from the gateway's TLS config so backend services can verify the gateway's identity.
- Add `catalyst.tls.ca-path` to the `TlsConfig` interface introduced in the server-side task.

## Acceptance Criteria

- Client rejects a connection where the gateway presents an untrusted certificate.
- Gateway backend connections reject backends presenting untrusted certificates.
- The QUIC handshake succeeds end-to-end with the CA-signed certificate chain.
- E2E tests still pass.
