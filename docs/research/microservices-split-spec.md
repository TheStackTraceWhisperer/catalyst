# Technical Specification: Catalyst Microservices Split

This specification describes the transition of the monolithic `server` module into a distributed microservice topology consisting of a public **Gateway** and three private backend services (**Login**, **Lobby**, and **World**), coordinated via a **World Registry** and secured using internal **QUIC + mTLS**.

---

## 1. Target Directory & Module Map

The root project structure must be updated to accommodate the new modules:

```
root/
├── pom.xml (Parent aggregator)
├── common/ (Aggregator)
│   ├── concurrency/
│   ├── network/
│   └── dto/
├── client/ (Aggregator)
│   ├── engine/
│   ├── network/
│   └── application/
├── gateway/ (New - Public facing QUIC endpoint)
├── server/ (Aggregator for backend services)
│   ├── pom.xml (Parent aggregator for servers)
│   ├── login-service/ (New - Account Auth)
│   ├── lobby-service/ (New - Character CRUD & Selection)
│   └── world-service/ (New - World simulations & sessions)
└── tests/ (Aggregator for E2E)
```

### 1.1 Server Monomodule Aggregator Conversion Steps

Currently, `server` is a monomodule containing direct source files. We must convert it into a parent aggregator module for the microservices:

1.  **Deconstruct `server/pom.xml`**:
    *   Change `<packaging>jar</packaging>` to `<packaging>pom</packaging>`.
    *   Delete the `<dependencies>` and `<build>` elements (these will be moved to the individual submodules).
    *   Add a `<modules>` section:
        ```xml
        <modules>
          <module>login-service</module>
          <module>lobby-service</module>
          <module>world-service</module>
        </modules>
        ```
2.  **Move Source Files & Split Code**:
    *   Create directories for the submodules:
        *   `server/login-service/src/main/java/`
        *   `server/lobby-service/src/main/java/`
        *   `server/world-service/src/main/java/`
    *   **Login Service** takes ownership of:
        *   `ServerProperties.java` (contains database auth configuration)
        *   `DatabaseConfiguration.java` (sets up Hikari DataSource for accounts)
        *   `ServerApplication.java` $\rightarrow$ Rename to `LoginServiceApplication.java` (retaining only Login initialization)
        *   `handler/LoginHandler.java`
        *   `repository/AccountRepository.java`
    *   **Lobby Service** takes ownership of:
        *   `DatabaseConfiguration.java` (sets up Hikari DataSource for character metadata)
        *   `ServerApplication.java` $\rightarrow$ Rename to `LobbyServiceApplication.java` (retaining Lobby validation initialization)
        *   `handler/LobbyHandler.java`
        *   `repository/CharacterRepository.java`
    *   **World Service** takes ownership of:
        *   `ServerApplication.java` $\rightarrow$ Rename to `WorldServiceApplication.java` (retaining session manager loops)
        *   `handler/WorldHandler.java`
        *   `repository/SessionRepository.java`
        *   `session/AuthTicketStore.java`
        *   `session/ZoneManager.java`
        *   `transport/QuicServerTransport.java` (acts as the internal backend listener)
3.  **Clean up Parent `server` Module**:
    *   Physically delete `server/src/` after file migration to ensure the parent module no longer compiles any code directly.

### 1.2 Maven Dependency Tree Changes:
1.  **`gateway`**: Depends on `catalyst-common-network`, `catalyst-common-concurrency`, and `catalyst-common-dto`.
2.  **`login-service`**: Depends on `catalyst-common-network`, `catalyst-common-concurrency`, `catalyst-common-dto`, PostgreSQL driver, and Micronaut.
3.  **`lobby-service`**: Depends on `catalyst-common-network`, `catalyst-common-concurrency`, `catalyst-common-dto`, PostgreSQL driver, and Micronaut.
4.  **`world-service`**: Depends on `catalyst-common-network`, `catalyst-common-concurrency`, `catalyst-common-dto`, and Micronaut.

---

## 2. Gateway Proxy Routing Design

The Gateway is the only service bound to a public IP. It handles outer QUIC connections and proxies inner payloads to backend services based on packet types.

### Session & Routing State:
```java
@Singleton
public class GatewaySessionRouter {
    // Maps active sessionId -> target World Service internal address (host:port)
    private final Map<String, String> sessionRoutes = new ConcurrentHashMap<>();
    
    public void registerRoute(String sessionId, String worldAddress) {
        sessionRoutes.put(sessionId, worldAddress);
    }
    
    public void removeRoute(String sessionId) {
        sessionRoutes.remove(sessionId);
    }
    
    public String getRoute(String sessionId) {
        return sessionRoutes.get(sessionId);
    }
}
```

### Phase Routing Table:
When a packet arrives from the client over QUIC:
*   **`LOGIN`**: Forward to **Login Service**.
*   **`CHAR_LIST`**, **`CHAR_CREATE`**, **`CHAR_DELETE`**, **`CHAR_SELECT`**: Forward to **Lobby Service**.
*   **`PLAY`**:
    1. Forward to **Lobby Service**.
    2. If Lobby Service returns `PLAY_OK` (which includes `sessionId` and assigned `worldAddress`), the Gateway updates its `sessionRoutes`:
       `router.registerRoute(response.getSessionId(), response.getWorldAddress())`
    3. Return `PLAY_OK` to the client.
*   **`PING`**, **`LOGOUT`**:
    1. Retrieve target world server address from `sessionRoutes` using the packet's `sessionId`.
    2. Forward to that **World Service** instance.
    3. If `LOGOUT` succeeds, clear the session route: `router.removeRoute(sessionId)`.

---

## 3. Internal mTLS & Transport Setup

All Gateway-to-Service communication is done via mTLS over QUIC. 

### Cert Generation for Local Dev:
The deployment script (or a new setup script) must generate self-signed certificates for mTLS:
*   `gateway.crt` / `gateway.key` (Presented by Gateway during outbound client calls).
*   `service.crt` / `service.key` (Presented by backend services).

### Netty SSL Context Configuration:
**Backend Services (e.g., Login Service listener):**
```java
// Accept only connections signed by the Gateway certificate authority
TrustManagerFactory trustManager = InsecureTrustManagerFactory.INSTANCE; // Replace with local CA trust in prod
QuicSslContext sslContext = QuicSslContextBuilder.forServer(serviceKey, serviceCert)
    .trustManager(trustManager) // Ensures client presenting cert is validated
    .clientAuth(ClientAuth.REQUIRE) // Enforce mTLS!
    .applicationProtocols("catalyst-internal-1")
    .build();
```

**Gateway (client calling backend services):**
```java
QuicSslContext sslContext = QuicSslContextBuilder.forClient()
    .keyManager(gatewayKey, gatewayCert) // Present Gateway identity to services
    .trustManager(InsecureTrustManagerFactory.INSTANCE)
    .applicationProtocols("catalyst-internal-1")
    .build();
```

---

## 4. World Registry and Play Flow

The Lobby Service assigns characters to World Servers based on the selected starting nation or zone.

```
                  ┌────────────────┐
                  │ World Registry │
                  └───────┬────────┘
                          ▲
                          │ 1. Lookup zoneId
                          │
  ┌─────────┐       ┌─────┴─────────┐       ┌─────────────────┐
  │ Gateway │──────▶│ Lobby Service │──────▶│ World Service A │
  └─────────┘       └───────────────┘       └─────────────────┘
       │ 4. Register Session Route                  │
       └────────────────────────────────────────────┘
                    3. PLAY_OK (with Session ID)
```

1.  **Play Intent:** Client submits `PLAY` with `characterId` and `authToken`.
2.  **Lobby Lookup:** Lobby Service retrieves the character's `currentZoneId` from the database. It queries the **World Registry** (which holds active World Server registrations):
    `String worldAddr = worldRegistry.getServerForZone(currentZoneId);`
3.  **Session Assignment:** Lobby Service makes an mTLS call to the target World Server:
    `assign_session(accountId, characterId, zoneId)`
4.  **Session Boot:** World Server instantiates the session state, generates a `sessionId`, and replies to Lobby with `PLAY_OK { sessionId, worldAddress }`.
5.  **Route Setup:** Lobby returns this back to the Gateway. The Gateway stores the route mapping, and returns the response to the client.

---

## 5. Step-by-Step Implementation Plan

### Step 1: Create Maven modules
1. Rename/restructure the folder directory.
2. Edit root `pom.xml` to declare the aggregators.
3. Write `pom.xml` descriptors for `gateway`, `server/login-service`, `server/lobby-service`, and `server/world-service`.
4. Validate project compiles: `mvn clean test-compile`.

### Step 2: Migrate database and handlers
1. Move the `AccountRepository` and Argon2 auth logic to `login-service`.
2. Move `CharacterRepository` and character CRUD checks to `lobby-service`.
3. Move `SessionRepository`, `ZoneManager`, and keeping loops to `world-service`.
4. Create independent main class boot environments using Micronaut in each service.

### Step 3: Implement Gateway Proxy Socket
1. Write the proxy socket in `gateway` using Netty QUIC.
2. Build the forwarding routing engine (handling client packets $\rightarrow$ forwarding to services $\rightarrow$ returning responses to clients).
3. Secure connections using mTLS contexts.

### Step 4: Update run-e2e.sh
Modify `scripts/run-e2e.sh` to boot the individual microservices concurrently:
```bash
# Start microservices in background
java -cp "server/login-service/target/..." catalyst.server.login.LoginApplication &
java -cp "server/lobby-service/target/..." catalyst.server.lobby.LobbyApplication &
java -cp "server/world-service/target/..." catalyst.server.world.WorldApplication &
java -cp "gateway/target/..." catalyst.gateway.GatewayApplication &
```
Ensure timeouts and cleanup hooks are correctly wired to terminate all four background PIDs.
