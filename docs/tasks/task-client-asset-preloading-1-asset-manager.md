# Task: Client Asset Preloading — AssetManager Core

**Priority:** Low (pre-requisite for 3D rendering work)  
**Area:** Client / Engine / Rendering  
**Effort:** Small (1-2 days)  
**Parent Task:** task-client-asset-preloading.md

## Purpose

Create the `AssetManager` singleton that tracks in-flight texture load requests, caches results, and exposes a clean API to the rest of the client without blocking the render thread.

## What Needs to Happen

- Create `AssetManager` singleton class with:
  - `requestTexture(String name, String path)` — submits a load to `TaskSchedulerService`; no-ops if already in-flight or cached.
  - `getTexture(String name)` — returns the cached OpenGL handle, or `null` (placeholder) while loading.
  - Internal `Map<String, Integer>` for cached handles.
  - Internal `Set<String>` for in-flight tracking to prevent duplicate disk reads.
- Wire `TaskSchedulerService` as the background executor.
- Load raw bytes from disk/classpath on a virtual thread; return via `runOnMainThread(...)` callback.
- On the GL thread callback: call `glTexImage2D` (or equivalent), store the handle in the cache, remove from in-flight set.

## Acceptance Criteria

- The same texture requested twice issues only one disk read; the second call returns the cached handle.
- Requesting a texture returns `null` before it is ready, and the real handle once upload completes.
- No OpenGL calls are made off the GL thread.
