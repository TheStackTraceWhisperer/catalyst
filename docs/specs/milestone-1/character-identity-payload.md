# Spec: Character Identity Payload

## Purpose

Provide a minimal post-auth character/profile payload to prove authenticated data flow.

## Payload (Minimum)

- `characterId` (stable ID)
- `accountId`
- `name`
- `homeZoneId`
- `homePosition` (`x`, `y`, `z`, `heading`)
- `currentZoneId`
- `currentPosition` (`x`, `y`, `z`, `heading`)

## Flow

1. Client authenticates.
2. Server resolves default/selected character identity.
3. Server sends identity payload in authenticated response flow.

## Rules

- Character identity is server-authoritative.
- If account has no valid character, return explicit status/error.
- Payload must be versioned or envelope-wrapped for forward compatibility.
- If `currentZoneId`/`currentPosition` are unavailable, server falls back to `homeZoneId`/`homePosition`.
- Zone attach for session initialization uses current location when present, otherwise home location.

## Milestone 1 Done Criteria

- Client receives and parses identity payload.
- Server uses payload to attach session to zone skeleton context.
