package frameparallel.renderers;

import arc.util.*;
import mindustry.Vars;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import frameparallel.async.RenderWorkerPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 유닛 및 탄환 궤적(Trail) 정점 버퍼 생성을 코어별 배열 범위 분할(Range Slicing)하여 연산하는 핸들러.
 */
public class AsyncTrailRenderer {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    public AsyncTrailRenderer(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncTrailRenderer initialized.");
    }

    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying()) return;

        if (Groups.bullet == null || Groups.bullet.size() == 0) return;

        int size = Groups.bullet.size();
        if (size < 10) return; // 탄환 수가 적을 때는 오버헤드 방지

        int threadCount = Math.min(size / 5, Runtime.getRuntime().availableProcessors());
        if (threadCount <= 1) return;

        int chunkSize = (int) Math.ceil((double) size / threadCount);

        List<Future<?>> futures = new ArrayList<>(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, size);
            if (start >= size) break;

            futures.add(workerPool.submit(() -> {
                for (int i = start; i < end; i++) {
                    Bullet b = Groups.bullet.index(i);
                    if (b != null && b.trail != null) {
                        try {
                            b.trail.update(b.x, b.y);
                        } catch (Throwable ignored) {
                        }
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
