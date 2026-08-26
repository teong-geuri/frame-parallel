package frameparallel.async;

import arc.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * 스레드 우선순위 미세 조절 기반 멀티코어 워커 스레드 풀.
 *
 * 코어 할당 및 우선순위 방식:
 *   - 코어 수 하드 캡 제한 없이 메인 GL 스레드 1개 몫을 제외한 [전체 코어 - 1]개를 워커 코어로 가동.
 *   - 워커 스레드 우선순위를 일반 스레드보다 약간 높게(NORM_PRIORITY + 1) 설정하여
 *     코어 할당 순위가 올라가 병렬 연산이 더 빠르게 CPU 시간을 점유하도록 함.
 */
public class RenderWorkerPool {
    private final ExecutorService pool;
    private final int workerCount;
    private final int totalCores;

    public RenderWorkerPool() {
        this.totalCores = Runtime.getRuntime().availableProcessors();
        // 메인 스레드 1개를 남겨두고 풀 워커 코어 할당 (하드 캡 제거)
        this.workerCount = Math.max(1, totalCores - 1);

        this.pool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "FrameParallel-Worker");
            t.setDaemon(true);
            // 우선순위를 약간 높여(NORM_PRIORITY + 1) 코어 할당 속도 향상
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        });

        Log.info("[FrameParallel] Worker pool initialized: @ worker threads active across @ total CPU cores (Priority: NORM_PRIORITY + 1).", workerCount, totalCores);
    }

    /** 작업 1개를 백그라운드 워커 코어에 제출 */
    public Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    /**
     * 동적 래인지 분할 연산.
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