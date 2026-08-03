# LSB Divergence Register

## Purpose

Track all intentional deviations from the LandSandBoat (LSB) server implementation so design decisions remain explicit, reviewable, and maintainable over time.

This is a living document and should be updated whenever an implementation choice departs from LSB behavior, architecture, protocol, data model, or operational assumptions.

## Tracking Rules

- Record divergences at the **highest meaningful level** first.
- If one decision affects many systems, capture a **single high-level divergence** and list impacted domains.
- Add subsystem-level entries only when implementation details materially differ beyond the high-level divergence.
- For each divergence, capture:
  - decision
  - rationale
  - impact
  - compatibility implications
  - status

## Divergence Format

| ID | Area | Divergence | Rationale | Impacted Systems | Compatibility Notes | Status |
|---|---|---|---|---|---|---|

## High-Level Divergences

| ID | Area | Divergence | Rationale | Impacted Systems | Compatibility Notes | Status |
|---|---|---|---|---|---|---|
| D-001 | Transport/Networking | Use **QUIC-based** client/server transport instead of LSB networking stack. | Modern reliability/performance model, built-in TLS, support for reliable streams and optional unreliable datagrams. | Login/auth flow, session transport, message framing, timeout/heartbeat behavior, deployment/runtime networking. | Protocol and transport are intentionally non-compatible with LSB network layer. Server behavior references may still align conceptually. | Approved |
| D-002 | Cryptography in Transport | Remove LSB packet crypto assumptions tied to legacy transport (e.g., packet-layer encryption/decryption patterns such as Blowfish usage in that stack) and rely on QUIC/TLS security model. | Avoid duplicate/legacy crypto layers where transport already provides confidentiality/integrity. | Packet codec design, auth/session handshake, key lifecycle, network diagnostics/tooling. | Message schemas may be inspired by LSB, but wire protection and packet flow mechanics differ. | Approved |
| D-006 | Runtime Mode Model | Support a **local-only client runtime mode** that bypasses live server dependency for development workflows. | Enables rapid iteration on resource decode/render and client systems without requiring backend availability. | Client bootstrap, character initialization, zone bootstrap, debug tooling, test workflows. | Not equivalent to LSB runtime assumptions; this is a project-specific development capability. | Approved |

## Subsystem Divergences (Initial)

| ID | Area | Divergence | Rationale | Impacted Systems | Compatibility Notes | Status |
|---|---|---|---|---|---|---|
| D-003 | Session Identity | Session lifecycle persisted in project-defined schema aligned to LSB semantics, not 1:1 table/column parity. | Preserve behavior parity where useful while allowing modernized model and naming. | `accounts_sessions` equivalent, timeout cleanup, double-login prevention, observability. | Data migration/interchange with LSB DB is not assumed. | Planned |
| D-004 | Auth Security | Password verification standard is **Argon2id**. | Current security best practice and stronger default posture. | Account/auth services, account storage, operational security controls. | Not compatible with any legacy hash assumptions without explicit bridge logic (out of scope). | Approved |
| D-005 | Client Integration Surface | Milestone 1 uses a Java LWJGL + Dear ImGui client shell for login + diagnostics, unlike LSB’s original client assumptions. | Needed for direct end-to-end validation and rapid debugging in this project. | Client UX, debugging workflow, integration testing velocity. | No behavior conflict on server domain rules; tooling surface is project-specific. | Approved |

## Review Cadence

- Update this file for every architecture decision that diverges from LSB.
- Revisit entries at each milestone boundary.
- Promote recurring subsystem deviations into high-level entries when they affect multiple domains.
