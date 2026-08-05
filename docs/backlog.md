Expanding the network architecture into the target multi-server topology (Gateway + Microservices) is a fantastic direction. This perfectly aligns with the target topology outlined in your network architecture documentation and solidifies the foundation before adding complex game logic.

Here is a structured approach to executing this architectural split based on your documentation:

### 1. Extract the Gateway Service

Currently, the `QuicServerTransport` in the `server` module handles all incoming connections.

* **Action:** Create a new `gateway` module and move the public-facing `QuicServerTransport` here.
* **Responsibility:** The gateway will act as the sole public endpoint on the internet, handling TLS 1.3 termination and auth token validation. It will contain a "Phase Router" to direct requests (e.g., `LOGIN` to the Login Service, `CHAR_*` to the Lobby Service).
* **Session Routing Table:** Implement a routing table to map a `sessionId` to a specific World Server. This allows the gateway to correctly forward active session packets like `PING` and `LOGOUT`.

### 2. Split Handlers into Independent Microservices

You currently have `LoginHandler`, `LobbyHandler`, and `WorldHandler` in a single monolithic process.

* **Action:** Break the `server` module into `login`, `lobby`, and `world` backend services.
* **Login Service:** Will handle Argon2id account authentication and issue auth tokens.
* **Lobby Service:** Will manage character CRUD operations, race/job/nation validations, and world server assignment on `PLAY`.
* **World Service:** Will manage session lifecycles, zone management, keepalives, and entity tracking.

### 3. Implement Internal QUIC & mTLS

The gateway needs to communicate with the internal services securely.

* **Action:** Implement internal QUIC transport for the backend services.
* **Security:** Configure mutual TLS (mTLS) for these internal connections. The backend services should only accept connections presenting the Gateway's certificate, which ensures that a compromised service cannot impersonate another and eliminates the need for per-packet source validation.

### 4. Implement the World Registry

When a user selects a character and hits "Play", the system needs to route them to the correct server instance.

* **Action:** Introduce a World Registry that the Lobby Service can query.
* **Flow:** On a `PLAY` request, the Lobby Service looks up the target `zoneId` in the World Registry to find the assigned World Server's QUIC address. The Lobby Service then contacts that World Server to create the session, and returns the assignment so the Gateway can update its routing table.

By tackling this now, you ensure the networking infrastructure is fully mature and horizontally scalable before the complexities of 3D asset rendering, movement interpolation, and combat math are introduced.