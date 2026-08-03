# Spec: Wire Protocol v2

## Purpose

Improve `WireCodec` and `MessageFrame` to be typed, versioned, and builder-friendly — without changing the wire format or breaking existing message types.

## Current Limitations

- `MessageFrame.get(key)` returns `String` or `null`; callers do ad-hoc `parseInt`/`parseLong` everywhere
- No protocol version field — impossible to detect client/server version mismatch
- No builder API — frames are constructed as raw `Map<String, String>`
- No validation of required fields at construction time

## Wire Format (Unchanged)

The pipe-delimited text format remains identical:
```
TYPE|key=value|key=value|...\n
```

All values remain URL-encoded strings on the wire. The improvements are purely in the Java API layer.

## Changes

### `MessageFrame` — Typed Accessors

```java
public record MessageFrame(String type, Map<String, String> fields) {
    // existing
    public String get(String key)                              { ... }

    // new typed accessors with fallback
    public int     getInt(String key, int fallback)           { ... }
    public long    getLong(String key, long fallback)         { ... }
    public float   getFloat(String key, float fallback)       { ... }
    public boolean getBoolean(String key, boolean fallback)   { ... }

    // protocol version
    public static final String VERSION_KEY = "_v";
    public static final int    CURRENT_VERSION = 2;
    public int protocolVersion()  { return getInt(VERSION_KEY, 1); }
}
```

### `MessageFrame.Builder`

```java
MessageFrame frame = MessageFrame.builder("PLAY_OK")
    .put("sessionId", sessionId)
    .put("zoneId", identity.currentZoneId())
    .put("playersInZone", playersInZone)
    .put("characterName", identity.name())
    .build();  // automatically adds _v=2
```

`put(String key, int value)`, `put(String key, long value)`, `put(String key, float value)`, `put(String key, boolean value)` — all convert to string internally for wire encoding.

### `WireCodec` — Version Injection

`WireCodec.encode()` automatically injects `_v=2` into every outgoing frame. Incoming frames without `_v` are treated as version 1 (backward compatible).

## Version Negotiation

In M2, version is informational and logged. No hard enforcement — a v1 client connecting to a v2 server still works. Version mismatch logging:

```
PROTOCOL_VERSION_MISMATCH client_v=1 server_v=2 type=LOGIN
```

Hard enforcement is deferred to a future milestone when breaking protocol changes are made.

## No Fory / Binary Serialization

Binary serialization (Apache Fory) is deferred to the world server milestone where high-frequency game-state traffic warrants it. The pipe-delimited format is sufficient for login/character/session protocol and remains debuggable.

## Milestone 2 Done Criteria

- [ ] `MessageFrame` has typed accessors (`getInt`, `getLong`, `getFloat`, `getBoolean`)
- [ ] `MessageFrame.Builder` exists and is used in all server handler response construction
- [ ] `WireCodec.encode()` injects `_v=2` into all outgoing frames
- [ ] All `parseInt`/`parseLong` boilerplate removed from handler/state classes
- [ ] Version mismatch is logged (not enforced) on inbound frames
