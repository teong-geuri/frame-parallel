package frameparallel;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import frameparallel.async.RenderWorkerPool;
import frameparallel.renderers.AsyncFloorRenderer;
import frameparallel.renderers.AsyncMinimapHandler;
import frameparallel.renderers.ParticleRendererController;

public class FrameParallelMod extends Mod{

    private RenderWorkerPool workerPool;
    private ParticleRendererController particleController;
    private AsyncMinimapHandler minimapHandler;
    private AsyncFloorRenderer floorRenderer;

    public FrameParallelMod(){
        Log.info("[FrameParallel] Initializing Frame Parallelization Mod...");

        Events.on(ClientLoadEvent.class, e -> {
            workerPool = new RenderWorkerPool();
            particleController = new ParticleRendererController(workerPool);
            minimapHandler = new AsyncMinimapHandler(workerPool);
            floorRenderer = new AsyncFloorRenderer(workerPool);

            Events.run(Trigger.update, () -> {
                if(particleController != null){
                    particleController.update();
                }
                if(minimapHandler != null){
                    minimapHandler.tick();
                }
                if(floorRenderer != null){
                    floorRenderer.update();
                }
            });

            Events.run(Trigger.draw, () -> {
                if(particleController != null){
                    particleController.render();
                }
            });

            Log.info("[FrameParallel] Frame Parallelization Mod successfully loaded and hooked.");
        });
    }

    @Override
    public void loadContent(){
        Log.info("[FrameParallel] Content loading initialized.");
    }
}
