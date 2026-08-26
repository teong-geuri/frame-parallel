package frameparallel;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import frameparallel.async.RenderWorkerPool;
import frameparallel.renderers.*;

public class FrameParallelMod extends Mod {

    private RenderWorkerPool workerPool;

    // Phase 1 Core (Verified Core Handlers)
    private ParticleRendererController particleController;
    private AsyncMinimapHandler minimapHandler;

    // Phase 2 Non-Chunk Handlers (Graph Topology & Pre-search Caching)
    private AsyncPowerGraphHandler powerGraphHandler;
    private AsyncTargetSearchHandler targetSearchHandler;

    // Phase 3 Spatial Chunk & Region Handlers
    private AsyncFloorRenderer floorRenderer;
    private AsyncFogHandler fogHandler;

    // Phase 4 Range Slicing Handlers
    private AsyncTrailRenderer trailRenderer;

    public FrameParallelMod() {
        Log.info("[FrameParallel] Initializing Mindustry Frame Parallelization Mod...");

        Events.on(ClientLoadEvent.class, e -> {
            workerPool = new RenderWorkerPool();

            // Phase 1 Core
            particleController = new ParticleRendererController(workerPool);
            minimapHandler = new AsyncMinimapHandler(workerPool);

            // Phase 2
            powerGraphHandler = new AsyncPowerGraphHandler(workerPool);
            targetSearchHandler = new AsyncTargetSearchHandler(workerPool);

            // Phase 3
            floorRenderer = new AsyncFloorRenderer(workerPool);
            fogHandler = new AsyncFogHandler(workerPool);

            // Phase 4
            trailRenderer = new AsyncTrailRenderer(workerPool);

            // Game Update Hooks
            Events.run(Trigger.update, () -> {
                if (particleController != null) particleController.update();
                if (minimapHandler != null) minimapHandler.tick();
                if (powerGraphHandler != null) powerGraphHandler.update();
                if (targetSearchHandler != null) targetSearchHandler.update();
                if (floorRenderer != null) floorRenderer.update();
                if (fogHandler != null) fogHandler.update();
                if (trailRenderer != null) trailRenderer.update();
            });

            // Draw Hooks
            Events.run(Trigger.draw, () -> {
                if (particleController != null) particleController.render();
            });

            Log.info("[FrameParallel] Frame Parallelization Mod successfully initialized and hooked all 7 multi-core modules.");
        });
    }

    @Override
    public void loadContent() {
        Log.info("[FrameParallel] Content loading initialized.");
    }
}