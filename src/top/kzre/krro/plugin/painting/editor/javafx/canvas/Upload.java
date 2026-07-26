package top.kzre.krro.plugin.painting.editor.javafx.canvas;

import javafx.scene.image.PixelWriter;
import top.kzre.krro.util.tile.Tile;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.Arrays;

public class Upload {
    private static final int GRAY_ARGB = 0xFF888888;

    public static void upload(TiledCanvas canvas, int imgW, int imgH,
                              double offsetX, double offsetY, double zoom,
                              PixelWriter writer, int canvasW, int canvasH) {
        int tileSize = canvas.getTileSize();
        int channels = canvas.getChannels();

        for (int sy = 0; sy < canvasH; sy++) {
            for (int sx = 0; sx < canvasW; sx++) {
                double lx = sx / zoom + offsetX;
                double ly = sy / zoom + offsetY;

                int argb;
                if (lx >= 0 && lx < imgW && ly >= 0 && ly < imgH) {
                    argb = sampleCanvas(canvas, tileSize, channels, lx, ly, imgW, imgH);
                } else {
                    argb = GRAY_ARGB;
                }
                writer.setArgb(sx, sy, argb);
            }
        }
    }

    private static int sampleCanvas(TiledCanvas canvas, int tileSize, int channels,
                                    double lx, double ly, int w, int h) {
        int x0 = (int) Math.floor(lx);
        int y0 = (int) Math.floor(ly);
        double fx = lx - x0;
        double fy = ly - y0;

        float[] c00 = new float[channels], c10 = new float[channels],
                c01 = new float[channels], c11 = new float[channels];
        readPixel(canvas, tileSize, x0, y0, c00);
        readPixel(canvas, tileSize, x0 + 1, y0, c10);
        readPixel(canvas, tileSize, x0, y0 + 1, c01);
        readPixel(canvas, tileSize, x0 + 1, y0 + 1, c11);

        // 双线性插值
        float r = (float) (c00[0] * (1 - fx) * (1 - fy) + c10[0] * fx * (1 - fy) +
                c01[0] * (1 - fx) * fy + c11[0] * fx * fy);
        float g = (float) (c00[1] * (1 - fx) * (1 - fy) + c10[1] * fx * (1 - fy) +
                c01[1] * (1 - fx) * fy + c11[1] * fx * fy);
        float b = (float) (c00[2] * (1 - fx) * (1 - fy) + c10[2] * fx * (1 - fy) +
                c01[2] * (1 - fx) * fy + c11[2] * fx * fy);
        float a = (float) (c00[3] * (1 - fx) * (1 - fy) + c10[3] * fx * (1 - fy) +
                c01[3] * (1 - fx) * fy + c11[3] * fx * fy);

        // 转换为 ARGB
        int ia = Math.min(255, Math.max(0, (int) (a * 255 + 0.5)));
        int ir = Math.min(255, Math.max(0, (int) (r * 255 + 0.5)));
        int ig = Math.min(255, Math.max(0, (int) (g * 255 + 0.5)));
        int ib = Math.min(255, Math.max(0, (int) (b * 255 + 0.5)));
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static void readPixel(TiledCanvas canvas, int tileSize,
                                  int x, int y, float[] out) {
        if (x < 0 || y < 0) {
            Arrays.fill(out, 0f);
            return;
        }
        int tx = TiledCanvas.tileX(x, tileSize);
        int ty = TiledCanvas.tileY(y, tileSize);
        Tile tile = canvas.getTile(tx, ty);
        if (tile == null) {
            Arrays.fill(out, 0f);
            return;
        }
        int lx = TiledCanvas.localX(x, tileSize);
        int ly = TiledCanvas.localY(y, tileSize);
        tile.getPixel(lx, ly, out, tileSize, out.length);
    }
}