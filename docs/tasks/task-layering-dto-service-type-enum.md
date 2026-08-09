# Task: Refactor DTOs to use ServiceType Enum

**Priority:** Low (typing improvements)  
**Area:** Common / Network / DTOs  
**Effort:** Small (1-2 days)  

## Problem
In `common-dto`, all network DTO interfaces (such as `LoginGatewayMessage`, `LobbyGatewayMessage`, `WorldGatewayMessage`) implement `GatewayMessage` which currently exposes `byte gatewayFlag()`:

```java
public interface GatewayMessage {
    byte gatewayFlag();
}
```

This requires each of the backend gateway sub-interfaces to return raw magic flag bytes. We have already introduced the `ServiceType` enum to replace magic flag bytes across the transport layer, but DTO definitions are still leaking raw bytes.

## What Needs to Happen
1. Refactor `GatewayMessage` to return the `ServiceType` enum instead of a raw byte:
   ```java
   public interface GatewayMessage {
       ServiceType gatewayServiceType();
   }
   ```
2. Update the backend sub-interfaces (`LoginGatewayMessage`, `LobbyGatewayMessage`, `WorldGatewayMessage`) to return `ServiceType.LOGIN`, `ServiceType.LOBBY`, and `ServiceType.WORLD` respectively.
3. Update `ForyEncoder.java` or `GatewayFrameEncoder.java` to extract `gatewayServiceType().flag()` at the Netty transport boundary.

## Acceptance Criteria
- No DTO interfaces or records reference raw byte flags.
- All routing flags are mapped using the `ServiceType` enum.
- E2E test suite compiles and runs successfully.
