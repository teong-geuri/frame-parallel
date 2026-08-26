# Frame Parallelization Mod

Mindustry `v159.7` client-side render helper. Version is managed in `gradle.properties`.

## Current scope

- Minimap tile-color calculation runs on worker threads.
- Pixmap and OpenGL texture upload remain on the render thread.
- World and gameplay state are not changed.
- `hidden: true` keeps the mod client-side and multiplayer-compatible. Do not add blocks, units, rules, network packets, or world simulation here.

## Building

Requires JDK 17 or newer.

```text
gradlew.bat jar
```

Output: `build/libs/frame-parallelDesktop.jar`.

Build version:

```properties
modVersion=1.1.0
mindustryVersion=v159.7
```

The build injects `modVersion` into the packaged `mod.hjson`. The checked-in value in `mod.hjson` is a readable fallback.

## Building compatibility

Building rendering cannot be moved wholesale to worker threads from a normal mod. Mindustry's `BlockRenderer` performs visibility selection, cache rebuilds, `Draw`, `SpriteCache`, and OpenGL work in the render thread. `Building.draw()` also writes directly to the active graphics batch.

The mod therefore does not fake building parallelization. A safe building implementation would require a Mindustry core fork: parallel CPU-only render-command preparation, followed by main-thread draw and GPU upload.

## Multiplayer

This mod is render-only and hidden from the multiplayer mod list. Install it client-side. If future changes add content or simulation state, remove `hidden: true` and implement proper multiplayer mod compatibility instead.
