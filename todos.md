# Sooner
* Cert-Manager (TLS Certificates)
  - Replace `new SelfSignedCertificate()` in `QuicServerTransport` with real persistent certs loaded from Kubernetes secrets
  - Replace `InsecureTrustManagerFactory` in `QuicGateway` and `QuicGatewayClient` with a proper trust manager that verifies the gateway's hostname and certificate chain
* Gateway Logout State Transition
  - When a player sends a `LogoutRequest` and receives a successful `LogoutResponse`, the gateway `RequestHandler` must transition the connection state from `PLAYING` back to `AUTHENTICATED`
  - Without this, a logged-out client retains `FLAG_WORLD` routing privileges until the connection drops
* Gateway Session Identity Injection
  - The client must never be trusted to self-report its `sessionId` in frame payloads
  - After `play_success`, the gateway should bind the verified `sessionId` to the `QuicChannel` attribute (alongside `ConnectionState`)
  - Backend world service handlers should read the `sessionId` from the channel context provided by the gateway, not from the inbound frame body

# Later
* Redis (Caching, World Registry, Session Management)

# Much Later
* Helm Charts (Kubernetes Deployment)

# Maybe?
* Keycloak (Authentication and Authorization)

# Done
* Apache Fory (Binary Serialization)