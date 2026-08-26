package frameparallel.async;

import arc.util.*;
import java.util.concurrent.*;

/**
 * 렌더링 워커 스레드 풀.
 *
 * 설계 원칙 (1프레임 look-ahead 파이프라이닝):
 *   - 프레임 N 시작: 프레임 N-1에서 백그라운드에 넘겼던 계산 결과를 수확(await)
 *   - 프레임 N 렌더링: 수확된 결과를 GL에 업로드
 *   - 프레임 N 끝: 프레임 N+1에서 쓸 계산을 백그라운드에 제출
 *
 * 이 방식은 1프레임의 미소한 레이턴시를 추가하지만,
 * 메인 스레드가 CPU-heavy 계산을 기다리지 않아도 되므로 프레임 시간이 줄어든다.
 */
public class RenderWorkerPool {
    private final ExecutorService pool;

    public RenderWorkerPool() {
        // 메인 스레드 1개를 반드시 남겨두고 나머지 코어를 워커로 사용
        int cores = Runtime.getRuntime().availableProcessors();
        int workerCount = Math.max(1, cores - 1);
        this.pool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "FrameParallel-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        Log.info("[FrameParallel] Worker pool initialized: @ threads (@ cores available)", workerCount, cores);
    }

    /** 작업을 백그라운드에 제출하고 Future를 반환한다. */
    public Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    /** Future 결과를 메인 스레드에서 동기적으로 수확한다. 예외는 로그로만 기록. */
    public static void await(Future<?> future) {
        if (future == null) return;
        try {
            future.get();
        } catch (Throwable t) {
            Log.err("[FrameParallel] Worker error", t);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}