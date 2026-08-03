# Milestone 2: Kernel Architecture

## Goal

Deliver the structural foundations that transform the Milestone 1 proof-of-life into a robust, maintainable kernel. M1 proved the slice works end-to-end. M2 makes it production-ready in architecture.

This milestone does **not** add new game features. Every change is architectural — the same login/character/session flow that works in M1 must still work identically at the end of M2, but the code structure, configuration model, and extension points should be unrecognizable.

## Blueprint

The `reference/october` project demonstrates the target patterns for the client engine: Micronaut + LWJGL, `IService`-based engine loop, stack-based `ApplicationStateService`, and `@Prototype` state beans. We adopt these patterns (not the code) as the structural blueprint for `ffxi-engine` and the client.

## Scope

### 1. `ffxi-engine` Module (new)

Extract a new Maven module `ffxi-engine` that provides the shared client engine kernel:

- `Launcher` — starts Micronaut `ApplicationContext`, retrieves `Engine` bean, calls `engine.run()`
- `Engine` — `@Singleton`, owns the main loop, ticks `List<IService>` ordered by `executionOrder()`
- `IService` — interface with default `start()`, `stop()`, `update()`, `update(float dt)`, `executionOrder()`
- `ApplicationLoopPolicy` — strategy for loop continuation (standard: window-close, frame-limited, time-limited)
- `ApplicationState` — interface: `onEnter`, `onUpdate(float)`, `onExit`, `onResume`, `onSuspend`, `systems()`
- `ApplicationStateService` — stack-based FSM: `pushState`, `popState`, `changeState`
- `GlfwContextService` — GLFW init/terminate as `IService`
- `WindowService` — GLFW window creation/management as `IService`, OpenGL 4.6 core context
- `ImGuiService` — Dear ImGui init/frame/render as `IService`

See: [Engine Module Spec](./specs/milestone-2/engine-module.md)

### 2. Micronaut Across All Modules

- Add Micronaut to `ffxi-client`, `ffxi-server`, `ffxi-engine`
- `@Singleton`, `@Prototype`, `@Inject`, `@RequiredArgsConstructor` (Lombok) replace all manual construction
- `@ConfigurationProperties` beans replace every hardcoded constant
- `application.yml` with `dev` and `prod` environment profiles in each module
- Server uses Micronaut for handler/repository/service wiring and DB pool configuration

See: [Micronaut Configuration Spec](./specs/milestone-2/micronaut-configuration.md)

### 3. Application State Machine (Client)

Replace scattered `if (!hasAuthToken())` phase checks with formal `ApplicationState` implementations:

- `UnauthenticatedState` — login panel, mode selector
- `AuthenticatedState` — character list, create form, select/delete
- `CharacterSelectedState` — character ready, Play button
- `InGameState` — session active, keepalive, logout
- `LocalZoneState` — local-only mode

Each state owns its UI panel class. The `ApplicationStateService` drives `onEnter`/`onExit`/`onUpdate`. Phase-locking is structural — invalid operations simply don't exist on the wrong state type.

See: [Application State Machine Spec](./specs/milestone-2/application-state-machine.md)

### 4. SOLID Refactor — Client

Decompose `ClientMain` (~600 lines) into:

- State classes (per above)
- UI panel classes per state (`LoginPanel`, `CharacterPanel`, `InGamePanel`)
- `QuicGateway` promoted to `@Singleton` service
- `KeepAliveService` — encapsulates the ping loop, injected into `InGameState`

See: [SOLID Refactor Spec](./specs/milestone-2/solid-refactor.md)

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

See: [SOLID Refactor Spec](./specs/milestone-2/solid-refactor.md)

### 6. Lombok Adoption

Add Lombok across all modules. Primary use:

- `@RequiredArgsConstructor` on all `@Singleton`/`@Prototype` beans (Micronaut honors constructor injection without `@Inject` on Lombok-generated constructors when combined with the annotation processor)
- `@Slf4j` replaces all `LoggerFactory.getLogger(...)` boilerplate
- `@Value` / `@Builder` where appropriate for immutable DTOs

See: [SOLID Refactor Spec](./specs/milestone-2/solid-refactor.md)

### 7. OpenGL 4.6 Upgrade

- Bump GLFW window hints from OpenGL 3.2 to 4.6 core profile
- Update `WindowService` (extracted from `ClientMain`) to configure the 4.6 context
- No rendering code changes required in M2 (still ImGui-only); the upgrade simply removes an artificial ceiling

See: [SOLID Refactor Spec](./specs/milestone-2/solid-refactor.md)

### 8. WireCodec v2

Improve the wire protocol without breaking the existing message format:

- Add protocol version field to every frame
- `MessageFrame` gains typed accessors (`getInt`, `getLong`, `getFloat`, `getBoolean`) with fallback/default
- `WireCodec` adds a builder API for constructing frames without raw `Map<String, String>`
- Maintain full backward compatibility with existing message types

See: [Wire Protocol v2 Spec](./specs/milestone-2/wire-protocol-v2.md)

## Out of Scope

- New game features (combat, movement, rendering, assets)
- Gateway / multi-server split
- Apache Fory binary serialization (deferred to world server milestone)
- Zone rendering or actual OpenGL draw calls

## Acceptance Criteria

- [ ] `ffxi-engine` module exists with `Launcher`, `Engine`, `IService`, `ApplicationStateService`, `ApplicationState`, `GlfwContextService`, `WindowService`, `ImGuiService`
- [ ] `ffxi-client` uses `Launcher.run()` as entry point; Micronaut context wires all beans
- [ ] `ffxi-server` uses Micronaut for all bean wiring; handlers and repositories are separate classes
- [ ] All hardcoded constants replaced with `@ConfigurationProperties` beans backed by `application.yml`
- [ ] `dev` and `prod` environment profiles exist and load correctly
- [ ] Client UI is driven by `ApplicationStateService`; each UI phase is a distinct `ApplicationState`
- [ ] Phase-locking is structural (wrong operations do not exist on the active state) not conditional (`if (!hasAuthToken())`)
- [ ] Lombok `@RequiredArgsConstructor` used throughout; no manual `new` for injectable beans
- [ ] `@Slf4j` used throughout; no `LoggerFactory.getLogger()` boilerplate
- [ ] GLFW window creates an OpenGL 4.6 core profile context
- [ ] `MessageFrame` has typed accessors; `WireCodec` has a builder API; frames include protocol version
- [ ] Full M1 functional test passes: login → create char → select → play → ping → logout

## Specifications

- [Engine Module](./specs/milestone-2/engine-module.md)
- [Micronaut Configuration](./specs/milestone-2/micronaut-configuration.md)
- [Application State Machine](./specs/milestone-2/application-state-machine.md)
- [SOLID Refactor (Client + Server)](./specs/milestone-2/solid-refactor.md)
- [Wire Protocol v2](./specs/milestone-2/wire-protocol-v2.md)
