# Task: Fory Class Registration

**Priority:** Medium (security hardening)  
**Area:** Common / Network / Serialization

## Problem

`ForySerializer` currently runs with `requireClassRegistration(false)`:

```java
private static final ThreadSafeFory FORY = Fory.builder()
    .withLanguage(Language.JAVA)
    .requireClassRegistration(false)
    .buildThreadSafeFory();
```

This means Fory will deserialize any class it encounters in the payload bytes, including classes
that were never intended to be part of the network protocol. This is a classic deserialization
attack surface — a crafted payload could instantiate arbitrary classes on the receiving JVM.

Fory itself warns about this on every startup:
> "Class registration isn't forced, unknown classes can be deserialized. If the environment isn't
> secure, please enable class registration."

## What Needs to Happen

### 1. Enumerate All Network DTOs
All classes that can legally appear in a `GatewayFrame` payload must be explicitly registered.
These are all records in `common-dto` that implement `GatewayMessage`, plus
`GatewayControlMessage` from `common-network`:

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
> `common-network` does **not** depend on `common-dto` — and it must not. Adding DTO references
> into `ForySerializer` would create a circular dependency. Registrations must happen at the
> application layer, not in the serializer itself.

Each service that participates in Fory serialization must register all DTOs it can legally
receive or send at startup, before any network traffic is processed. This is best done in a
`@PostConstruct` method or an `ApplicationEventListener<ServerStartupEvent>` in each service's
application class:

```java
// Example: in GatewayApplication or WorldServiceApplication
@PostConstruct
void registerForyClasses() {
    ForySerializer.register(LoginRequest.class);
    ForySerializer.register(LoginResponse.class);
    // ... all DTOs this service touches
}
```

`ForySerializer` needs to expose a `register(Class<?>)` method that delegates to the underlying
`ThreadSafeFory` instance.

### 3. Keep Registration Exhaustive Per Service
Each service only needs to register the DTOs it actually handles — not the entire universe. For
example, `login-service` only needs `LoginRequest` and `LoginResponse`. The `gateway` needs all
of them since it proxies all traffic (even though it never deserializes payloads, it may log or
inspect frame types).

### 4. Enforce at Startup

## Acceptance Criteria
- `requireClassRegistration(true)` is set in `ForySerializer`.
- All current network DTOs are explicitly registered.
- The Fory startup warnings no longer appear in service logs.
- E2E test harness still passes.
- Attempting to deserialize an unregistered class throws a clear exception.
