# Spec: Protocol Status and Error Conventions

## Purpose

Define uniform response semantics for Milestone 1 client/server messaging.

## Message Envelope (Minimum)

- `protocolVersion`
- `messageType`
- `requestId` (for request/response correlation)
- `statusCode`
- `payload` (optional)
- `errorMessage` (optional, sanitized)

## Status Code Set (Initial)

- `OK`
- `INVALID_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `RATE_LIMITED`
- `SERVER_ERROR`

## Rules

- Every request receives a terminal response (success or error).
- `errorMessage` must be safe for client display/logging (no sensitive internals).
- Unknown message types return `INVALID_REQUEST`.
- Authentication failures return `UNAUTHORIZED` with auth-specific detail code.

## Milestone 1 Done Criteria

- Client and server both implement and honor the same envelope/status semantics.
- Failures are actionable in logs and deterministic for client behavior.

