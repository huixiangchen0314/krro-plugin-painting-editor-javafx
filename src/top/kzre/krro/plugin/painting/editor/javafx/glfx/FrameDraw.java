package top.kzre.krro.plugin.painting.editor.javafx.glfx;

import com.huskerdev.openglfx.canvas.events.GLRenderEvent;
import top.kzre.krro.canvas.gl.resource.GLTexture;
import top.kzre.krro.canvas.gl.tile.TiledTextureBlitter;
import top.kzre.krro.canvas.gl.util.ResourceUtils;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL32.GL_TIMEOUT_IGNORED;
import static org.lwjgl.opengl.GL32.glDeleteSync;
import static org.lwjgl.opengl.GL32.glFinish;
import static org.lwjgl.opengl.GL32.glWaitSync;

/**
 * 单帧绘制请求。持有一帧渲染产物的完整信息——纹理、层、canvas、
 * fence、渲染时的视口尺寸——并提供两种终态操作：
 *
 * <ul>
 *   <li>{@link #discard()} —— 帧从未被 blit。GPU 从未采样过 texture，
 *       直接删除 fence + 关闭 canvas，不做 GPU 同步。</li>
 *   <li>{@link #consume(TiledTextureBlitter, GLRenderEvent)} —— 帧被
 *       blit 到 GLCanvas 的 FBO。等离屏 GPU 写入完成、blit、glFinish
 *       确保采样结束，然后释放资源。</li>
 * </ul>
 *
 * <p><b>两种操作互斥</b>——一个帧最终要么被 discard（channel 满、
 * dispose 清理），要么被 consume（正常渲染路径）。内部的
 * {@link AtomicBoolean} 保证幂等——重复调用是 no-op。
 *
 * <p><b>线程契约</b>：
 * <ul>
 *   <li>{@code consume} 必须在 GLCanvas 的 GL 线程调用——内部的
 *       glWaitSync / blit / glFinish 都要求正确的上下文。</li>
 *   <li>{@code discard} 可在任意线程——它不做 GPU 同步，只删
 *       fence 和关闭 canvas。</li>
 *   <li>{@code close} 走 discard 路径——作为 AutoCloseable 的兜底。</li>
 * </ul>
 */
public final class FrameDraw implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final long fence;
    private final GLTexture viewTex;
    private final long layer;
    private final TiledCanvas canvas;
    private final double viewWidth;
    private final double viewHeight;

    /**
     * @param canvas     本帧的离屏画布——持有 texture 的引用
     * @param viewTex    视口纹理，由 canvas 内的 tile 引用
     * @param layer      viewTex 的层索引
     * @param viewWidth  渲染时的视口宽（逻辑像素），用于 blit 源区域
     * @param viewHeight 渲染时的视口高（逻辑像素）
     * @param fence      离屏侧 GPU 写入完成的同步点；0 表示无 fence
     * @throws IllegalArgumentException canvas 或 viewTex 为 null
     */
    public FrameDraw(TiledCanvas canvas, GLTexture viewTex, long layer,
                     double viewWidth, double viewHeight,
                     long fence) {
        if (canvas == null) throw new IllegalArgumentException("canvas can't be null.");
        if (viewTex == null) throw new IllegalArgumentException("view tex can't be null.");
        this.fence = fence;
        this.viewTex = viewTex;
        this.layer = layer;
        this.canvas = canvas;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    /**
     * 丢弃该帧——GPU 从未采样过 texture。直接删除 fence 并关闭 canvas，
     * 不做 GPU 同步。
     *
     * <p>用于 channel 满、dispose 清理等场景——该帧没有被 blit。
     */
    public void discard() {
        if (!closed.compareAndSet(false, true)) return;
        if (fence != 0) {
            glDeleteSync(fence);
        }
        if (!canvas.isClosed()) {
            RuntimeException error = ResourceUtils.closeDelayError(canvas);
            if (error != null) {
                throw error;
            }
        }
    }

    /**
     * 消费该帧——等待离屏侧 GPU 写入完成、blit 到 event 的 FBO、
     * 等 GPU 采样结束，然后释放资源。
     *
     * <p><b>线程契约</b>：必须在 GLCanvas 的 GL 线程调用。
     *
     * @param blitter blit 执行器，与 GLCanvas 会话同寿，非 null
     * @param event   当前 GLCanvas 渲染事件，非 null
     * @throws IllegalArgumentException blitter 或 event 为 null
     */
    public void consume(TiledTextureBlitter blitter, GLRenderEvent event) {
        if (blitter == null) throw new IllegalArgumentException("blitter can't be null.");
        if (event == null) throw new IllegalArgumentException("event can't be null.");
        if (!closed.compareAndSet(false, true)) return;

        // 1. 等离屏侧 GPU 写入完成
        if (fence != 0) {
            glWaitSync(fence, 0, GL_TIMEOUT_IGNORED);
        }

        // 2. blit——源用渲染时的视口尺寸，目标用事件尺寸
        blitter.blit(
                viewTex, (int) layer,
                (int) viewWidth, (int) viewHeight,
                event.fbo,
                event.width, event.height);

        // 3. 等 GPU 完成对 viewTex 的采样，再释放
        glFinish();

        if (fence != 0) {
            glDeleteSync(fence);
        }
        RuntimeException error = ResourceUtils.closeDelayError(canvas);
        if (error != null) {
            throw error;
        }
    }

    /**
     * 释放资源。幂等。走 discard 路径——不引入额外 GPU 同步。
     */
    @Override
    public void close() {
        discard();
    }

    public boolean isClosed() {
        return closed.get();
    }
}