package frameparallel.async;

import arc.util.*;
import frameparallel.util.PlatformUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * 플랫폼 적응형 멀티코어 워커 스레드 풀.
 *
 * 코어 할당 및 우선순위 방식:
 *   - 메인 GL 스레드 1개 몫을 제외한 [전체 코어 - 1]개를 워커 코어로 가동.
 *   - 데스크탑: NORM_PRIORITY + 1 (코어 할당 속도 향상)
 *   - Android:  NORM_PRIORITY (ART 스케줄러가 우선순위 조작을 무시하므로 기본값 유지)
 */
public class RenderWorkerPool {
    private final ExecutorService pool;
    private final int workerCount;
    private final int totalCores;

    public RenderWorkerPool() {
        this.totalCores = Runtime.getRuntime().availableProcessors();
        // 메인 스레드 1개 남겨두고 전체 워커 코어 할당
        this.workerCount = Math.max(1, totalCores - 1);

        final int priority = PlatformUtil.WORKER_PRIORITY;

        this.pool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "FrameParallel-Worker");
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        });

        Log.info("[FrameParallel] Worker pool initialized: @ workers / @ cores | Platform: @ | Priority: @",
            workerCount, totalCores,
            PlatformUtil.IS_ANDROID ? "Android" : "Desktop",
            priority);
    }

    /** 작업 1개를 백그라운드 워커 코어에 제출 */
    public Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    /**
     * 동적 래인지 분할 연산.
     * minBatchSize 기본값은 PlatformUtil.MIN_BATCH_SIZE를 사용 권장.
     */
    public void parallelBatch(int totalItems, int minBatchSize, BiConsumer<Integer, Integer> batchConsumer) {
        if (totalItems <= 0 || batchConsumer == null) return;

        if (totalItems <= minBatchSize || workerCount <= 1) {
            try {
                batchConsumer.accept(0, totalItems);
            } catch (Throwable t) {
                Log.err("[FrameParallel] Single batch execution error", t);
            }
            return;
        }

        int threadsToUse = Math.min(workerCount, (int) Math.ceil((double) totalItems / minBatchSize));
        int chunkSize = (int) Math.ceil((double) totalItems / threadsToUse);

        List<Future<?>> futures = new ArrayList<>(threadsToUse);

        for (int t = 0; t < threadsToUse; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, totalItems);

            if (start >= totalItems) break;

            futures.add(pool.submit(() -> {
                try {
                    batchConsumer.accept(start, end);
                } catch (Throwable ex) {
                    Log.err("[FrameParallel] Parallel batch worker error", ex);
                }
            }));
        }

        for (Future<?> f : futures) {
            await(f);
        }
    }

    /** Future 결과를 메인 스레드에서 동기적으로 수확 */
    public static void await(Future<?> future) {
        if (future == null) return;
        try {
            future.get();
        } catch (Throwable t) {
            Log.err("[FrameParallel] Worker error", t);
        }
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public int getTotalCores() {
        return totalCores;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}