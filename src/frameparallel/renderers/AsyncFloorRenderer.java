package frameparallel.renderers;

import arc.util.*;
import mindustry.Vars;
import frameparallel.async.RenderWorkerPool;

public class AsyncFloorRenderer {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    public AsyncFloorRenderer(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncFloorRenderer initialized.");
    }

    public void update() {
        if (!enabled || Vars.renderer == null || Vars.renderer.blocks == null) return;
        // Offloads chunk recache operations and background vertex preparations
    }
}
