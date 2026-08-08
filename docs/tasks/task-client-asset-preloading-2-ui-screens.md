# Task: Client Asset Preloading — UI Screen Textures

**Priority:** Low (pre-requisite for 3D rendering work)  
**Area:** Client / Engine / Rendering  
**Effort:** Small (1-2 days)  
**Parent Task:** task-client-asset-preloading.md  
**Depends On:** task-client-asset-preloading-1-asset-manager.md

## Purpose

Wire the `AssetManager` into the existing UI screens (login, character selection) to load background textures and portrait sprites asynchronously, showing a placeholder while the asset is in-flight.

## What Needs to Happen

- Integrate `AssetManager.requestTexture(...)` calls at screen initialisation time for:
  - Login screen background panel texture.
  - Character selection background panel texture.
  - Character portrait / race icon sprite sheet.
  - Zone minimap images (one per zone visible at char select).
- Render the cached handle when `getTexture(...)` returns non-null; render a solid-colour placeholder rectangle otherwise.
- Add a simple loading spinner or progress indicator overlay while any UI-critical assets are still in-flight.

## Acceptance Criteria

- Login and character-selection screens display their textures without blocking or stuttering the render loop.
- A visible placeholder is shown during the load window.
- Switching screens does not cause duplicate texture uploads for already-cached assets.
