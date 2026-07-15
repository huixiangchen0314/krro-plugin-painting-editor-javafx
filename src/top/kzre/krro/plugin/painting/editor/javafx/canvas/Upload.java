package top.kzre.krro.plugin.painting.editor.javafx.canvas;

import javafx.scene.image.PixelWriter;
import top.kzre.krro.plugin.painting.editor.core.Viewport;

public class Upload {
    public static void upload(float[] src, int srcW, int srcH,
                              double offsetX, double offsetY, double zoom,
                              PixelWriter writer, int canvasW, int canvasH) {
        for (int sy = 0; sy < canvasH; sy++) {
            for (int sx = 0; sx < canvasW; sx++) {
                double lx = sx / zoom + offsetX;
                double ly = sy / zoom + offsetY;
                int argb = Viewport.samplePixel(src, srcW, srcH, lx, ly);
                writer.setArgb(sx, sy, argb);
            }
        }
    }
}
