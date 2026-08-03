# Spec: Application State Machine (Client)

## Purpose

Replace `ClientMain`'s scattered boolean phase checks with a formal stack-based `ApplicationStateService` driving distinct `ApplicationState` implementations. Phase-locking becomes structural — the wrong operation simply doesn't exist on the active state type.

## States

| State class | Active when | UI Panel |
|---|---|---|
| `UnauthenticatedState` | App start; after sign out or session end | `LoginPanel` |
| `AuthenticatedState` | After `LOGIN_OK` (auth token held) | `CharacterPanel` |
| `CharacterSelectedState` | After `CHAR_SELECT_OK` (character loaded, no session) | `CharacterPanel` (with Play button) |
| `InGameState` | After `PLAY_OK` (session active, keepalive running) | `InGamePanel` |
| `LocalZoneState` | Local mode "Enter Local Zone" pressed | `InGamePanel` (local variant) |

## Transition Map

```
UnauthenticatedState
    → AuthenticatedState         (LOGIN_OK)

AuthenticatedState
    → CharacterSelectedState     (CHAR_SELECT_OK)
    → UnauthenticatedState       (sign out / auth token expiry)

CharacterSelectedState
    → InGameState                (PLAY_OK)
    → AuthenticatedState         (de-select / back)
    → UnauthenticatedState       (sign out)

InGameState
    → UnauthenticatedState       (LOGOUT / window close / timeout)

LocalZoneState
    → UnauthenticatedState       (mode switch to remote)
```

## State Interface

```java
public interface ApplicationState {
    void onEnter();
    void onUpdate(float dt);
    void onExit();
    default void onResume()  {}  // uncovered by pop
    default void onSuspend() {}  // covered by push
}
```

## Example State Implementation

```java
@Prototype
@RequiredArgsConstructor
@Slf4j
public class InGameState implements ApplicationState {
    private final KeepAliveService keepAlive;
    private final InGamePanel panel;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;

    @Override
    public void onEnter() {
        keepAlive.start();
        log.info("Entered InGame state");
    }

    @Override
    public void onUpdate(float dt) {
        panel.render();
        if (panel.isLogoutRequested()) {
            keepAlive.stop();
            stateService.changeState(unauthProvider::get);
        }
    }

    @Override
    public void onExit() {
        keepAlive.stop();
    }
}
```

## `ApplicationStateService`

`@Singleton`. Stack-based FSM. Injected with `@Named("initial") Provider<ApplicationState>` which resolves to `UnauthenticatedState`.

Operations:
- `pushState(Supplier<ApplicationState>)` — suspends current (onSuspend), enters new (onEnter)
- `popState()` — exits current (onExit), resumes previous (onResume)
- `changeState(Supplier<ApplicationState>)` — exits current, enters new (no stack growth)
- `isEmpty()` — signals `Engine` to exit the main loop

States are `@Prototype` beans — Micronaut creates a fresh instance per `BeanProvider::get` call.

## UI Panel Pattern

Each state owns one UI panel class. The panel is a `@Prototype` bean (fresh instance per state lifecycle) responsible only for:
1. Rendering the Dear ImGui widgets for that phase
2. Exposing intent flags (`isLoginRequested()`, `isLogoutRequested()`, etc.)

The state reads the flags and performs the transitions. No network calls inside panel classes.

```java
@Prototype
@RequiredArgsConstructor
@Slf4j
public class LoginPanel {
    private final ImString username = new ImString("dev", 64);
    private final ImString password = new ImString("dev", 64);
    private boolean loginRequested;

    public void render() {
        ImGui.inputText("Username", username);
        ImGui.inputText("Password", password);
        if (ImGui.button("Login")) loginRequested = true;
    }

    public boolean isLoginRequested()  { return loginRequested; }
    public String getUsername()        { return username.get(); }
    public String getPassword()        { return password.get(); }
    public void   clearIntent()        { loginRequested = false; }
}
```

## Phase-Locking: Structural vs Conditional

**M1 (conditional — what we have now):**
```java
private void createCharacter() {
    if (!hasAuthToken() || hasActiveRemoteSession()) return; // scattered guards
    ...
}
```

**M2 (structural — what we want):**
The `createCharacter()` method only exists on `CharacterPanel`, which is only rendered by `AuthenticatedState`. There is no guard to forget. The wrong operation is unreachable.

## `KeepAliveService`

`@Singleton`. Encapsulates the ping timer. Injected into `InGameState`.

- `start(String host, int port, String sessionId)` — begins 5s ping loop on a background thread
- `stop()` — cancels the loop, sends one final LOGOUT if session is still valid
- Exposes `getStatus()`, `getLastRttMs()`, `getLastOkAt()` for `InGamePanel` display

## Initial State Binding

```java
// In ffxi-client's Micronaut config:
@Factory
public class ClientStateConfiguration {
    @Singleton
    @Named("initial")
    ApplicationState initialState(BeanProvider<UnauthenticatedState> provider) {
        return provider.get();
    }
}
```

## Milestone 2 Done Criteria

- [ ] All five state classes exist and are `@Prototype` beans
- [ ] `ApplicationStateService` drives the engine update loop
- [ ] All UI phase transitions happen through `changeState` / `pushState`
- [ ] No `hasAuthToken()` / `hasActiveSession()` style guards remain in rendering or action code
- [ ] `KeepAliveService` is injectable and owned by `InGameState`
- [ ] Each state has a dedicated panel class; panels have no network dependencies
- [ ] `LocalZoneState` correctly bypasses auth and disables keepalive
