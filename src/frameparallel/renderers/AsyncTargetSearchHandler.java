package frameparallel.renderers;

import arc.util.*;
import mindustry.*;
import mindustry.entities.EntityGroup;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import frameparallel.async.RenderWorkerPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * BlockIndexer 및 QuadTree 기반 AI 타깃 탐색을 미리 워커 스레드에서 캐싱하는 핸들러.
 *
 * 특성:
 *   - 유닛 수가 많아지면 closestTarget() 및 closestEnemy() 탐색이 메인 스레드 병목이 됨.
 *   - 백그라운드 워커에서 활성 유닛들의 주변 적/건물 타깃 후보를 병렬로 검색하여 맵 캐시에 보관함.
 */
public class AsyncTargetSearchHandler {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    // (Unit ID -> Building Target Candidate) 캐시
    private final ConcurrentHashMap<Integer, Building> targetCache = new ConcurrentHashMap<>();
    private Future<?> pendingTask = null;

    public AsyncTargetSearchHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncTargetSearchHandler initialized.");
    }

    /**
     * 메인 틱 시작 시 이전 틱의 워커 타깃 캐시 결과를 확인하고 다음 틱 타깃 탐색 제출
     */
    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying() || Vars.state.isEditor()) return;

        // 1) 이전 워커 계산 대기
        RenderWorkerPool.await(pendingTask);
        pendingTask = null;

        // 2) 유닛 목록 확인 및 백그라운드 스레드에 사전 탐색 작업 오프로드
        EntityGroup<Unit> units = Groups.unit;
        if (units == null || units.isEmpty()) return;

        int count = units.size();

        pendingTask = workerPool.submit(() -> {
            targetCache.clear();
            for (int i = 0; i < count; i++) {
                Unit u = units.index(i);
                if (u != null && u.isValid() && u.isAI()) {
                    try {
                        // 사전 타깃 쿼드트리 탐색 (5번째: UnitPred, 6번째: TilePred)
                        Teamc closest = Units.closestTarget(u.team, u.x, u.y, u.range(), unit -> true, b -> b.block.targetable);
                        if (closest instanceof Building b) {
                            targetCache.put(u.id, b);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    public Building getCachedTarget(Unit unit) {
        return unit == null ? null : targetCache.get(unit.id);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
