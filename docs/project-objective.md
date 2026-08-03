# Project Objective

## Purpose

Build a Java-based FFXI project composed of a real desktop client and a real server:

- **Client reference:** `xim-source`
- **Server reference:** `reference/server` (LandSandBoat)
- **Precursor/reference project:** `/home/samuel/projects/ffxi-xim-analysis`

This project is not a simulator. It is intended to become a working client/server system.

## Core Goals

- A client that can **read, decrypt, decode, and render unmodified game asset files**.
- A client that can **communicate with the server** that orchestrates sessions and server-authoritative systems.
- A client that can run in **local-only mode** for asset/system development without a live server dependency.
- A server that can **accept client connections, handle authentication, and manage sessions and zones**.
- All components should provide **extensible kernels and clear injection points** so new systems can be plugged in incrementally.

## Architectural Direction

- Keep **client, server, and shared/common code** modular and clearly separated.
- Support dual client runtime paths: **server-connected mode** and **local-only mode**.
- Keep **server authority boundaries** explicit and enforceable.
- Design core systems to be **data-driven, testable, and replaceable**.
- Use **Dear ImGui** for runtime debug tooling and inspection in the desktop client.
- Keep LandSandBoat as a **server behavior/domain reference**, but do **not** mirror its network architecture directly.
- Prefer a **modern transport stack** based on **QUIC (HTTP/3-era technology)** for client/server communication.

## Networking Direction (QUIC)

- Use QUIC to support both:
  - **Reliable, ordered streams** for authentication, session control, and authoritative state transitions.
  - **Unreliable datagrams** (where supported) for latency-sensitive transient updates.
- Leverage QUIC's built-in **TLS 1.3**, congestion control, and loss recovery for stronger baseline security and network stability.
- Validate Java QUIC library maturity early, with specific focus on **datagram support, performance characteristics, and operational stability**.

## Reuse Strategy

- Treat `/home/samuel/projects/ffxi-xim-analysis` as a **primary implementation source** for existing IO/DAT/crypto/decode functionality.
- Prefer **copying proven code into new focused modules** in this repository (instead of rewriting), while preserving clean module boundaries.
- Expose reused code through stable module APIs so the client (and shared/common systems) can import them without tight coupling.
