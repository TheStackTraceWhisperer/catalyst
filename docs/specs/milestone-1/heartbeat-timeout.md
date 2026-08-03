# Spec: Heartbeat and Timeout Handling

## Purpose

Keep session state healthy and reclaim dead connections deterministically.

## Functional Requirements

- Support heartbeat ping/pong over active QUIC connection.
- Track `lastSeenAt` per session.
- Enforce idle timeout and transition timed-out sessions to closed/cleaned state.

## Baseline Timing (Configurable)

- Heartbeat interval: `N` seconds (config)
- Timeout threshold: `M` seconds without valid activity (config)
- Grace policy: optional short grace window for transient loss

## Rules

- Any valid client activity updates `lastSeenAt`.
- Missed heartbeat/timeouts trigger:
  1. session state transition
  2. zone detach/cleanup
  3. active session-record invalidation/removal
  4. transport close
- Timeouts are server-authoritative.

## Milestone 1 Done Criteria

- Idle sessions are cleaned automatically.
- Cleanup path matches explicit disconnect cleanup behavior.
