# Milestone 5: Client and E2E Network Interaction Comparison

This document details the call sequences of `QuicGatewayService` methods by the interactive game client (`ClientApplication` via the state machine) versus the automated E2E harness (`E2EValidationHarness`), comparing behaviors and identifying structural divergences.

---

## 1. ClientApplication Network Sequence

In the interactive client, method execution is driven by user UI inputs triggering transitions across the stack-based application states (`UnauthenticatedState`, `AuthenticatedState`, `CharacterSelectedState`, `InGameState`), with pings handled asynchronously by the `KeepAliveService`.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant App as ClientApplication (State Machine)
    participant KeepAlive as KeepAliveService (Virtual Thread)
    participant GW as QuicGatewayService
    participant Server as Catalyst Server (QUIC)

    Note over App: UnauthenticatedState
    User->>App: Input Username/Password & Login
    App->>GW: login(host, port, user, pass)
    GW->>Server: MessageFrame (LOGIN)
    Server-->>GW: MessageFrame (LOGIN_OK)
    GW-->>App: LoginResponse
    
    Note over App: Transition to AuthenticatedState
    App->>GW: listCharacterSummaries(host, port, authToken)
    GW->>Server: MessageFrame (CHAR_LIST)
    Server-->>GW: MessageFrame (CHAR_LIST_OK)
    GW-->>App: List<CharacterSummary>
    
    opt Optional Character Creation / Deletion
        User->>App: Create Character
        App->>GW: createCharacter(...)
        GW->>Server: MessageFrame (CHAR_CREATE)
        Server-->>GW: MessageFrame (CHAR_CREATE_OK)
        GW-->>App: CharCreateResponse
        App->>GW: listCharacterSummaries(...)
        
        User->>App: Delete Character
        App->>GW: deleteCharacter(...)
        GW->>Server: MessageFrame (CHAR_DELETE)
        Server-->>GW: MessageFrame (CHAR_DELETE_OK)
        GW-->>App: CharDeleteResponse
        App->>GW: listCharacterSummaries(...)
    end

    User->>App: Select Character
    App->>GW: selectCharacter(host, port, authToken, charId)
    GW->>Server: MessageFrame (CHAR_SELECT)
    Server-->>GW: MessageFrame (CHAR_SELECT_OK)
    GW-->>App: CharSelectResponse

    Note over App: Transition to CharacterSelectedState
    App->>GW: listCharacterSummaries(...)
    
    User->>App: Play Game
    App->>GW: play(host, port, authToken, charId)
    GW->>Server: MessageFrame (PLAY)
    Server-->>GW: MessageFrame (PLAY_OK)
    GW-->>App: PlayResponse

    Note over App: Transition to InGameState
    App->>KeepAlive: start(host, port, sessionId, interval)
    
    loop Every Keepalive Interval
        KeepAlive->>GW: ping(host, port, sessionId)
        GW->>Server: MessageFrame (PING)
        Server-->>GW: MessageFrame (PING_OK)
        GW-->>KeepAlive: PingResponse
    end

    User->>App: Log Out / Character Select
    App->>KeepAlive: stop()
    App->>GW: logout(host, port, sessionId)
    GW->>Server: MessageFrame (LOGOUT)
    Server-->>GW: MessageFrame (LOGOUT_OK)
    GW-->>App: LogoutResponse
    Note over App: Transition to UnauthenticatedState or CharacterSelectedState
```

---

## 2. E2E Validation Harness Network Sequence

The `E2EValidationHarness` is a linear, single-threaded script running sequentially from step 1 to step 8.

```mermaid
sequenceDiagram
    autonumber
    participant Harness as E2EValidationHarness (Main Thread)
    participant GW as QuicGatewayService
    participant Server as Catalyst Server (QUIC)

    Harness->>GW: login(host, port, "dev", "dev")
    GW->>Server: MessageFrame (LOGIN)
    Server-->>GW: MessageFrame (LOGIN_OK)
    GW-->>Harness: LoginResponse

    Harness->>GW: createCharacter(...)
    GW->>Server: MessageFrame (CHAR_CREATE)
    Server-->>GW: MessageFrame (CHAR_CREATE_OK)
    GW-->>Harness: CharCreateResponse

    Harness->>GW: listCharacterSummaries(...)
    GW->>Server: MessageFrame (CHAR_LIST)
    Server-->>GW: MessageFrame (CHAR_LIST_OK)
    GW-->>Harness: List<CharacterSummary>

    Harness->>GW: selectCharacter(...)
    GW->>Server: MessageFrame (CHAR_SELECT)
    Server-->>GW: MessageFrame (CHAR_SELECT_OK)
    GW-->>Harness: CharSelectResponse

    Harness->>GW: play(...)
    GW->>Server: MessageFrame (PLAY)
    Server-->>GW: MessageFrame (PLAY_OK)
    GW-->>Harness: PlayResponse

    Harness->>GW: ping(...) [Once]
    GW->>Server: MessageFrame (PING)
    Server-->>GW: MessageFrame (PING_OK)
    GW-->>Harness: PingResponse

    Harness->>GW: logout(...)
    GW->>Server: MessageFrame (LOGOUT)
    Server-->>GW: MessageFrame (LOGOUT_OK)
    GW-->>Harness: LogoutResponse

    Harness->>GW: deleteCharacter(...) [Cleanup]
    GW->>Server: MessageFrame (CHAR_DELETE)
    Server-->>GW: MessageFrame (CHAR_DELETE_OK)
    GW-->>Harness: CharDeleteResponse
```

---

## 3. Comparison and Divergences

An analysis of the two sequences shows they share the same underlying protocol sequence, but differ in execution dynamics and lifecycle context.

### Method Call Commonalities
Both sequences execute the exact same core operations in the same logical order for a standard walkthrough session:
1.  `login(...)`
2.  `createCharacter(...)`
3.  `listCharacterSummaries(...)`
4.  `selectCharacter(...)`
5.  `play(...)`
6.  `ping(...)`
7.  `logout(...)`
8.  `deleteCharacter(...)`

### Divergences and Execution Differences

| Dimension | ClientApplication | E2EValidationHarness |
| :--- | :--- | :--- |
| **Execution Flow** | State-driven & user event-based. | Strictly linear, sequential script. |
| **Threading Model** | Main UI Thread handles transitions. Pings are delegated to virtual background threads in `KeepAliveService`. | Monolithic execution on the main application thread. |
| **Ping Frequency** | Periodic background loop driven by `keepaliveIntervalMs`. | One-shot request validating message roundtrip. |
| **Cleanup Sequencing** | Character deletion is only possible *before* launching game sessions (i.e. from the lobby state). | Character deletion occurs *after* `logout` as a teardown cleanup step. |
| **Exception Handling** | Logs failures to an in-app `DebugLogPanel` and maintains state stability without crashing. | Throws `AssertionError` and aborts execution (terminating the JVM with code 1). |
