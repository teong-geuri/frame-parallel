package frameparallel.renderers;

import arc.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.world.*;
import frameparallel.async.RenderWorkerPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 미니맵 업데이트를 백그라운드 스레드에서 미리 계산하는 핸들러.
 *
 * 초기 로딩 최적화:
 *   - WorldLoadEvent 시전 전체 미니맵 픽셀 연산(25만+ 타일)을 모든 CPU 코어에 병렬 분할 할당하여 초기 로딩 랙 극복.
 *
 * 1프레임 look-ahead 파이프라이닝:
 *   - 매 틱 변경 타일을 워커에서 colorForTile 계산 후 GL 업로드.
 */
public class AsyncMinimapHandler {

    private final RenderWorkerPool workerPool;

    // 더블버퍼: 워커가 결과를 쓰고 메인 스레드가 읽는다
    private int[] pendingPositions = new int[0];
    private int[] pendingColors    = new int[0];
    private volatile int pendingCount = 0;

    private List<Future<?>> fullUpdateFutures = new ArrayList<>();
    private Future<?> asyncFuture = null;

    public AsyncMinimapHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    public void tick() {
        var minimap = Vars.renderer == null ? null : Vars.renderer.minimap;
        if (minimap == null) return;

        // 1) 전체 로딩 병렬 작업 대기
        if (!fullUpdateFutures.isEmpty()) {
            for (Future<?> f : fullUpdateFutures) {
                RenderWorkerPool.await(f);
            }
            fullUpdateFutures.clear();
        }

        // 2) 일반 비동기 작업 수확
        RenderWorkerPool.await(asyncFuture);
        asyncFuture = null;

        // 3) 수확된 결과를 메인 스레드에서 Pixmap + GL 업로드
        flushPendingToGL(minimap);

        minimap.update();
    }

    /**
     * 월드 로드 시 전체 미니맵을 모든 CPU 코어에 Y행 단위로 병렬 분할하여 멀티코어로 고속 계산한다.
     */
    public void scheduleFullUpdate() {
        if (Vars.world == null || Vars.world.tiles == null || Vars.renderer == null) return;

        int w = Vars.world.width(), h = Vars.world.height();
        int total = w * h;
        if (total <= 0) return;

        if (pendingPositions.length < total) {
            pendingPositions = new int[total];
            pendingColors    = new int[total];
        }

        pendingCount = total;

        int cores = Math.min(h, Runtime.getRuntime().availableProcessors());
        int rowsPerThread = (int) Math.ceil((double) h / cores);

        fullUpdateFutures.clear();

        for (int c = 0; c < cores; c++) {
            final int startY = c * rowsPerThread;
            final int endY = Math.min(startY + rowsPerThread, h);

            if (startY >= h) break;

            fullUpdateFutures.add(workerPool.submit(() -> {
                for (int y = startY; y < endY; y++) {
                    for (int x = 0; x < w; x++) {
                        Tile tile = Vars.world.tile(x, y);
                        if (tile == null) continue;

                        int idx = x + y * w;
                        int color = colorForTile(tile);

                        pendingPositions[idx] = tile.pos();
                        pendingColors[idx]    = color;
                    }
                }
            }));
        }
        Log.info("[FrameParallel] Parallel full minimap generation scheduled on @ cores for @x@ map.", cores, w, h);
    }

    /** 수확된 결과를 Pixmap에 쓰고 GL에 업로드한다. 메인 스레드에서만 호출. */
    private void flushPendingToGL(MinimapRenderer minimap) {
        int count = pendingCount;
        if (count == 0) return;
        pendingCount = 0;

        Pixmap pixmap  = minimap.getPixmap();
        Texture texture = minimap.getTexture();
        if (pixmap == null || texture == null) return;

        for (int i = 0; i < count; i++) {
            int pos   = pendingPositions[i];
            int color = pendingColors[i];
            int tx    = arc.math.geom.Point2.x(pos);
            int ty    = arc.math.geom.Point2.y(pos);

            if (tx >= 0 && tx < pixmap.width && ty >= 0 && ty < pixmap.height) {
                pixmap.set(tx, pixmap.height - 1 - ty, color);
            }
        }
        texture.load(texture.getTextureData());
    }

    private static int colorForTile(Tile tile) {
        if (tile == null) return 0;
        Block real = realBlock(tile);

        int bc = real.minimapColor(tile);
        if (bc == 0 && tile.block() == Blocks.air && tile.overlay() == Blocks.air) {
            bc = tile.floor().minimapColor(tile);
        }

        arc.graphics.Color color = arc.graphics.Color.white.cpy();
        color.set(bc == 0 ? MapIO.colorFor(real, tile.floor(), tile.overlay(), tile.team()) : bc);
        color.mul(1f - arc.math.Mathf.clamp(Vars.world.getDarkness(tile.x, tile.y) / 4f));

        if (real == Blocks.air && tile.y < Vars.world.height() - 1
            && realBlock(Vars.world.tile(tile.x, tile.y + 1)).solid) {
            color.mul(0.7f);
        } else if (tile.floor().isLiquid
            && (tile.y >= Vars.world.height() - 1 || !Vars.world.tile(tile.x, tile.y + 1).floor().isLiquid)) {
            color.mul(0.84f, 0.84f, 0.9f, 1f);
        }

        return color.rgba();
    }

    private static Block realBlock(Tile tile) {
        return tile.build == null ? tile.block()
            : (Vars.state.rules.fog && !tile.build.wasVisible ? Blocks.air : tile.block());
    }
}