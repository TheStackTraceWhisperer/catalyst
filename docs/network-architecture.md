# Network Architecture

## Overview

This document describes the complete network architecture for the Catalyst Java project, covering the production server topology, client connection lifecycle, message routing, internal trust model, and phase transitions. PlantUML diagrams are included for each major concept.

This project uses a client-facing gateway that routes all traffic to internal backend services. QUIC is used for **all** transport — external and internal.


> [!NOTE]
> For the design specification, DTO-agnostic framing, and stateful gateway routing decisions, see [ADR 0001: Stateful Gateway Routing & Apache Fory Serialization](file:///home/samuel/projects/ffxi-java/docs/adr/0001-network-architecture-and-gateway-routing.md).

---

## 1. Production Topology

All communication — client-facing and internal — uses QUIC over UDP. External traffic terminates at the gateway; internal services are unreachable from the internet.

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
  component [Login Service\n(QUIC internal)] as LOGIN
  component [Lobby Service\n(QUIC internal)] as LOBBY
  database [PostgreSQL\n(accounts, characters)] as DB
  rectangle "World Cluster" {
    component [World Server A\n(zones 1-100)] as WA
    component [World Server B\n(zones 101-200)] as WB
    database [World State\n(sessions, entities,\nzone populations)] as WS
  }
}

Client -right-> GW : QUIC / TLS 1.3\nUDP :35555
GW -right-> LOGIN : QUIC / mTLS\n(internal)
GW -right-> LOBBY : QUIC / mTLS\n(internal)
GW -down-> WA : QUIC / mTLS\n(internal)
GW -down-> WB : QUIC / mTLS\n(internal)
LOGIN -down-> DB
LOBBY -down-> DB
WA -right-> WS
WB -right-> WS
@enduml
```

**Key properties:**
- The client has **one address** for its entire session lifetime.
- All backend services are unreachable from the internet — the gateway is the sole public endpoint.
- Internal communication uses the same `MessageFrame`/`WireCodec` stack as the client transport; no second protocol stack.
- The gateway maintains a routing table mapping `sessionId → world server` after `PLAY`.
- World servers can be added or restarted without the client noticing.

---

## 2. Internal Trust Model

### Trust Design
The trust model assumes login and world servers operate behind a gateway. The gateway routes external clients to backend services, avoiding exposing internal services to potential impersonation.

### Why the gateway eliminates this problem

The gateway is the **only** service with a public address. Network-level enforcement (firewall rules, k8s NetworkPolicy) ensures:

1. Internet → gateway only
2. Gateway → backend services only (private CIDR)
3. Backend services cannot be reached from the internet regardless of what code they run

External spoofing of internal sources becomes physically impossible — there is no path from the internet to Login, Lobby, or World. The classic server registry and per-packet source validation are not needed.

### What mTLS adds on the private network

Even inside the private network, mutual TLS (mTLS) ensures that a compromised service cannot impersonate another. The threat model shifts from "external actor spoofing internal source" to "lateral movement from a compromised pod":

```plantuml
@startuml trust
!theme plain
skinparam defaultFontSize 13
skinparam linetype ortho

rectangle "Certificate Authority\n(internal, cert-manager or static)" as CA

rectangle "Gateway" as GW #LightBlue
rectangle "Login Service" as LS #LightGreen
rectangle "Lobby Service" as LBS #LightGreen
rectangle "World Server" as WS #LightYellow
rectangle "PostgreSQL" as DB #LightCoral

CA -down-> GW : issues cert\n(trusted by Login, Lobby, World)
CA -down-> LS : issues cert\n(trusted by Gateway, DB only)
CA -down-> LBS : issues cert\n(trusted by Gateway, DB, World Registry)
CA -down-> WS : issues cert\n(trusted by Gateway, World State DB only)

GW -right-> LS : mTLS (GW cert accepted)
GW -right-> LBS : mTLS (GW cert accepted)
GW -down-> WS : mTLS (GW cert accepted)
LS -down-> DB : mTLS (accounts table)
LBS -down-> DB : mTLS (characters table)
WS -down-> DB : mTLS (world state only)
@enduml
```

### Least-privilege access matrix

| Service | Can reach | Cannot reach |
|---|---|---|
| Gateway | Login, Lobby, World (any) | PostgreSQL directly |
| Login | PostgreSQL (accounts) | Lobby, World, World State DB |
| Lobby | PostgreSQL (characters), World Registry | Login directly, World State DB |
| World | World State DB | Login, Lobby, accounts/characters DB |

### Auth token validation: once at the gateway

Auth tokens are validated **once** at the gateway before forwarding. Backend services trust that any inbound QUIC connection arrived from the gateway (enforced by network position + mTLS cert), and do not re-validate tokens. This eliminates the classic Catalyst pattern of each server maintaining its own session verification chain.

### Production cert management options

| Environment | Approach |
|---|---|
| Dev / single-node | Self-signed cert generated at startup (current) |
| Docker Compose / bare metal | Static internal CA, certs baked into service containers at deploy |
| Kubernetes | cert-manager with a `ClusterIssuer`; automatic rotation, no manual cert lifecycle |

---

## 3. Current State (Milestone 1)

The milestone 1 single-server deployment collapses everything into one process. The gateway split is additive — the internal architecture is already logically separated.

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

Client --> LH : QUIC — LOGIN
Client --> CH : QUIC — CHAR_LIST\nCHAR_CREATE\nCHAR_SELECT\nCHAR_DELETE
Client --> SH : QUIC — PLAY\nPING\nLOGOUT
LH --> DB
CH --> DB
SH --> DB
SH --> ZT
@enduml
```

---

## 4. Target Multi-Server Topology

```plantuml
@startuml multiserver
!theme plain
skinparam defaultFontSize 13
skinparam linetype polyline

actor Client

rectangle "gateway" as GW {
  component [QUIC Listener\n(external)] as QL
  component [Auth Token Validator] as ATV
  component [Phase Router] as PR
  component [Session Route Table\nsessionId → WorldServer] as RT
}

rectangle "login" as LS {
  component [Login Handler] as LH
}

rectangle "lobby" as LBS {
  component [Character Handler] as CH
  component [World Selector] as WSel
}

rectangle "world (A)" as WA {
  component [Session Handler] as SHA
  component [Zone Manager] as ZMA
  component [Entity Tracker] as ETA
}

rectangle "world (B)" as WB {
  component [Session Handler] as SHB
  component [Zone Manager] as ZMB
  component [Entity Tracker] as ETB
}

database "PostgreSQL\n(accounts, characters)" as DB
database "World State\n(sessions, zones)" as WST

Client --> QL : QUIC / TLS 1.3
QL --> ATV
ATV --> PR
PR --> LH : QUIC / mTLS — LOGIN
PR --> CH : QUIC / mTLS — CHAR_*
CH --> WSel : on PLAY
WSel --> RT : assign world
PR --> SHA : QUIC / mTLS — PING / LOGOUT\n(world A sessions)
PR --> SHB : QUIC / mTLS — PING / LOGOUT\n(world B sessions)
LH --> DB
CH --> DB
SHA --> WST
SHB --> WST
@enduml
```

---

## 5. Client Connection Lifecycle

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
C -> GW : QUIC connect (UDP) + TLS 1.3
GW --> C : TLS handshake complete
C -> GW : stream(0): LOGIN {username, password}
GW -> LS : QUIC/mTLS — LOGIN (authToken not yet issued)
LS -> LS : Argon2id verify
LS --> GW : LOGIN_OK {authToken, accountId}
GW --> C : LOGIN_OK {authToken, accountId}

== Character Selection Phase ==
C -> GW : stream(1): CHAR_LIST {authToken}
GW -> GW : validate authToken
GW -> LBS : QUIC/mTLS — CHAR_LIST
LBS --> GW : CHAR_LIST_OK {characters}
GW --> C : CHAR_LIST_OK

C -> GW : stream(2): CHAR_SELECT {authToken, characterId}
GW -> LBS : QUIC/mTLS — CHAR_SELECT
LBS --> GW : CHAR_SELECT_OK {identity, position}
GW --> C : CHAR_SELECT_OK

== Play Phase ==
C -> GW : stream(3): PLAY {authToken, characterId}
GW -> LBS : QUIC/mTLS — PLAY
LBS -> WS : QUIC/mTLS — assign_session
WS --> LBS : session created
LBS --> GW : PLAY_OK {sessionId, zoneId, position}
GW -> GW : store sessionId → WorldServer(A)
GW --> C : PLAY_OK {sessionId, zoneId}

== Active Session ==
loop every 5s
  C -> GW : stream(N): PING {sessionId}
  GW -> GW : lookup sessionId → WorldServer(A)
  GW -> WS : QUIC/mTLS — PING
  WS --> GW : PONG
  GW --> C : PONG
end

== Logout ==
C -> GW : stream(M): LOGOUT {sessionId}
GW -> WS : QUIC/mTLS — LOGOUT
WS --> GW : BYE
GW -> GW : remove sessionId from route table
GW --> C : BYE
@enduml
```

---

## 6. Message Routing Table

The gateway validates the auth token on every pre-session request, then routes by message type. Post-`PLAY`, session messages are routed by `sessionId` lookup.

```plantuml
@startuml routing
!theme plain
skinparam defaultFontSize 13

map "Static Routes (by message type)" as SR {
  LOGIN => Login Service
  CHAR_LIST => Lobby Service
  CHAR_CREATE => Lobby Service
  CHAR_SELECT => Lobby Service
  CHAR_DELETE => Lobby Service
  PLAY => Lobby Service
}

map "Session Routes (by sessionId lookup)" as SS {
  PING => World Server
  LOGOUT => World Server
  ZONE_CHANGE => World Server
  ENTITY_UPDATE => World Server
}
@enduml
```

---

## 7. QUIC Stream Model

Each logical request/response pair uses a **dedicated bidirectional QUIC stream** on a persistent connection. The same model applies on both the external (client ↔ gateway) and internal (gateway ↔ service) connections.

```plantuml
@startuml quic_streams
!theme plain
skinparam defaultFontSize 13

rectangle "External QUIC Connection (persistent, Client ↔ Gateway)" {
  rectangle "stream 0" {
    component "LOGIN →\n← LOGIN_OK"
  }
  rectangle "stream 1" {
    component "CHAR_LIST →\n← CHAR_LIST_OK"
  }
  rectangle "stream 2..N" {
    component "CHAR_SELECT / PLAY /\nPING / LOGOUT / ..."
  }
}

rectangle "Internal QUIC Connection (persistent, Gateway ↔ World Server A)" {
  rectangle "stream 0 " {
    component "PING →\n← PONG"
  }
  rectangle "stream 1 " {
    component "LOGOUT →\n← BYE"
  }
  rectangle "stream 2..N " {
    component "ZONE_CHANGE /\nENTITY_UPDATE / ..."
  }
}
@enduml
```

**Properties:**
- One stream per request/response pair — streams are cheap, connections are valuable.
- Client opens a stream, writes request, shuts down output (half-close).
- Receiver writes response, shuts down output.
- Client completes `CompletableFuture` on newline detection in `channelRead`; stream closes.
- `QuicChannel` connection persists across all requests.

---

## 8. World Server Zone Assignment

```plantuml
@startuml zone_assign
!theme plain
skinparam defaultFontSize 13

participant "Lobby Service" as L
participant "World Registry" as WR
participant "World Server A\n(zones 1-100)" as WA
participant "World Server B\n(zones 101-200)" as WB

L -> WR : lookup(zoneId=230)
WR --> L : WorldServer A (QUIC addr)
L -> WA : QUIC/mTLS — assign_session\n(accountId, characterId, zoneId=230)
WA -> WA : joinZone(sessionId, 230)
WA --> L : PLAY_OK {sessionId}
L --> L : auth ticket consumed
@enduml
```

---

## 9. Module Breakdown (Target)

| Module | Responsibility | External Transport | Internal Transport |
|---|---|---|---|
| `gateway` | Client-facing QUIC endpoint, TLS termination, auth token validation, phase routing, session→world route table | QUIC / TLS 1.3 | QUIC / mTLS |
| `login` | Account auth (Argon2id), auth token issuance | — | QUIC / mTLS |
| `lobby` | Character CRUD, soft delete, race/job/nation validation, world server assignment on PLAY | — | QUIC / mTLS |
| `world` | Session lifecycle, zone management, entity tracking, keepalive, movement | — | QUIC / mTLS |
| `common` | Shared wire codec (`MessageFrame`, `WireCodec`), domain models | — | — |
| `client` | LWJGL + Dear ImGui desktop client, `QuicGateway` transport | QUIC / TLS 1.3 | — |

---

## 10. Current vs Target

| Concern | Milestone 1 (now) | Target |
|---|---|---|
| Client endpoint | Single `ServerMain` on UDP :35555 | `gateway` on UDP :35555 |
| Internal comms | N/A (single process) | QUIC / mTLS between gateway and services |
| Auth | In `ServerMain` | `login` service |
| Auth token validation | In `ServerMain` | Once at gateway; not repeated by backend |
| Character ops | In `ServerMain` | `lobby` service |
| Session/world | In `ServerMain` | `world` service(s) |
| Server-to-server auth | N/A | mTLS pinned certs (dev) / cert-manager (k8s) |
| World scale | Single in-memory map | Multiple `world` instances + world registry |
| DB | Single PostgreSQL | Accounts/chars DB + world state DB |
