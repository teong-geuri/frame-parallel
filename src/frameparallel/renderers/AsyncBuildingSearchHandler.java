package frameparallel.renderers;

import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import frameparallel.async.RenderWorkerPool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 손상된 건물(Damaged Building) 및 수리/자원 타깃 탐색을 미리 워커 스레드에서 캐싱하는 핸들러.
 */
public class AsyncBuildingSearchHandler {

    private final RenderWorkerPool workerPool;
    private boolean enabled = true;

    // 팀별 손상된 건물 타깃 리스트 캐시
    private final ConcurrentHashMap<Team, Seq<Building>> damagedCache = new ConcurrentHashMap<>();
    private Future<?> pendingTask = null;

    public AsyncBuildingSearchHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
        Log.info("[FrameParallel] AsyncBuildingSearchHandler initialized.");
    }

    public void update() {
        if (!enabled || Vars.state == null || !Vars.state.isPlaying() || Vars.state.isEditor()) return;

        RenderWorkerPool.await(pendingTask);
        pendingTask = null;

        if (Vars.indexer == null) return;

        pendingTask = workerPool.submit(() -> {
            for (Team team : Team.all) {
                if (team.active()) {
                    try {
                        Seq<Building> damaged = Vars.indexer.getDamaged(team);
                        if (damaged != null && !damaged.isEmpty()) {
                            damagedCache.put(team, damaged.copy());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    public Seq<Building> getDamaged(Team team) {
        return damagedCache.get(team);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
