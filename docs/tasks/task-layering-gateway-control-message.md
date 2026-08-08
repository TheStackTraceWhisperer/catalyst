# Task: Relocate GatewayControlMessage Out of common-network

**Priority:** Low (exposure, not an active bug)  
**Area:** Module Layering / Common

## Problem

`GatewayControlMessage` currently lives in `common-network`, which is a dependency of both the
server-side modules **and** the client-side modules. This means the client has classpath visibility
into a type it should never know exists.

`GatewayControlMessage` is a server/gateway-internal contract:
- The **gateway** deserializes it to perform connection state transitions.
- The **login-service** and **lobby-service** produce it to signal those transitions.
- The **client** never sends, receives, or processes it — the gateway swallows all `FLAG_CONTROL`
  frames before they reach the client.

This is a layering violation. The client module `client-network` depending on `common-network`
should not implicitly expose server-internal protocol types to the client.

## What Needs to Happen

`GatewayControlMessage` should be moved to a module that is visible to the gateway and backend
services but **not** to the client. The exact name and structure of that module is a decision to
be made at implementation time — candidates include a new `common-server-protocol` module or an
alternative approach.

Constraints to respect at design time:
- The new home must not introduce a circular dependency with `common-network` (which provides
  `GatewayFrame`, `ForySerializer`, and `GatewayMessage` — all of which `GatewayControlMessage`
  depends on).
- `server-common` is not the right home as it is intentionally kept free of Netty/QUIC
  infrastructure dependencies.
- The client modules (`client-network`, `client-application`, `client-engine`) must not depend
  on the new module.

## Acceptance Criteria
- `GatewayControlMessage` is no longer on the classpath of any client module.
- The gateway and all backend services still compile and function correctly.
- E2E test harness still passes.
