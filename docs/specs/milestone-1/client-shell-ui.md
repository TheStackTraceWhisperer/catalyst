# Spec: Client Shell UI (LWJGL + Dear ImGui)

## Purpose

Define the desktop client shell for Milestone 1, implemented with LWJGL 3 and Dear ImGui.

## Windows

### Control Window (left panel)

Contains all interactive controls. Content changes based on the current phase.

#### Phase: Unauthenticated
- Mode selector: Local / Remote radio buttons
- Host and port input fields (locked once authenticated in Remote mode)
- **Remote mode:** username/password inputs + Login button
- **Local mode:** "Enter Local Zone" button

#### Phase: Authenticated (auth token held, no game session)
- Character list (name, race name, size, face, job, nation per row)
- Select and Delete (soft delete) buttons per character
- "Create Character" toggle button — reveals the create form:
  - Name input
  - Race combo (Hume Male/Female, Elvaan Male/Female, Tarutaru Male/Female, Mithra, Galka)
  - Size combo (Small/Medium/Large) — auto-locked for Tarutaru (Small) and Galka (Large)
  - Face numeric (1-8) + Variant B checkbox (maps to face 0-15)
  - Starting Job combo (Warrior, Monk, White Mage, Black Mage, Red Mage, Thief)
  - Nation combo (San d'Oria, Bastok, Windurst)
  - Create and Cancel buttons
- Refresh Characters and Sign Out buttons
- After selecting a character: Play button + "Ready: \<name\>" label

#### Phase: In Game (game session active)
- "In game as: \<name\>" label
- Ping Now button (manual keepalive trigger)
- Logout Session button

#### Status Bar (always visible at bottom of control window)
- Mode, Status, Account, Auth Token (set/none), Selected Character, Session ID
- KeepAlive status, Last RTT (ms), Last OK timestamp

### Debug Log Viewer (right panel)

- Timestamped log lines (`[HH:mm:ss] message`)
- Auto-scroll toggle
- Clear button
- Captures: connection events, auth outcomes, character operations, session transitions, PING/PONG with RTT, errors

## Phase Locking Rules

| Action | Allowed when |
|---|---|
| Edit host/port | Only before authentication in Remote mode |
| Login | Only in unauthenticated Remote mode |
| Create/Delete character | Only in authenticated phase, not during active session |
| Select character | Only in authenticated phase, not during active session |
| Play | Only after character is selected, not during active session |
| Logout | Only during active game session |

## Sensitive Field Rules

- Passwords are never written to the debug log
- Auth tokens are shown as `<set>` / `<none>` only, never as the raw UUID
- Session IDs are shown in the status bar (debug tool context)

## Local-Only Mode

- Login/auth controls are hidden; replaced by "Enter Local Zone"
- Entering local zone sets a synthetic `LOCAL-<timestamp>` session string
- Keepalive is disabled in local mode
- Status bar shows local mode indicator

## Milestone 1 Done Criteria

- [x] LWJGL window initialises and renders ImGui at 60fps
- [x] Login flow executes fully from the UI
- [x] Character create form visible only on demand; hidden after successful create
- [x] UI is phase-locked — correct controls visible per phase
- [x] Host/port fields disabled once authenticated
- [x] Character management controls hidden during active game session
- [x] Debug log viewer captures all relevant session/auth events
- [x] Runtime mode switching works and gracefully disconnects if session is active
