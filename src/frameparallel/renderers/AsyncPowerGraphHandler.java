package frameparallel.renderers;

import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.world.blocks.power.PowerGraph;
import frameparallel.async.RenderWorkerPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 독립된 PowerGraph (전력망) 객체들을 멀티코어 워커 스레드로 분산 연산하는 핸들러.
 *
 * 특성:
 *   - 전력망은 2D 공간 청크가 아니라 그래프 토폴로지(Graph Topology) 구조임.
 *   - 각 PowerGraph 인스턴스는 다른 전력망과 독립적으로 생산/소비/배터리 충방전을 계산함.
 *   - N개의 전력망이 존재할 때 워커 스레드풀에 배치 분할 제출하여 병렬 연산.
 */
public class AsyncPowerGraphHandler {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    public AsyncPowerGraphHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncPowerGraphHandler initialized.");
    }

    /**
     * 매 틱 게임 업데이트 직전 또는 전력망 업데이트 시점에 호출됨.
     */
    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying() || Vars.state.isEditor()) return;

        Seq<PowerGraph> graphs = Groups.powerGraph;
        if (graphs == null || graphs.size == 0) return;

        int size = graphs.size;

        // 전력망이 1개이거나 소수일 때는 메인 스레드 오버헤드 방지를 위해 즉시 실행
        if (size <= 2) {
            for (int i = 0; i < size; i++) {
                PowerGraph g = graphs.get(i);
                if (g != null) {
                    try {
                        g.update();
                    } catch (Throwable t) {
                        Log.err("[FrameParallel] Error in single PowerGraph update", t);
                    }
                }
            }
            return;
        }

        // 전력망이 다수(3개 이상)일 때 그래프 단위 멀티코어 분할 연산
        int threadCount = Math.min(size, Runtime.getRuntime().availableProcessors());
        int chunkSize = (int) Math.ceil((double) size / threadCount);

        List<Future<?>> futures = new ArrayList<>(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, size);

            if (start >= size) break;

            futures.add(workerPool.submit(() -> {
                for (int i = start; i < end; i++) {
                    PowerGraph g = graphs.get(i);
                    if (g != null) {
                        try {
                            g.update();
                        } catch (Throwable ex) {
                            Log.err("[FrameParallel] Error in parallel PowerGraph update", ex);
                        }
                    }
                }
            }));
        }

        // 모든 전력망 병렬 연산 완료 대기
        for (Future<?> f : futures) {
            RenderWorkerPool.await(f);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
