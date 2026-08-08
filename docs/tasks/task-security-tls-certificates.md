# Task: TLS Certificate Management

**Priority:** Sooner  
**Area:** Infrastructure / Security
**Effort:** Medium (3-5 days)  

## Problem

The current QUIC transport layer uses self-signed, ephemeral, in-memory certificates that are
regenerated every time a process restarts. The client blindly trusts any certificate it receives.
This means there is zero identity verification in the TLS handshake and the connection provides
encryption with no authentication.

Affected files:
- `server/world-service/.../transport/QuicServerTransport.java` — generates `new SelfSignedCertificate()` on each startup
- `client/network/.../QuicGateway.java` — uses `InsecureTrustManagerFactory.INSTANCE`
- `gateway/proxy/QuicGatewayClient.java` — uses `InsecureTrustManagerFactory.INSTANCE` on internal backend connections

## What Needs to Happen

### Server / Gateway Side
- Stop generating ephemeral self-signed certificates at startup.
- Mount real persistent certificates from Kubernetes Secrets (provisioned by Cert-Manager).
- Load the certificate chain from the mounted PEM / PKCS12 file path instead of generating one at runtime.
- All three backend services (login, lobby, world) and the gateway should load certs the same way.

### Client Side
- Remove `InsecureTrustManagerFactory.INSTANCE`.
- Configure a `TrustManagerFactory` that loads the CA certificate bundle (e.g., the Cert-Manager root CA) from a bundled resource or configurable file path.
- Validate that the gateway's presented certificate is signed by the expected CA and matches the expected hostname.

### Kubernetes Setup
- Install Cert-Manager into the k3d cluster.
- Create a `ClusterIssuer` or `Issuer` (self-signed CA for local dev, Let's Encrypt or private CA for production).
- Create `Certificate` resources for each service and mount them as Kubernetes Secrets into the relevant pods.

## Acceptance Criteria
- The QUIC handshake between client and gateway uses a verifiable certificate chain.
- The QUIC handshake between gateway and backend services uses mutual TLS (mTLS).
- Certificates rotate without requiring a code change or rebuild.
- E2E test harness still passes with cert-based TLS.

## Sub-Tasks
This task has been broken into the following smaller tasks:
- **task-security-tls-1-server-cert-loading.md** — Replace ephemeral SelfSignedCertificate with file-based loading in all services
- **task-security-tls-2-client-validation.md** — Remove InsecureTrustManagerFactory, add CA bundle validation in client and gateway
- **task-security-tls-3-cert-manager.md** — Automate issuance and rotation via Cert-Manager in k3d
