package top.kzre.krro.plugin.painting.editor.javafx.canvas;

import javafx.scene.image.PixelWriter;
import top.kzre.krro.plugin.painting.core.Viewport;

public class Upload {
    private static final int GRAY_ARGB = 0xFF888888; // 不透明灰色

    public static void upload(float[] src, int srcW, int srcH,
                              double offsetX, double offsetY, double zoom,
                              PixelWriter writer, int canvasW, int canvasH) {
        for (int sy = 0; sy < canvasH; sy++) {
            for (int sx = 0; sx < canvasW; sx++) {
                double lx = sx / zoom + offsetX;
                double ly = sy / zoom + offsetY;

                int argb;
                if (lx >= 0 && lx < srcW && ly >= 0 && ly < srcH) {
                    // 逻辑坐标在源图像内，正常采样（双线性插值）
                    argb = Viewport.samplePixel(src, srcW, srcH, lx, ly);
                } else {
                    // 越界区域填充灰色
                    argb = GRAY_ARGB;
                }
                writer.setArgb(sx, sy, argb);
            }
        }
    }
}