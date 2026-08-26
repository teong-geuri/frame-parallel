package frameparallel;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import frameparallel.async.RenderWorkerPool;
import frameparallel.renderers.*;

public class FrameParallelMod extends Mod {

    public static final String VERSION = "1.3.1";

    private RenderWorkerPool workerPool;

    // Phase 1 Core (Verified Core Handlers)
    private ParticleRendererController particleController;
    private AsyncMinimapHandler minimapHandler;

    // Phase 2 Non-Chunk Handlers (Graph Topology & Pre-search Caching)
    private AsyncPowerGraphHandler powerGraphHandler;
    private AsyncTargetSearchHandler targetSearchHandler;
    private AsyncBuildingSearchHandler buildingSearchHandler;

    // Phase 3 Spatial Chunk & Region Handlers
    private AsyncFloorRenderer floorRenderer;
    private AsyncFogHandler fogHandler;
    private AsyncFirePuddleHandler firePuddleHandler;

    // Phase 4 Range Slicing Handlers
    private AsyncTrailRenderer trailRenderer;

    public FrameParallelMod() {
        Log.info("[FrameParallel] Initializing Mindustry Frame Parallelization Mod v@...", VERSION);

        Events.on(ClientLoadEvent.class, e -> {
            workerPool = new RenderWorkerPool();

            // Phase 1 Core
            particleController = new ParticleRendererController(workerPool);
            minimapHandler = new AsyncMinimapHandler(workerPool);

            // Phase 2
            powerGraphHandler = new AsyncPowerGraphHandler(workerPool);
            targetSearchHandler = new AsyncTargetSearchHandler(workerPool);
            buildingSearchHandler = new AsyncBuildingSearchHandler(workerPool);

            // Phase 3
            floorRenderer = new AsyncFloorRenderer(workerPool);
            fogHandler = new AsyncFogHandler(workerPool);
            firePuddleHandler = new AsyncFirePuddleHandler(workerPool);

            // Phase 4
            trailRenderer = new AsyncTrailRenderer(workerPool);

            // Initial World Load Optimization Hook
            Events.on(WorldLoadEvent.class, event -> {
                Log.info("[FrameParallel] WorldLoadEvent triggered - Optimizing initial map loading across multi-cores...");
                if (minimapHandler != null) {
                    minimapHandler.scheduleFullUpdate();
                }
                if (floorRenderer != null) {
                    floorRenderer.update();
                }
            });

            // Game Update Hooks
            Events.run(Trigger.update, () -> {
                if (particleController != null) particleController.update();
                if (minimapHandler != null) minimapHandler.tick();
                if (powerGraphHandler != null) powerGraphHandler.update();
                if (targetSearchHandler != null) targetSearchHandler.update();
                if (buildingSearchHandler != null) buildingSearchHandler.update();
                if (floorRenderer != null) floorRenderer.update();
                if (fogHandler != null) fogHandler.update();
                if (firePuddleHandler != null) firePuddleHandler.update();
                if (trailRenderer != null) trailRenderer.update();
            });

            // Draw Hooks
            Events.run(Trigger.draw, () -> {
                if (particleController != null) particleController.render();
            });

            Log.info("[FrameParallel] v@ successfully initialized and hooked all 9 multi-core modules + initial load optimizer.", VERSION);
        });
    }

    @Override
    public void loadContent() {
        Log.info("[FrameParallel] Content loading initialized.");
    }
}