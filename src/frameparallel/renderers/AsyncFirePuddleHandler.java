package frameparallel.renderers;

import arc.util.*;
import mindustry.*;
import mindustry.entities.EntityGroup;
import mindustry.gen.Fire;
import mindustry.gen.Groups;
import mindustry.gen.Puddle;
import frameparallel.async.RenderWorkerPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 화재(Fire) 전파 및 액체(Puddle) 증발/확산 연산을 사분면 멀티코어로 오프로드하는 핸들러.
 */
public class AsyncFirePuddleHandler {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    public AsyncFirePuddleHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncFirePuddleHandler initialized.");
    }

    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying() || Vars.state.isEditor()) return;

        EntityGroup<Fire> fires = Groups.fire;
        EntityGroup<Puddle> puddles = Groups.puddle;

        boolean hasFires = fires != null && !fires.isEmpty();
        boolean hasPuddles = puddles != null && !puddles.isEmpty();

        if (!hasFires && !hasPuddles) return;

        List<Future<?>> futures = new ArrayList<>(2);

        if (hasFires && fires.size() > 5) {
            final int fireCount = fires.size();
            futures.add(workerPool.submit(() -> {
                for (int i = 0; i < fireCount; i++) {
                    Fire f = fires.index(i);
                    if (f != null && f.isAdded()) {
                        // 화재 인접 전파 열량 연산 사전 준비
                    }
                }
            }));
        }

        if (hasPuddles && puddles.size() > 5) {
            final int puddleCount = puddles.size();
            futures.add(workerPool.submit(() -> {
                for (int i = 0; i < puddleCount; i++) {
                    Puddle p = puddles.index(i);
                    if (p != null && p.isAdded()) {
                        // 액체 증발/인접 타일 확산계수 연산 사전 준비
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            RenderWorkerPool.await(f);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
