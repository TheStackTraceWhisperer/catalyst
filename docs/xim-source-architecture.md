# Xim Source: High-Level Components and Architecture

## Overview

`xim-source` is a **Kotlin/JS WebGL2 client simulator** for Catalyst-style content.  
It runs entirely in the browser and is organized around:

1. A browser bootstrap and platform layer
2. A frame-driven engine loop
3. A data/resource pipeline for DAT parsing and table loading
4. Scene/world simulation and event-driven game logic
5. Rendering, audio, and UI subsystems

Most implementation code lives under:

- `src/jsMain/kotlin/xim/poc` (runtime, engine, scene, rendering, audio, UI, tools)
- `src/jsMain/kotlin/xim/resource` (DAT parsing and resource model)
- `src/jsMain/resources` (HTML/CSS/env config and static data tables)

---

## Runtime Startup Flow

The app boots from `src/jsMain/kotlin/main.kt`:

1. Select game mode via `DomainSpecificConfigs.apply()`:
   - `AssetViewer` (default, debug-heavy)
   - `GameV0` (`?mode=game`)
2. Create browser platform dependencies (`BrowserPlatformDependencies.get(...)`):
   - WebGL context
   - input/keyboard
   - window/screen settings
   - frame executor (`requestAnimationFrame`)
   - renderer (`GLDrawer`)
3. Set `GameState` game mode and enable debug tools when appropriate
4. Enter the main loop through `MainTool.run(...)`

The browser shell (`src/jsMain/resources/index.html`) provides the canvas and tool/config UI panels.

---

## Major Components

| Component | Responsibility | Key Files |
|---|---|---|
| **Bootstrap & Mode Selection** | Entry-point, mode selection, debug gating, platform initialization | `main.kt`, `poc/browser/DomainSpecificConfigs.kt`, `poc/browser/BrowserPlatformDependencies.kt` |
| **Platform Abstractions** | Browser/window/input abstractions and frame scheduling | `poc/browser/PlatformDependencies.kt`, `poc/browser/JsViewExecutor.kt`, `poc/browser/ExecutionEnvironment.kt` |
| **Main Orchestrator** | Coordinates per-frame update order, loading gates, subsystem sequencing | `poc/MainTool.kt` |
| **Game Simulation Core** | Event queue, actor ticks, behavior updates, combat and skill constraints | `poc/game/GameEngine.kt`, `poc/game/GameState.kt`, `poc/game/GameClient.kt` |
| **Scene/World System** | Zone loading, sub-area management, collisions, interactions, culling context, zoning lifecycle | `poc/Scene.kt`, `poc/tools/ZoneChanger.kt` |
| **Game Modes / Rulesets** | Pluggable game rules and progression logic via `GameLogic` interface | `poc/game/configuration/GameLogic.kt`, `poc/game/configuration/assetviewer/AssetViewer.kt`, `poc/game/configuration/v0/GameV0.kt` |
| **Rendering Pipeline** | WebGL shader programs, draw modes, frame buffers, 3D/2D render passes | `poc/gl/Drawer.kt`, `poc/gl/GLDrawer.kt`, `poc/ZoneDrawer.kt`, `poc/ActorDrawer.kt`, `poc/ParticleDrawer.kt` |
| **Resource Loading & Parsing** | Async DAT fetch/cache/retry; binary section parsing into typed resources | `poc/browser/DatLoader.kt`, `resource/DatParser.kt`, `resource/DatResource.kt` |
| **Static Tables & Metadata** | Loads table data and DLL-derived offsets used for file path mapping and gameplay metadata | `resource/table/FTable.kt`, `resource/table/MainDll.kt`, other `resource/table/*.kt` |
| **Audio** | BGM and sound effect lifecycle, dedupe, spatial volume/pan, per-channel volume | `poc/audio/AudioManager.kt`, `poc/audio/BgmManager.kt` |
| **UI State Machine** | Menu stack, focus/cursor handling, contextual actions, UI interaction flow | `poc/game/UiState.kt`, `poc/ui/*` |
| **Debug/Tooling Layer** | Runtime debugging, environment tuning, spawning/inspection tools | `poc/tools/*`, `poc/tools/DebugToolsManager.kt` |

---

## Frame Loop Architecture

`MainTool.internalLoop(...)` drives the engine in a strict staged order:

1. **Input and async loading updates** (keyboard/gamepad, `DatLoader.update`)
2. **Time and game speed control**
3. **Load gating**:
   - execution environment ready
   - core DAT/resources and tables loaded
   - scene fully loaded
4. **Game simulation**:
   - `GameEngine.tick(...)`
   - scene update and interaction checks
   - actor updates and camera update
5. **Environment/audio/effects updates**
6. **Render passes**:
   - sky
   - zone geometry
   - opaque actors
   - shadows / weapon traces / particles / decals / lens flare
   - translucent actors
   - debug 3D overlays
   - screen buffer resolve
7. **UI and overlays**
8. **Debug tools and telemetry hooks**

This is an explicit game-loop architecture rather than ECS; subsystems are mostly singleton managers coordinated by `MainTool`.

---

## Data and Resource Pipeline

### 1) Acquisition and Caching

`DatLoader` fetches files from `ExecutionEnvironment.serverPath` and uses:

- browser cache (`caches.open("dats")`)
- in-memory wrapper cache
- retry/backoff for failed loads

### 2) Binary Parsing

`DatParser` reads section headers and dispatches to section-specific parsers (`SectionType` switch), building a `DirectoryResource` tree of typed entries (`DatResource` subclasses).

### 3) Post-processing / Patching

Parser-time and post-parse patch managers (`DatPatchManager`, `DatPostProcessorManager`) allow resource corrections or behavior adjustments.

### 4) Table Preload Dependencies

During startup, `MainTool` preloads table resources (e.g., zone settings, spells, abilities, items) and waits for readiness before full simulation starts.

---

## Scene and World Model

`Scene` encapsulates currently loaded world state:

- Main area + optional sub-area(s)
- Optional ship/secondary area transforms
- Zone NPC population and interaction metadata
- Collision queries (terrain, doors, elevators, water/fishing checks)
- Visibility/culling context for rendering

`SceneManager` owns lifecycle: load, readiness checks, unload, and reload requests.

---

## Game Logic Composition

The architecture separates **engine mechanics** from **rules/content**:

- Engine-level mechanics live in `GameEngine`, actor/event systems, and scene handling.
- Game-specific rules implement `GameLogic`.

This allows swapping behavior between:

- **AssetViewer**: inspection/testing workflow with broad debug controls
- **GameV0**: fuller gameplay logic, zone-specific systems, interactions, progression rules

---

## Rendering Architecture

Rendering is abstraction-first:

- `Drawer` defines render operations by mode (world, skinned, particles, UI, decals, lens flare).
- `GLDrawer` implements this with dedicated shader programs and state transitions.
- Framebuffer managers support multi-pass rendering before compositing to screen.

Render responsibilities are split by domain (`ZoneDrawer`, `ActorDrawer`, `ParticleDrawer`) while `MainTool` controls pass ordering.

---

## UI and Input Architecture

Input is polled each frame (keyboard/gamepad).  
UI behavior is driven by a **state stack** (`UiStateHelper`) with context-specific handlers, menu focus rules, and action confirmation flow.

This keeps UI transitions explicit and tightly integrated with game events (`GameClient` -> `GameEngine` events).

---

## Architectural Characteristics

- **Strengths**
  - Clear subsystem boundaries with explicit orchestrator
  - Good separation of content/rules (`GameLogic`) from engine loop
  - Flexible DAT/resource parsing model with patch hooks
  - Strong runtime tooling/debug surface

- **Tradeoffs**
  - Heavy use of global singletons/managers increases shared-state coupling
  - Frame orchestration logic is centralized in one large coordinator (`MainTool`)
  - Async resource readiness is distributed across multiple manager readiness checks

