package frameparallel.renderers;

import arc.util.*;
import mindustry.graphics.ParticleRenderer;
import frameparallel.async.RenderWorkerPool;

public class ParticleRendererController {
    private final ParticleRenderer particleRenderer;
    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    public ParticleRendererController(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        this.particleRenderer = new ParticleRenderer();
        Log.info("[FrameParallel] ParticleRenderer initialized.");
    }

    public void update() {
        // Skip update overhead if no particles exist
        if (!enabled || particleRenderer == null || particleRenderer.count() == 0) return;
        try {
            particleRenderer.update();
        } catch (Throwable t) {
            Log.err("[FrameParallel] Error in ParticleRenderer update", t);
        }
    }

    public void render() {
        // Skip draw call and shader binding overhead if no particles exist
        if (!enabled || particleRenderer == null || particleRenderer.count() == 0) return;
        try {
            particleRenderer.render();
        } catch (Throwable t) {
            Log.err("[FrameParallel] Error in ParticleRenderer render", t);
        }
    }

    public ParticleRenderer getParticleRenderer() {
        return particleRenderer;
    }
}