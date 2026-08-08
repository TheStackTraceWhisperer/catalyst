# Task: Fory Class Registration

**Priority:** Medium (security hardening)  
**Area:** Common / Network / Serialization
**Effort:** Small (1-2 days)  

## Problem

`ForySerializer` currently runs with `requireClassRegistration(false)`:

```java
private static final ThreadSafeFory FORY = Fory.builder()
    .withLanguage(Language.JAVA)
    .requireClassRegistration(false)
    .buildThreadSafeFory();
```

This means Fory will deserialize any class it encounters in the payload bytes, including classes that were never intended to be part of the network protocol. This is a classic deserialization attack surface — a crafted payload could instantiate arbitrary classes on the receiving JVM.

Fory itself warns about this on every startup:
> "Class registration isn't forced, unknown classes can be deserialized. If the environment isn't secure, please enable class registration."

## Package Organization Update
Since all DTO records have been reorganized into dedicated subpackages based on their backend service:
* `catalyst.common.dto.login`
* `catalyst.common.dto.lobby`
* `catalyst.common.dto.world`

We can now leverage package-based class-path scanning or package-level validation filters to safely authorize these specific package namespaces instead of having to register every DTO class manually at startup, keeping the registration process maintenance-free.

## What Needs to Happen

### 1. Enumerate All Network DTOs
All classes that can legally appear in a `GatewayFrame` payload must be explicitly registered. These are all records in `common-dto` that implement `GatewayMessage`, plus `GatewayControlMessage` from `common-network`:

- `LoginRequest`, `LoginResponse`
- `CharListRequest`, `CharListResponse`
- `CharCreateRequest`, `CharCreateResponse`
- `CharSelectRequest`, `CharSelectResponse`
- `CharDeleteRequest`, `CharDeleteResponse`
- `PlayRequest`, `PlayResponse`
- `PingRequest`, `PingResponse`
- `LogoutRequest`, `LogoutResponse`
- `GatewayControlMessage`

### 2. Register All Classes at Service Startup

> [!IMPORTANT]
> `common-network` does **not** depend on `common-dto` — and it must not. Adding DTO references into `ForySerializer` would create a circular dependency. Registrations must happen at the application layer, not in the serializer itself, or dynamically via package scanning.

Each service that participates in Fory serialization must register all DTOs it can legally receive or send at startup, before any network traffic is processed. This is best done in a `@PostConstruct` method or an `ApplicationEventListener<ServerStartupEvent>` in each service's application class.

```java
// Example: in GatewayApplication or WorldServiceApplication
@PostConstruct
void registerForyClasses() {
    ForySerializer.register(LoginRequest.class);
    ForySerializer.register(LoginResponse.class);
    // ... all DTOs this service touches
}
```

`ForySerializer` needs to expose a `register(Class<?>)` method that delegates to the underlying `ThreadSafeFory` instance.

### 3. Keep Registration Exhaustive Per Service
Each service only needs to register the DTOs it actually deserializes:

| Service | Classes to register |
|---|---|
| `login-service` | `LoginRequest`, `LoginResponse` |
| `lobby-service` | `CharListRequest/Response`, `CharCreateRequest/Response`, `CharSelectRequest/Response`, `CharDeleteRequest/Response`, `PlayRequest/Response` |
| `world-service` | `PingRequest/Response`, `LogoutRequest/Response` |
| `gateway` | `GatewayControlMessage` only — all game DTOs are forwarded as opaque `byte[]` and are never deserialized by the gateway |
| `client-network` | All DTOs (client may receive any response) |

###  acceptance Criteria
- `requireClassRegistration(true)` is set in `ForySerializer`.
- All current network DTOs are explicitly registered.
- The Fory startup warnings no longer appear in service logs.
- E2E test harness still passes.
- Attempting to deserialize an unregistered class throws a clear exception.
