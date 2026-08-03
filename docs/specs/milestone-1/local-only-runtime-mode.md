# Spec: Local-Only Runtime Mode

## Purpose

Enable client-side development workflows that do not depend on a running server, while preserving a clean boundary between offline simulation and server-authoritative play.

## Functional Requirements

- Support a client startup mode that bypasses server connection/authentication.
- Load a preset development character profile.
- Load directly into a configured zone/entry context.
- Enable movement and camera controls for in-zone iteration.
- Reuse the same core render/update pipeline used in server-connected mode where possible.

## Configuration Inputs

- `mode=local` runtime flag or equivalent launcher setting
- preset character configuration source
- initial zone ID and spawn transform

## Rules

- Local-only mode must be clearly marked in UI/logs to avoid confusion with server-authoritative sessions.
- Local-only mode must not emit or persist server session records.
- Local-only mode should use modular injection points (character provider, zone bootstrap provider) so future systems can replace stubs cleanly.

## Milestone 1 Done Criteria

- Client can start in local-only mode without server availability.
- Client enters a target zone with preset character and can move.
- Debug logs clearly indicate local-only runtime path and zone bootstrap events.

