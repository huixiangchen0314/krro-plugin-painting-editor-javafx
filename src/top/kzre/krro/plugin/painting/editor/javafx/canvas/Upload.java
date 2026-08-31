package top.kzre.krro.plugin.painting.editor.javafx.canvas;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import top.kzre.krro.util.pool.ObjectPool;
import top.kzre.krro.util.tile.Tile;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

public final class Upload {
    private static final int CHECKER_COLOR1 = 0xFFCCCCCC;
    private static final int CHECKER_COLOR2 = 0xFF888888;
    // 64x64 像素一个棋盘格
    private static final int CHECKER_GRID = 64;
    private static final int GRAY_ARGB = 0xFF888888;
    private static final ObjectPool<TileTask> taskPool = new ObjectPool<>(TileTask::new, 1024, TileTask::reset);
    /**
     * 上传画布，画布已经经过预先视口变换，采样直接像素
     */
    public static void uploadPreTransformed(TiledCanvas canvas, int imgW, int imgH,
                                            double offsetX, double offsetY, double zoom,
                                            PixelWriter writer, int canvasW, int canvasH, int[] pixels) {
//        long t0 = System.nanoTime();

        int channels = canvas.getChannels();
        assert channels == 4;
        int tileSize = canvas.getTileSize();
        assert pixels.length == canvasW * canvasH;
//        int[] pixels = new int[canvasW * canvasH];
//        long t1 = System.nanoTime();

        // 预计算图像在屏幕空间的边界
        int intImgMinX = (int) Math.ceil(-offsetX * zoom);
        int intImgMaxX = (int) Math.floor((imgW - offsetX) * zoom);
        int intImgMinY = (int) Math.ceil(-offsetY * zoom);
        int intImgMaxY = (int) Math.floor((imgH - offsetY) * zoom);

        // 屏幕瓦片范围
        int tileMinX = 0;
        int tileMaxX = TiledCanvas.tileX(canvasW - 1, tileSize);
        int tileMinY = 0;
        int tileMaxY = TiledCanvas.tileY(canvasH - 1, tileSize);

        List<TileTask> tasks = new ArrayList<>();
        for (int tx = tileMinX; tx <= tileMaxX; tx++) {
            for (int ty = tileMinY; ty <= tileMaxY; ty++) {
                TileTask task = taskPool.acquire();
                task.set(tx, ty, canvas, tileSize, pixels, canvasW, canvasH,
                        intImgMinX, intImgMaxX, intImgMinY, intImgMaxY);
                tasks.add(task);
            }
        }
        ForkJoinTask.invokeAll(tasks);
        tasks.forEach(taskPool::release);
        tasks.clear();
//        long t2 = System.nanoTime();
        writer.setPixels(0, 0, canvasW, canvasH,
                PixelFormat.getIntArgbPreInstance(),
                pixels, 0, canvasW);
//        long t3 = System.nanoTime();

//        System.out.printf("uploadPreTransformed: alloc=%.3fms, sample=%.3fms, setPixels=%.3fms, total=%.3fms%n",
//                (t1-t0)/1e6, (t2-t1)/1e6, (t3-t2)/1e6, (t3-t0)/1e6);
    }

    private static class TileTask extends RecursiveAction {
        private int tx, ty;
        private TiledCanvas canvas;
        private int tileSize;
        private int[] pixels;
        private int canvasW, canvasH;
        private int intImgMinX, intImgMaxX, intImgMinY, intImgMaxY;


        public void set(int tx, int ty, TiledCanvas canvas, int tileSize, int[] pixels,
                 int canvasW, int canvasH,
                 int intImgMinX, int intImgMaxX, int intImgMinY, int intImgMaxY) {
            this.tx = tx; this.ty = ty;
            this.canvas = canvas; this.tileSize = tileSize;
            this.pixels = pixels; this.canvasW = canvasW; this.canvasH = canvasH;
            this.intImgMinX = intImgMinX; this.intImgMaxX = intImgMaxX;
            this.intImgMinY = intImgMinY; this.intImgMaxY = intImgMaxY;
        }

        // 及时释放
        public void reset(){
            this.pixels = null;
            this.canvas = null;
            reinitialize();
        }

        @Override
        protected void compute() {
            int screenStartX = Math.max(tx * tileSize, 0);
            int screenEndX = Math.min((tx + 1) * tileSize, canvasW);
            int screenStartY = Math.max(ty * tileSize, 0);
            int screenEndY = Math.min((ty + 1) * tileSize, canvasH);
            if (screenStartX >= screenEndX || screenStartY >= screenEndY) return;

            // 剪枝：如果整个瓦片都在图像范围外，填充灰色并返回
            if (screenEndX <= intImgMinX || screenStartX >= intImgMaxX ||
                    screenEndY <= intImgMinY || screenStartY >= intImgMaxY) {
                for (int sy = screenStartY; sy < screenEndY; sy++) {
                    int rowBase = sy * canvasW;
                    for (int sx = screenStartX; sx < screenEndX; sx++) {
                        pixels[rowBase + sx] = GRAY_ARGB;
                    }
                }
                return;
            }

            // 获取瓦片像素（可能在边界内）
            Tile tile = canvas.getTile(tx, ty);
            float[] tilePixels = (tile != null) ? tile.getPixelsSnapshot() : null;

            for (int sy = screenStartY; sy < screenEndY; sy++) {
                int rowBase = sy * canvasW;
                for (int sx = screenStartX; sx < screenEndX; sx++) {
                    // 像素级检查
                    if (sx >= intImgMinX && sx < intImgMaxX &&
                            sy >= intImgMinY && sy < intImgMaxY) {
                        // 在图像范围内
                        if (tilePixels != null) {
                            int localX = sx - tx * tileSize;
                            int localY = sy - ty * tileSize;
                            int offset = (localY * tileSize + localX) * 4;
                            float a = tilePixels[offset + 3];
                            if (a < 1e-6f) {
                                int gridX = sx / CHECKER_GRID;
                                int gridY = sy / CHECKER_GRID;
                                pixels[rowBase + sx] = ((gridX + gridY) & 1) == 0 ? CHECKER_COLOR1 : CHECKER_COLOR2;
                            } else {
                                float r = tilePixels[offset];
                                float g = tilePixels[offset + 1];
                                float b = tilePixels[offset + 2];
                                int ia = Math.round(a * 255);
                                int ir = Math.round(r * a * 255);
                                int ig = Math.round(g * a * 255);
                                int ib = Math.round(b * a * 255);
                                pixels[rowBase + sx] = (ia << 24) | (ir << 16) | (ig << 8) | ib;
                            }
                        } else {
                            // 瓦片不存在，视为透明
                            int gridX = sx / CHECKER_GRID;
                            int gridY = sy / CHECKER_GRID;
                            pixels[rowBase + sx] = ((gridX + gridY) & 1) == 0 ? CHECKER_COLOR1 : CHECKER_COLOR2;
                        }
                    } else {
                        // 图像范围外（边界部分），填充灰色
                        pixels[rowBase + sx] = GRAY_ARGB;
                    }
                }
            }
        }
    }


}