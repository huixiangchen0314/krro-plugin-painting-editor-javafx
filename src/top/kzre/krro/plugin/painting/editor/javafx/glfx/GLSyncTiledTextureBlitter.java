package top.kzre.krro.plugin.painting.editor.javafx.glfx;

import com.huskerdev.openglfx.canvas.events.GLRenderEvent;
import org.lwjgl.opengl.GL32;
import top.kzre.krro.canvas.gl.tile.GLTextureLayerCanvas;
import top.kzre.krro.canvas.gl.tile.TiledTextureBlitter;
import top.kzre.krro.core.util.BufferedChannel;
import top.kzre.krro.util.tile.TiledCanvas;

import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE;

public final class GLSyncTiledTextureBlitter implements AutoCloseable {
    private final TiledTextureBlitter blitter;
    private final BufferedChannel<FrameDraw> frameChan;

    private GLSyncTiledTextureBlitter(TiledTextureBlitter blitter) {
        this.blitter = blitter;
        this.frameChan = new BufferedChannel<>(2, FrameDraw::discard);
    }

    public static GLSyncTiledTextureBlitter create(int tileSize) {
        TiledTextureBlitter tiledTextureBlitter = new TiledTextureBlitter(tileSize);
        return new GLSyncTiledTextureBlitter(tiledTextureBlitter);
    }

    public void poll(GLRenderEvent event){
        FrameDraw draw = frameChan.poll();
        if (draw != null) {
            draw.consume(blitter, event);
        }
    }

    public void send(TiledCanvas canvas, double width, double height) {
        if (canvas == null) {
            throw new NullPointerException("canvas can't be null");
        }
        if (canvas.getTiles().isEmpty()) {
            return;
        }
        glFlush();
        long fence = GL32.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        GLTextureLayerCanvas texCanvas = GLTextureLayerCanvas.fromCanvas(canvas);
        FrameDraw frameDraw = new FrameDraw(texCanvas, width, height, fence);
        frameChan.send(frameDraw);
    }

    @Override
    public void close() throws Exception {
        blitter.close();
        frameChan.close();
    }
}
