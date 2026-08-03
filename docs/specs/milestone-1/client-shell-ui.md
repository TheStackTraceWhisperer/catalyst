# Spec: Client Shell UI (LWJGL + Dear ImGui)

## Purpose

Define the minimum desktop client shell needed to exercise Milestone 1 networking and session flows.

## Functional Requirements

- Initialize LWJGL window and render loop.
- Initialize Dear ImGui context and frame integration.
- Provide runtime mode selection:
  - server-connected mode
  - local-only mode
- Provide a login window with:
  - username field
  - password field
  - connect/login action
  - clear auth/session status display
- Provide a debug log viewer window with:
  - timestamped log lines
  - severity level indicator
  - auto-scroll toggle
  - clear log action

## Logging Scope (Minimum)

- Connection lifecycle events
- Authentication request/response outcomes
- Session state transitions
- Timeout/disconnect cleanup events
- Protocol/status errors

## Rules

- UI must remain responsive while network operations are in progress.
- Sensitive fields (passwords, tokens, session keys) must never be displayed in plaintext logs.
- The log viewer is a development/debug tool and should be easy to disable or gate in production builds.
- In local-only mode, login/auth controls may be disabled or bypassed, and UI should clearly indicate offline state.

## Milestone 1 Done Criteria

- Client starts and renders ImGui-based login + log viewer.
- Login flow can be executed fully from the UI.
- Runtime mode switching/selection is available for development workflows.
- Log viewer captures enough detail to diagnose connection/auth/session issues without external tooling.
