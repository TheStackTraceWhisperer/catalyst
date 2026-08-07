# World Service & Simulation Architecture

This document tracks the details, design decisions, and future tasks for the Catalyst World Service.

---

## 📐 Architecture Details

The **World Service** is a stateful, microservice-based environment responsible for active player sessions, entity management, combat math, and world ticks.

* **Transport:** Internal QUIC endpoint communicating over mutual TLS (mTLS) with the Gateway Server.
* **State Management:** Interacts with the World State database to keep track of active sessions, player positions, and zone populations.
* **Sequential Simulation:** Needs to process gameplay packets sequentially to prevent concurrency anomalies (e.g. database race conditions during chest looting).

---

## 📋 TODO Tasks (Unprioritized)

- **Implement Zone Tick Loop:** Build the `ZoneMessageDispatcher` running a 10Hz sequential game tick (100ms interval) to process player inputs, updates, AI, and entity syncing.
- **Handler Strategy Refactoring:** Split monolithic `WorldHandler` methods into individual class-level strategy beans implementing the `PacketHandler<T>` interface.
- **World Registry Integration:** Integrate with a central World Registry so the Lobby Service can dynamically look up which World Server instance has been assigned to a given `zoneId`.
- **Entity Spawning & Tracking:** Implement basic spatial entity tracking (players, monsters, NPCs) within zones, mapping coordinate updates (`x, y, z, rot`).
- **Session Expiry & Cleanup:** Monitor keepalive timestamps and forcefully clean up database session entries for players who have disconnected or missed heartbeats for over 30 seconds.
- **Cross-Zone Handover:** Establish procedures for handling zone transitions, transferring player states from one World Server instance to another via the Gateway.
