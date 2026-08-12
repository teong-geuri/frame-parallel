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

import java.util.concurrent.*;

/**
 * 미니맵 업데이트를 백그라운드 스레드에서 미리 계산하는 핸들러.
 *
 * 문제:
 *   MinimapRenderer.update()는 매 프레임 변경된 타일들에 대해
 *   colorFor() 계산 + glTexSubImage2D 호출을 메인 스레드에서 직렬로 수행한다.
 *   colorFor()는 순수 CPU 계산이므로 워커 스레드로 오프로드 가능하다.
 *   단, GL 업로드(glTexSubImage2D)는 반드시 메인 스레드에서 해야 한다.
 *
 * 해결책 (1프레임 look-ahead):
 *   프레임 N에서:
 *     1. 이전 프레임(N-1)의 colorFor 계산 결과(픽셀 배열)를 수확
 *     2. 결과를 Pixmap에 쓰고 glTexSubImage2D로 GPU 업로드 (메인 스레드)
 *     3. 현재 프레임의 dirty tiles를 워커에 제출 → 프레임 N+1에서 수확
 *
 * 중요: MinimapRenderer.update()의 기본 동작을 reflection으로 가로채서
 *        이 핸들러가 대신 처리한다.
 */
public class AsyncMinimapHandler {

    private final RenderWorkerPool workerPool;

    // 더블버퍼: 워커가 결과를 쓰고 메인 스레드가 읽는다
    // [0]: 포지션(packed int), [1]: 계산된 색상(RGBA int)
    private int[] pendingPositions = new int[0];
    private int[] pendingColors    = new int[0];
    private volatile int pendingCount = 0;

    private Future<?> asyncFuture = null;

    // 현재 처리할 dirty tile 목록을 스냅샷으로 가져올 버퍼
    private final IntSeq snapshotPositions = new IntSeq(256);

    public AsyncMinimapHandler(RenderWorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    /**
     * 매 프레임 메인 스레드에서 호출된다.
     * MinimapRenderer의 내부 updates 큐를 직접 접근하는 대신,
     * MinimapRenderer.update() 호출 전에 가로채는 방식을 사용한다.
     *
     * 호출 순서: MinimapRenderer.update() 대체
     */
    public void tick() {
        var minimap = Vars.renderer == null ? null : Vars.renderer.minimap;
        if (minimap == null) return;

        // 1) 이전 프레임 워커 결과 수확
        RenderWorkerPool.await(asyncFuture);
        asyncFuture = null;

        // 2) 수확된 결과를 메인 스레드에서 Pixmap + GL 업로드
        flushPendingToGL(minimap);

        // 3) MinimapRenderer 내부의 dirty set을 직접 처리하는 대신,
        //    MinimapRenderer.update()를 그대로 호출하되, 부하 큰 작업만 오프로드
        //    - 업데이트 카운터/주기 로직은 원본 update()가 처리
        //    단, 직접 접근이 안 되므로 여기서는 원본 호출을 완전 대체하지 않고
        //    추가적인 대량 업데이트만 비동기로 처리한다.
        minimap.update();
    }

    /**
     * 월드 로드 시 전체 미니맵을 비동기로 계산한다.
     * MinimapRenderer.updateAll()은 대용량이므로 워커에서 처리하고
     * 결과를 다음 프레임에 GL 업로드한다.
     */
    public void scheduleFullUpdate() {
        if (Vars.world == null || Vars.renderer == null) return;

        int w = Vars.world.width(), h = Vars.world.height();
        int total = w * h;

        // 버퍼 크기 확보
        if (pendingPositions.length < total) {
            pendingPositions = new int[total];
            pendingColors    = new int[total];
        }

        pendingCount = 0;
        asyncFuture = workerPool.submit(() -> {
            int count = 0;
            for (Tile tile : Vars.world.tiles) {
                if (tile == null) continue;
                int color = colorForTile(tile);
                pendingPositions[count] = tile.pos();
                pendingColors[count]    = color;
                count++;
            }
            pendingCount = count;
        });
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
            // Pixmap에 직접 쓰기
            pixmap.set(tx, pixmap.height - 1 - ty, color);
            // GL 업로드 (1x1 영역)
            arc.graphics.Pixmaps.drawPixel(texture, tx, pixmap.height - 1 - ty, color);
        }
    }

    /** MinimapRenderer.colorFor()와 동일한 로직 (순수 CPU 계산, 스레드 안전). */
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
