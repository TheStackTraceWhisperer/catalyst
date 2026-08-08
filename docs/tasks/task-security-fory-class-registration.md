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

### 2. Register All Classes in `ForySerializer`
```java
private static final ThreadSafeFory FORY = Fory.builder()
    .withLanguage(Language.JAVA)
    .requireClassRegistration(true)
    .buildThreadSafeFory();

static {
    FORY.register(LoginRequest.class);
    FORY.register(LoginResponse.class);
    // ... all DTOs
}
```

### 3. Enforce at Startup
With `requireClassRegistration(true)`, any payload containing an unregistered class will throw
immediately at deserialization time rather than silently succeeding. This makes protocol violations
loud and fast-failing.

### 4. Keep `ForySerializer` as the Single Source of Truth
All modules (`gateway`, `login-service`, `lobby-service`, `world-service`, `client-network`) share
the same `ForySerializer` from `common-network`. Registration only needs to happen in one place.

## Acceptance Criteria
- `requireClassRegistration(true)` is set in `ForySerializer`.
- All current network DTOs are explicitly registered.
- The Fory startup warnings no longer appear in service logs.
- E2E test harness still passes.
- Attempting to deserialize an unregistered class throws a clear exception.
