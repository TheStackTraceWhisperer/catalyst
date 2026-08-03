# Spec: SOLID Refactor (Client + Server)

## Purpose

Decompose `ClientMain` and `ServerMain` monoliths into focused, injectable, testable classes following SOLID principles. Adopt Lombok and upgrade to OpenGL 4.6.

## Lombok Adoption

Add Lombok to all modules. Core usage:

| Annotation | Usage |
|---|---|
| `@RequiredArgsConstructor` | Constructor injection on all `@Singleton`/`@Prototype` beans |
| `@Slf4j` | Replaces `LoggerFactory.getLogger(ClassName.class)` on every class |
| `@Value` | Immutable record-style DTOs where records aren't suitable |
| `@Builder` | Complex construction of domain objects |

`lombok.config` at project root:
```
lombok.addLombokGeneratedAnnotation = true
config.stopBubbling = true
```

Lombok annotation processor must run **before** the Micronaut annotation processor in Maven build configuration.

## OpenGL 4.6 Upgrade

`WindowService.start()` sets:
```java
glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
```

No rendering code changes are required in M2 (ImGui renders via its own compatibility path). The upgrade removes the 3.2 ceiling for future rendering work.

---

## Client Refactor

### Target Module Structure

```
ffxi-client/src/main/java/catalyst/ffxi/client/
  ClientApplication.java           -- Micronaut entry point (calls Launcher.run)
  config/
    ClientStateConfiguration.java  -- @Factory: binds initial state
  state/
    UnauthenticatedState.java
    AuthenticatedState.java
    CharacterSelectedState.java
    InGameState.java
    LocalZoneState.java
  ui/
    LoginPanel.java
    CharacterPanel.java
    CharacterCreateForm.java
    InGamePanel.java
    DebugLogPanel.java
    StatusBar.java
  network/
    QuicGatewayService.java        -- @Singleton wrapper around QuicGateway
    KeepAliveService.java
  model/
    CharacterViewModel.java        -- UI-facing character data
```

### `ClientApplication`

```java
public class ClientApplication {
    public static void main(String[] args) {
        Launcher.run(ClientApplication.class, args);
    }
}
```

### Responsibilities

| Class | Single Responsibility |
|---|---|
| `UnauthenticatedState` | Renders `LoginPanel`; calls `QuicGatewayService.login()` on intent; transitions to `AuthenticatedState` on success |
| `AuthenticatedState` | Renders `CharacterPanel`; handles list/create/delete; transitions to `CharacterSelectedState` on select |
| `CharacterSelectedState` | Renders `CharacterPanel` with Play visible; transitions to `InGameState` on play |
| `InGameState` | Owns `KeepAliveService`; renders `InGamePanel`; transitions to `UnauthenticatedState` on logout |
| `LoginPanel` | Dear ImGui widgets for username/password + Login button; exposes intent flags |
| `CharacterPanel` | Character list, create form toggle, select/delete buttons; exposes intent flags |
| `CharacterCreateForm` | Race/size/face/job/nation combo inputs; exposes creation data |
| `InGamePanel` | In-game status, ping button, logout button |
| `DebugLogPanel` | Timestamped log viewer with auto-scroll and clear |
| `StatusBar` | Mode, account, session, keepalive status display (always visible) |
| `QuicGatewayService` | `@Singleton` wrapping `QuicGateway`; exposes all network operations as clean method calls |
| `KeepAliveService` | Background ping loop; injectable; start/stop per session |

---

## Server Refactor

### Target Package Structure

```
ffxi-server/src/main/java/catalyst/ffxi/server/
  ServerApplication.java           -- Micronaut entry point
  transport/
    QuicServerTransport.java        -- unchanged, wired by DI
  dispatch/
    MessageDispatcher.java          -- routes MessageFrame to handlers
  handler/
    LoginHandler.java
    LobbyHandler.java               -- character CRUD
    WorldHandler.java               -- PLAY, PING, LOGOUT, zone ops
  repository/
    AccountRepository.java
    CharacterRepository.java
    SessionRepository.java
  session/
    AuthTicketStore.java            -- in-memory ticket map
    ZoneManager.java                -- zone population tracking
  config/
    DatabaseConfiguration.java     -- HikariCP DataSource @Factory
    ServerProperties.java           -- @ConfigurationProperties
```

### Responsibilities

| Class | Single Responsibility |
|---|---|
| `MessageDispatcher` | Switch on `MessageFrame.type()`, delegate to the appropriate handler |
| `LoginHandler` | `LOGIN` → Argon2id verify → issue auth ticket |
| `LobbyHandler` | `CHAR_LIST`, `CHAR_CREATE`, `CHAR_SELECT`, `CHAR_DELETE` |
| `WorldHandler` | `PLAY`, `PING`, `LOGOUT` |
| `AccountRepository` | All SQL against `accounts` table |
| `CharacterRepository` | All SQL against `characters` + `character_jobs` |
| `SessionRepository` | All SQL against `accounts_sessions` |
| `AuthTicketStore` | `issue(accountId)`, `validate(token)`, `expire(token)`, scheduled cleanup |
| `ZoneManager` | `join(sessionId, zoneId)`, `leave(sessionId)`, `getPopulation(zoneId)` |
| `DatabaseConfiguration` | `@Factory` producing `HikariDataSource` from `ServerProperties` |

### `ServerApplication`

```java
@Singleton
@RequiredArgsConstructor
@Slf4j
public class ServerApplication implements ApplicationEventListener<StartupEvent> {
    private final QuicServerTransport transport;
    private final MessageDispatcher dispatcher;
    private final SessionRepository sessions;

    @Override
    public void onApplicationEvent(StartupEvent event) {
        transport.setDispatcher(dispatcher::dispatch);
        try {
            transport.start();
            log.info("Server started");
            transport.awaitShutdown();
        } catch (Exception e) {
            throw new RuntimeException("Server startup failed", e);
        }
    }
}
```

---

## Rule: No Logic in Constructors

All `@Singleton` / `@Prototype` beans have `@RequiredArgsConstructor`. No logic in constructors — only field assignment. Initialisation logic goes in `@PostConstruct` or `start()` (for `IService` implementations).

## Milestone 2 Done Criteria

- [ ] `lombok.config` present at project root
- [ ] Lombok annotation processor configured before Micronaut AP in all module poms
- [ ] `@Slf4j` on all classes; no `LoggerFactory.getLogger()` remaining
- [ ] `@RequiredArgsConstructor` on all injectable beans; no manual `new` for injectables
- [ ] `ClientMain` deleted; `ClientApplication` is the entry point
- [ ] `ServerMain` deleted; `ServerApplication` is the entry point
- [ ] All server DB access in repository classes; no SQL in handler classes
- [ ] `AuthTicketStore` and `ZoneManager` are `@Singleton` beans
- [ ] GLFW window hints set to OpenGL 4.6 core profile
- [ ] All M1 functional tests pass with refactored code
