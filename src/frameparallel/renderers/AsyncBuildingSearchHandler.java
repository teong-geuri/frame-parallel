package frameparallel.renderers;

import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import frameparallel.async.RenderWorkerPool;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 손상된 건물(Damaged Building) 타깃 리스트를 메인 스레드에서 안전하게 스냅샷 후 캐시하는 핸들러.
 *
 * 안전성 보장:
 *   BlockIndexer.getDamaged()는 내부에서 tiles.removeAll()로 원본 배열을 직접 변형한다.
 *   이를 백그라운드 스레드에서 호출하면 메인 스레드의 eachBlock() 순회 도중 items[i] = null
 *   경쟁 상태(race condition)가 발생해 NPE 크래시를 유발한다.
 *   따라서 getDamaged() 호출은 반드시 메인 스레드에서만 수행하고,
 *   그 결과를 깊은 복사(deep copy) 스냅샷으로 캐시에 보관한다.
 */
public class AsyncBuildingSearchHandler {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    // 팀별 손상된 건물 타깃 스냅샷 캐시 (읽기 전용, 타 스레드에서 읽기 안전)
    private final ConcurrentHashMap<Team, Seq<Building>> damagedCache = new ConcurrentHashMap<>();

    public AsyncBuildingSearchHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncBuildingSearchHandler initialized (thread-safe snapshot mode).");
    }

    /**
     * 메인 스레드에서 호출: getDamaged()는 원본 Seq를 변형하므로 반드시 메인 스레드에서만 읽고,
     * 결과를 즉시 copy()로 스냅샷화하여 캐시에 저장한다.
     */
    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying() || Vars.state.isEditor()) return;
        if (Vars.indexer == null) return;

        // 반드시 메인 스레드에서만 getDamaged() 호출 (race condition 방지)
        for (Team team : Team.all) {
            if (!team.active()) continue;
            try {
                Seq<Building> damaged = Vars.indexer.getDamaged(team);
                if (damaged != null && !damaged.isEmpty()) {
                    // copy()로 불변 스냅샷을 만들어 캐시에 저장
                    damagedCache.put(team, damaged.copy());
                } else {
                    damagedCache.remove(team);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 캐시된 손상 건물 리스트 반환 (스냅샷이므로 어느 스레드에서도 안전하게 읽기 가능) */
    public Seq<Building> getDamaged(Team team) {
        return damagedCache.get(team);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
