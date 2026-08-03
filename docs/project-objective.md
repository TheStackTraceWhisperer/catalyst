# Project Objective

## Purpose

Build a Java-based FFXI project composed of a real desktop client and a real server:

- **Client reference:** `xim-source` (Kotlin/JS WebGL2 — see `docs/xim-source-architecture.md`)
- **Server reference:** `reference/server` (LandSandBoat)
- **Precursor/reference project:** `/home/samuel/projects/ffxi-xim-analysis`

This project is not a simulator. It is intended to become a working client/server system.

## Core Goals

- A client that can **read, decrypt, decode, and render unmodified game asset files**.
- A client that can **communicate with the server** that orchestrates sessions and server-authoritative systems.
- A client that can run in **local-only mode** for asset/system development without a live server dependency.
- A server that can **accept client connections, handle authentication, manage characters, and manage sessions and zones**.
- All components provide **extensible kernels and clear injection points** so new systems can be plugged in incrementally.

## Architectural Direction

- **Client:** Java 25, LWJGL 3, Dear ImGui, QUIC transport (`QuicGateway`)
- **Server:** Java 25, Netty QUIC, PostgreSQL, HikariCP, Argon2id, Logback
- **Transport:** QUIC (Netty incubator, TLS 1.3) end-to-end — external and internal (see `docs/network-architecture.md`)
- **Server reference:** LandSandBoat for domain behavior; network architecture intentionally diverges (see `docs/lsb-divergence.md`)
- **Build:** Maven multi-module, Java 25 (SDKMAN)

## Networking Direction (QUIC)

- Use QUIC for all transport (client ↔ gateway, gateway ↔ backend services).
- Reliable, ordered streams for auth, session control, and authoritative state transitions.
- Per-request bidirectional streams on persistent `QuicChannel` connections.
- Unreliable datagrams reserved for future latency-sensitive updates.
- Built-in TLS 1.3; mTLS for internal service-to-service communication.

## Current State (Post-Milestone 1 Kernel)

The following is implemented and working end-to-end:

- QUIC client/server with self-signed dev cert
- Account authentication (Argon2id, two-phase: auth token → game session)
- Character lifecycle: create (LSB-aligned fields), list, select, soft-delete
- Character creation: race 1-8 (LSB gender-encoded), body size, face 0-15, starting job 1-6, nation with random zone
- Session lifecycle: PLAY, PING/PONG keepalive, LOGOUT, timeout cleanup
- Zone population tracking (in-memory, per-session)
- Phase-locked LWJGL + Dear ImGui client UI
- PostgreSQL persistence with Docker bootstrap
- Structured logging (SLF4J + Logback)
- Local-only client mode (bypasses server for development)
- `character_jobs` table (per-job level storage, mirrors LSB)

## Reuse Strategy

- Treat `/home/samuel/projects/ffxi-xim-analysis` as a **primary implementation source** for IO/DAT/crypto/decode functionality.
- Prefer **copying proven code into focused modules** (not rewriting), while preserving clean module boundaries.
- Expose reused code through stable module APIs.

## Module Structure

| Module | Role |
|---|---|
| `ffxi-common` | Shared wire codec, domain models |
| `ffxi-server` | Headless QUIC server (Milestone 1 monolith) |
| `ffxi-client` | LWJGL + Dear ImGui desktop client |
