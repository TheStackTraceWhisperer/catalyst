# Task: Client Background Asset Preloading

**Priority:** Low (pre-requisite for 3D rendering work)  
**Area:** Client / Engine / Rendering

## Purpose

When the game moves into rendering 2D/3D assets (UI textures, zone geometry, sprite sheets),
loading these from disk on the render thread will freeze the frame loop and cause visible hitches.

The `TaskSchedulerService` virtual-thread executor already exists and is purpose-built for exactly
this problem.

## What Needs to Happen

### Asset Loading Pattern
- Asset load requests are submitted to `TaskSchedulerService` as background tasks.
- The background virtual thread reads raw bytes from disk (or the JAR classpath).
- The raw bytes are passed back to the foreground queue via `runOnMainThread(...)`.
- The render thread receives the callback and calls the OpenGL texture upload (`glTexImage2D` etc.)
  safely on the single GL context thread.

### Asset Manager
- Create an `AssetManager` singleton that:
  - Accepts a load request by asset name / path.
  - Tracks in-flight loads to prevent duplicate requests.
  - Caches loaded texture handles by name.
  - Provides a `getTexture(name)` method that returns `null` (or a placeholder) while loading and
    the real handle once uploaded.

### Scope
Initial scope covers:
- UI panel background textures (login screen, character selection).
- Character portrait / race icon sprites.
- Zone minimap images.

3D mesh and terrain loading is out of scope for this task (separate rendering milestone).

## Acceptance Criteria
- Loading a texture does not block or stutter the render loop.
- The same texture requested twice returns a cached handle without a duplicate disk read.
- A placeholder / loading indicator is shown while the asset is in-flight.
