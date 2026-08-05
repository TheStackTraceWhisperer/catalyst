# Milestone 2: Kernel Architecture

## Goal

Deliver the structural foundations that transform the Milestone 1 proof-of-life into a robust, maintainable kernel. M1 proved the slice works end-to-end. M2 makes it production-ready in architecture.

This milestone does **not** add new game features. Every change is architectural — the same login/character/session flow that works in M1 must still work identically at the end of M2, but the code structure, configuration model, and extension points should be unrecognizable.

## Blueprint

The `reference/october` project demonstrates the target patterns for the client engine: Micronaut + LWJGL, `IService`-based engine loop, stack-based `ApplicationStateService`, and `@Prototype` state beans. We adopt these patterns (not the code) as the structural blueprint for `engine` and the client.

## Embedded Specification (M2)

This section is the consolidated source of truth for Milestone 2 architecture.

### Engine Kernel (`engine`)

- `Launcher` boots Micronaut and runs `Engine`.
- `Engine` runs ordered `IService` implementations and exits cleanly when state stack is empty.
- Core services: `GlfwContextService`, `WindowService` (OpenGL 4.6), `ImGuiService`, `FrameTimeService`, `ApplicationStateService`.
- `ApplicationLoopPolicy` defines loop continuation strategy.

### Micronaut and Configuration Conventions

- Constructor injection via Lombok `@RequiredArgsConstructor` on injectable beans.
- Runtime tuning constants are represented as `@ConfigurationProperties` with class-level defaults.
- `application.yml` and environment variables are override layers, not the primary source of defaults.
- Keepalive interval is server-owned (`catalyst.server.keepalive-interval-ms`), communicated in `PLAY_OK`, and honored by client runtime.

### Client State Machine and Phase-Locking

- States: `UnauthenticatedState`, `AuthenticatedState`, `CharacterSelectedState`, `InGameState`, `LocalZoneState`.
- `ApplicationStateService` owns transitions (`pushState`, `popState`, `changeState`).
- Structural phase-locking is enforced at auth/session boundaries by state-owned operations, not scattered token/session guard checks.
- Panels are intent-only; network actions execute in state classes and injected services.

### SOLID Decomposition and Ownership

- Monolithic mains were split into focused handlers, repositories, services, and state classes.
- Server handlers orchestrate flow; repository classes own SQL access.
- Client network boundary is centralized via `QuicGatewayService` (including typed decode helpers for repeated message shapes).

### Wire Protocol v2

- `MessageFrame` includes typed field accessors and builder API.
- `_v=2` version field is injected on outbound encoding.
- Version mismatch is logged for compatibility visibility; hard enforcement is deferred.

## Scope

### 1. `engine` Module (new)

Extract a new Maven module `engine` that provides the shared client engine kernel:

- `Launcher` — starts Micronaut `ApplicationContext`, retrieves `Engine` bean, calls `engine.run()`
- `Engine` — `@Singleton`, owns the main loop, ticks `List<IService>` ordered by `executionOrder()`
- `IService` — interface with default `start()`, `stop()`, `update()`, `update(float dt)`, `executionOrder()`
- `ApplicationLoopPolicy` — strategy for loop continuation (standard: window-close, frame-limited, time-limited)
- `ApplicationState` — interface: `onEnter`, `onUpdate(float)`, `onExit`, `onResume`, `onSuspend`, `systems()`
- `ApplicationStateService` — stack-based FSM: `pushState`, `popState`, `changeState`
- `GlfwContextService` — GLFW init/terminate as `IService`
- `WindowService` — GLFW window creation/management as `IService`, OpenGL 4.6 core context
- `ImGuiService` — Dear ImGui init/frame/render as `IService`

### 2. Micronaut Across All Modules

- Add Micronaut to `client`, `server`, `engine`
- `@Singleton`, `@Prototype`, `@Inject`, `@RequiredArgsConstructor` (Lombok) replace all manual construction
- `@ConfigurationProperties` beans replace every hardcoded constant
- Runtime defaults are defined in `@ConfigurationProperties`; `application.yml`/env vars provide overrides
- Server uses Micronaut for handler/repository/service wiring and DB pool configuration

### 3. Application State Machine (Client)

Replace scattered `if (!hasAuthToken())` phase checks with formal `ApplicationState` implementations:

- `UnauthenticatedState` — login panel, mode selector
- `AuthenticatedState` — character list, create form, select/delete
- `CharacterSelectedState` — character ready, Play button
- `InGameState` — session active, keepalive, logout
- `LocalZoneState` — local-only mode

Each state owns its UI panel lifecycle. The `ApplicationStateService` drives `onEnter`/`onExit`/`onUpdate`. Phase-locking is structural at auth/session boundaries — operations are exposed by state, not by scattered token/session guard checks.

### 4. SOLID Refactor — Client

Decompose `ClientMain` (~600 lines) into:

- State classes (per above)
- UI panel classes per state (`LoginPanel`, `CharacterPanel`, `InGamePanel`)
- `QuicGateway` promoted to `@Singleton` service
- `KeepAliveService` — encapsulates the ping loop, injected into `InGameState`

### 5. SOLID Refactor — Server

Decompose `ServerMain` (~650 lines) into focused classes:

- `LoginHandler` — auth, token issuance
- `LobbyHandler` — character CRUD
- `WorldHandler` — PLAY, PING, LOGOUT, zone ops
- `AccountRepository` — all `accounts` DB ops
- `CharacterRepository` — all `characters` / `character_jobs` DB ops
- `SessionRepository` — all `accounts_sessions` DB ops
- `AuthTicketStore` — in-memory ticket map (encapsulated, injectable)
- `ZoneManager` — zone population tracking
- `MessageDispatcher` — routes to handlers
- `QuicServerTransport` — unchanged, but wired by DI

### 6. Lombok Adoption

Add Lombok across all modules. Primary use:

- `@RequiredArgsConstructor` on all `@Singleton`/`@Prototype` beans (Micronaut honors constructor injection without `@Inject` on Lombok-generated constructors when combined with the annotation processor)
- `@Slf4j` replaces all `LoggerFactory.getLogger(...)` boilerplate
- `@Value` / `@Builder` where appropriate for immutable DTOs

### 7. OpenGL 4.6 Upgrade

- Bump GLFW window hints from OpenGL 3.2 to 4.6 core profile
- Update `WindowService` (extracted from `ClientMain`) to configure the 4.6 context
- No rendering code changes required in M2 (still ImGui-only); the upgrade simply removes an artificial ceiling

### 8. WireCodec v2

Improve the wire protocol without breaking the existing message format:

- Add protocol version field to every frame
- `MessageFrame` gains typed accessors (`getInt`, `getLong`, `getFloat`, `getBoolean`) with fallback/default
- `WireCodec` adds a builder API for constructing frames without raw `Map<String, String>`
- Maintain full backward compatibility with existing message types

## Out of Scope

- New game features (combat, movement, rendering, assets)
- Gateway / multi-server split
- Apache Fory binary serialization (deferred to world server milestone)
- Zone rendering or actual OpenGL draw calls

## Acceptance Criteria

- [x] `engine` module exists with `Launcher`, `Engine`, `IService`, `ApplicationStateService`, `ApplicationState`, `GlfwContextService`, `WindowService`, `ImGuiService`
- [x] `client` uses `Launcher.run()` as entry point; Micronaut context wires all beans
- [x] `server` uses Micronaut for all bean wiring; handlers and repositories are separate classes
- [x] Runtime constants are represented by `@ConfigurationProperties` (class defaults), with `application.yml`/env used for overrides
- [x] Client UI is driven by `ApplicationStateService`; each UI phase is a distinct `ApplicationState`
- [x] Phase-locking is structural at auth/session boundaries, not conditional (`if (!hasAuthToken())`)
- [x] Lombok `@RequiredArgsConstructor` used throughout; no manual `new` for injectable beans
- [x] `@Slf4j` used throughout; no `LoggerFactory.getLogger()` boilerplate
- [x] GLFW window creates an OpenGL 4.6 core profile context
- [x] `MessageFrame` has typed accessors; `WireCodec` has a builder API; frames include protocol version
- [x] Full M1 functional test passes: login → create char → select → play → ping → logout
