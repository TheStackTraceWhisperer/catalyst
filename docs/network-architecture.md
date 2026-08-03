# Network Architecture

## Overview

This document describes the complete network architecture for the FFXI Java project, covering the production server topology, client connection lifecycle, message routing, and phase transitions. PlantUML diagrams are included for each major concept.

This architecture is a deliberate divergence from LandSandBoat (see D-007 in `lsb-divergence.md`). LSB uses direct client-to-zone-server connections after login; this project uses a client-facing gateway that routes all traffic to internal backend services.

---

## 1. Production Topology

```plantuml
@startuml topology
!theme plain
skinparam linetype ortho
skinparam defaultFontSize 13

cloud "Internet" {
  actor Client
}

rectangle "DMZ" {
  component [Gateway Server\n(QUIC UDP :35555)] as GW
}

rectangle "Private Network" {
  component [Login Service\n(internal)] as LOGIN
  component [Lobby Service\n(internal)] as LOBBY
  database [PostgreSQL\n(accounts, characters)] as DB
  rectangle "World Cluster" {
    component [World Server A\n(zones 1-100)] as WA
    component [World Server B\n(zones 101-200)] as WB
    database [World State\n(sessions, entities,\nzone populations)] as WS
  }
}

Client -right-> GW : QUIC / TLS 1.3\nUDP :35555
GW -right-> LOGIN : gRPC (internal)
GW -right-> LOBBY : gRPC (internal)
GW -down-> WA : gRPC (internal)
GW -down-> WB : gRPC (internal)
LOGIN -down-> DB
LOBBY -down-> DB
WA -right-> WS
WB -right-> WS
@enduml
```

**Key properties:**
- The client has **one address** for its entire session lifetime.
- All backend services are unreachable from the internet.
- The gateway maintains a routing table mapping `sessionId → world server` after `PLAY`.
- World servers can be added or restarted without the client noticing.

---

## 2. Current State (Milestone 1)

The milestone 1 single-server deployment collapses everything into one process for simplicity. The internal architecture is already split logically; the gateway split is additive.

```plantuml
@startuml milestone1
!theme plain
skinparam defaultFontSize 13

actor Client

rectangle "Single Process (ServerMain)" {
  component [Login Handler] as LH
  component [Lobby / Character Handler] as CH
  component [Session / World Handler] as SH
  component [Zone Population Tracker\n(in-memory)] as ZT
  database [PostgreSQL] as DB
}

Client --> LH : LOGIN
Client --> CH : CHAR_LIST\nCHAR_CREATE\nCHAR_SELECT\nCHAR_DELETE
Client --> SH : PLAY\nPING\nLOGOUT
LH --> DB
CH --> DB
SH --> DB
SH --> ZT
@enduml
```

---

## 3. Target Multi-Server Topology

```plantuml
@startuml multiserver
!theme plain
skinparam defaultFontSize 13
skinparam linetype polyline

actor Client

rectangle "ffxi-gateway" as GW {
  component [QUIC Listener] as QL
  component [Auth Token Validator] as ATV
  component [Phase Router] as PR
  component [Session Route Table\nsessionId → WorldServer] as RT
}

rectangle "ffxi-login" as LS {
  component [Login Handler] as LH
}

rectangle "ffxi-lobby" as LBS {
  component [Character Handler] as CH
  component [World Selector] as WS
}

rectangle "ffxi-world (A)" as WA {
  component [Session Handler] as SHA
  component [Zone Manager] as ZMA
  component [Entity Tracker] as ETA
}

rectangle "ffxi-world (B)" as WB {
  component [Session Handler] as SHB
  component [Zone Manager] as ZMB
  component [Entity Tracker] as ETB
}

database "PostgreSQL\n(accounts, characters)" as DB
database "World State\n(sessions, zones)" as WST

Client --> QL : QUIC
QL --> ATV
ATV --> PR
PR --> LH : LOGIN
PR --> CH : CHAR_*
CH --> WS : on PLAY
WS --> RT : assign world
PR --> SHA : PING / LOGOUT\n(world A sessions)
PR --> SHB : PING / LOGOUT\n(world B sessions)
LH --> DB
CH --> DB
SHA --> WST
SHB --> WST
@enduml
```

---

## 4. Client Connection Lifecycle

```plantuml
@startuml lifecycle
!theme plain
skinparam defaultFontSize 13

participant "Client\n(QuicGateway)" as C
participant "Gateway" as GW
participant "Login Service" as LS
participant "Lobby Service" as LBS
participant "World Server" as WS

== Authentication Phase ==
C -> GW : QUIC connect (UDP)
GW --> C : TLS handshake complete
C -> GW : LOGIN {username, password}
GW -> LS : forward LOGIN
LS -> LS : Argon2id verify
LS --> GW : LOGIN_OK {authToken, accountId}
GW --> C : LOGIN_OK {authToken, accountId}

== Character Selection Phase ==
C -> GW : CHAR_LIST {authToken}
GW -> LBS : forward CHAR_LIST
LBS --> GW : CHAR_LIST_OK {characters}
GW --> C : CHAR_LIST_OK

C -> GW : CHAR_SELECT {authToken, characterId}
GW -> LBS : forward CHAR_SELECT
LBS --> GW : CHAR_SELECT_OK {identity, position}
GW --> C : CHAR_SELECT_OK

== Play Phase ==
C -> GW : PLAY {authToken, characterId}
GW -> LBS : forward PLAY
LBS -> WS : assign session
WS --> LBS : session created
LBS --> GW : PLAY_OK {sessionId, zoneId, position}
GW -> GW : store sessionId → WorldServer(A)
GW --> C : PLAY_OK {sessionId, zoneId}

== Active Session ==
loop every 5s
  C -> GW : PING {sessionId}
  GW -> WS : forward PING (via route table)
  WS --> GW : PONG
  GW --> C : PONG
end

== Logout ==
C -> GW : LOGOUT {sessionId}
GW -> WS : forward LOGOUT
WS --> GW : BYE
GW -> GW : remove sessionId from route table
GW --> C : BYE
@enduml
```

---

## 5. Message Routing Table

The gateway holds an in-memory routing table updated at `PLAY` and cleared at `LOGOUT` or session timeout.

```plantuml
@startuml routing
!theme plain
skinparam defaultFontSize 13
skinparam linetype ortho

rectangle "Gateway Route Table" {
  map "phaseRouter" {
    LOGIN => Login Service
    CHAR_LIST => Lobby Service
    CHAR_CREATE => Lobby Service
    CHAR_SELECT => Lobby Service
    CHAR_DELETE => Lobby Service
    PLAY => Lobby Service
    PING => World Server (lookup by sessionId)
    LOGOUT => World Server (lookup by sessionId)
    ZONE_CHANGE => World Server (lookup by sessionId)
  }
}
@enduml
```

---

## 6. QUIC Stream Model

Each logical request/response pair uses a **dedicated bidirectional QUIC stream** on a persistent connection. The connection is maintained for the full session; streams are cheap to open and close.

```plantuml
@startuml quic_streams
!theme plain
skinparam defaultFontSize 13

rectangle "QUIC Connection (persistent, Client ↔ Gateway)" {
  rectangle "stream 0" {
    component "LOGIN →\n← LOGIN_OK"
  }
  rectangle "stream 1" {
    component "CHAR_LIST →\n← CHAR_LIST_OK"
  }
  rectangle "stream 2" {
    component "CHAR_SELECT →\n← CHAR_SELECT_OK"
  }
  rectangle "stream 3" {
    component "PLAY →\n← PLAY_OK"
  }
  rectangle "stream 4..N" {
    component "PING →\n← PONG\n(repeated)"
  }
}
@enduml
```

**Properties:**
- Client (`QuicGateway`) opens one stream per request, writes the request, shuts down output.
- Server reads the request, writes the response, shuts down output.
- Client reads the response on `channelRead` newline detection, completes the `CompletableFuture`.
- Stream fully closes; `QuicChannel` connection persists.

---

## 7. World Server Zone Assignment

```plantuml
@startuml zone_assign
!theme plain
skinparam defaultFontSize 13

participant "Lobby Service" as L
participant "World Registry" as WR
participant "World Server A\n(zones 1-100)" as WA
participant "World Server B\n(zones 101-200)" as WB

L -> WR : lookup(zoneId=230)
WR --> L : WorldServer A
L -> WA : assign_session(accountId, characterId, zoneId=230)
WA -> WA : joinZone(sessionId, 230)
WA --> L : PLAY_OK {sessionId}
L --> L : auth ticket consumed
@enduml
```

---

## 8. Module Breakdown (Target)

| Module | Responsibility | Transport |
|---|---|---|
| `ffxi-gateway` | Client-facing QUIC endpoint, TLS, auth token validation, phase routing, session→world route table | QUIC (external), gRPC (internal) |
| `ffxi-login` | Account auth (Argon2id), auth token issuance | gRPC |
| `ffxi-lobby` | Character CRUD, soft delete, race/job/nation validation, world server assignment on PLAY | gRPC |
| `ffxi-world` | Session lifecycle, zone management, entity tracking, keepalive, movement | gRPC |
| `ffxi-common` | Shared wire codec (`MessageFrame`, `WireCodec`), domain models (`CharacterIdentity`, `RuntimeMode`) | — |
| `ffxi-client` | LWJGL + Dear ImGui desktop client, QUIC transport (`QuicGateway`), all UI phases | QUIC |

---

## 9. Current vs Target

| Concern | Milestone 1 (now) | Target |
|---|---|---|
| Client endpoint | Single `ServerMain` on UDP :35555 | `ffxi-gateway` on UDP :35555 |
| Auth | In `ServerMain` | `ffxi-login` service |
| Character ops | In `ServerMain` | `ffxi-lobby` service |
| Session/world | In `ServerMain` | `ffxi-world` service(s) |
| Internal comms | N/A (single process) | gRPC between gateway and services |
| World scale | Single in-memory map | Multiple `ffxi-world` instances, registry |
| DB | Single PostgreSQL | Shared accounts/chars DB + world state DB |
