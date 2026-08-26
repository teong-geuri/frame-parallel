package frameparallel.util;

/**
 * 런타임 플랫폼(데스크탑 / 안드로이드) 감지 유틸리티.
 *
 * Android는 Dalvik/ART JVM을 사용하므로 java.vm.name에 "Dalvik"이 포함된다.
 */
public final class PlatformUtil {

    /** true = Android(ART/Dalvik), false = 데스크탑(JVM) */
    public static final boolean IS_ANDROID =
        System.getProperty("java.vm.name", "").toLowerCase().contains("dalvik");

    /**
     * 플랫폼별 권장 워커 스레드 우선순위.
     *   - Android: NORM_PRIORITY (우선순위 조작이 ART 스케줄러에서 무의미하므로 기본값 유지)
     *   - Desktop:  NORM_PRIORITY + 1 (코어 할당 속도 향상)
     */
    public static final int WORKER_PRIORITY =
        IS_ANDROID ? Thread.NORM_PRIORITY : Thread.NORM_PRIORITY + 1;

    /**
     * 플랫폼별 미니맵 버퍼 최대 크기 (타일 수).
     *   - Android: 100,000 타일 (메모리 제한 대응)
     *   - Desktop:  500,000 타일 (대형 맵 지원)
     */
    public static final int MINIMAP_MAX_BUFFER =
        IS_ANDROID ? 100_000 : 500_000;

    /**
     * 플랫폼별 권장 병렬 배치 최소 항목 수 (parallelBatch minBatchSize 기본값).
     *   - Android: 64 (빅리틀 구조 코어 특성상 작은 배치가 오히려 오버헤드)
     *   - Desktop:  32
     */
    public static final int MIN_BATCH_SIZE =
        IS_ANDROID ? 64 : 32;

    private PlatformUtil() {}
}
