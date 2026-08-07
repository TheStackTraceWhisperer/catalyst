# Catalyst MMO: Protocol Conversion & Phase 2.5 Dispatcher Assessment

We have analyzed the current codebase after the conversion to the Fory (Fury) serialization protocol and reviewed it against the blueprint defined in [`phase-2.5-concurrency-dispatcher.md`](file:///home/samuel/projects/ffxi-java/docs/architecture/phase-2.5-concurrency-dispatcher.md).

---

## 🔍 The Situation: Why the Protocol Migration Stopped Here

The Fory protocol migration successfully eliminated the old untyped `MessageFrame`/`WireCodec` stack, replacing it with type-safe Java records (`LoginRequest`, `PlayResponse`, etc.). 

However, to keep the migration compile-safe and runnable, the implementation stopped at a **temporary bridge phase**:
1. **Retained Monolithic Handlers:** The codebase currently keeps the old monolithic handler classes (`LoginHandler`, `LobbyHandler`, `WorldHandler`) and their synchronous methods.
2. **Introduced `ObjectDispatcher`:** A temporary `ObjectDispatcher` class was added to `common/network` to register these handler methods (e.g., `lobbyHandler::handleList`) and route them synchronously on Netty's thread.
3. **Did Not Implement Concurrent/Tick Loops Yet:** The virtual-thread-per-task executor (`StatelessMessageDispatcher`) and the 10Hz tick-loop executor (`ZoneMessageDispatcher`) described in Phase 2.5 were not introduced.

Stopping here was a logical intermediate step to ensure the new network codec was fully functional (passing the `E2EValidationHarness` tests) before executing the major architectural refactoring required for the concurrency dispatcher.

---

## ⚠️ Architectural Divergences & Code Quality Concerns

To align the current state with the target architecture, we must address the following discrepancies:

### 1. Packet Dispatcher Leakage (`ObjectDispatcher` in `common`)
* **Divergence:** The blueprint states that the shared server infrastructure (`server-common`) contains the dispatching contracts and that they **do not** leak into the `client` or `common/network` modules. 
* **Current State:** `ObjectDispatcher` and `RoutingContext` are placed in the `common/network` module, exposing server-only routing details to the client.

### 2. Manual Method Wiring vs. Dependency Injection (DI)
* **Divergence:** The blueprint calls for an open/closed design where new features are added by creating a class implementing `PacketHandler<T>`. The dispatchers (`StatelessMessageDispatcher` / `ZoneMessageDispatcher`) automatically discover and register these beans via Micronaut's dependency injection container (`BeanProvider<PacketHandler<?>>`).
* **Current State:** The handlers are manual method references registered inside the main application startup classes, which is prone to circular dependency errors and makes testing harder.

### 3. Duplicate Framing in the Gateway
* **Concern:** To avoid re-serializing messages, the `GatewayServer` uses `RawFrameCaptureHandler` to capture raw bytes, while `ForyDecoder` separately deserializes the same byte stream to extract session keys. 
* **Improvement:** This can be unified into a single decoder pass that yields a compound message holder containing the routing key, the decoded object, and the raw byte array, removing the array copying in `RawFrameCaptureHandler`.

---

## 🗺️ Path Forward: Implementing Phase 2.5

With the Fory types fully operational, we are now ready to implement the Phase 2.5 blueprint:

1. **Move Infrastructure to `server-common`:**
   * Create `GameCommand` and `PacketHandler` under `catalyst.server.common.dispatch`.
   * Implement `StatelessMessageDispatcher` using a Java Virtual Thread executor.

2. **Refactor Handlers to `PacketHandler` Beans:**
   * Split the methods in `LoginHandler`, `LobbyHandler`, and `WorldHandler` into individual handler beans (e.g., `LoginRequestHandler`, `CharListRequestHandler`, `PlayRequestHandler`).
   * Clean up and delete the temporary `ObjectDispatcher`.

3. **Implement the Zone Tick Loop:**
   * Implement `ZoneMessageDispatcher` inside `world-service` running at 10Hz to handle spatial sequential actions.

4. **Integrate Non-blocking Client Dispatcher:**
   * Implement `ClientDispatcher` in the `client/engine` module to route inbound network packets safely to the single-threaded GLFW/ImGui render loop.
