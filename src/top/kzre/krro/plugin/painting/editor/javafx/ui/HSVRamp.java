package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import top.kzre.krro.util.math.KMath;

/**
 * 三条水平渐变带：色相 (H)、饱和度 (S)、明度 (V)。
 * 垂直排列（VBox），每条带水平渐变，均可拖动调整对应的 HSV 分量。
 * 支持设置色相显示范围（例如 0-180°）和选择性显示条带。
 * 全局跟踪：光标移出画布仍可继续拖动。
 */
public final class HSVRamp extends ColorPickerBase {

    private enum Component { H, S, V }

    private static final int BAR_COUNT = Component.values().length;
    private static final double BAR_SPACING = 2.0;
    private static final double PADDING = 4.0;
    private static final double TRIANGLE_SIZE = 6.0;
    private static final double INDICATOR_OPACITY = 0.6;
    private static final double INDICATOR_LINE_WIDTH = 1.0;
    private static final double MIN_WIDTH = 200;
    private static final double MIN_HEIGHT = 30;
    private static final double PREF_WIDTH = 300;
    private static final double PREF_HEIGHT = 50;

    private final VBox container;
    private final Canvas[] canvases = new Canvas[BAR_COUNT];

    private double currentHue = 0.0;
    private double currentSat = 1.0;
    private double currentVal = 1.0;

    private final double hueRange;
    private final int hsvVisibility;

    private int draggingIndex = -1;

    public static final int HUE_VISIBLE   = 1 << 0;
    public static final int SAT_VISIBLE   = 1 << 1;
    public static final int VAL_VISIBLE   = 1 << 2;

    public HSVRamp() {
        this(60.0);
    }

    public HSVRamp(double hueRange) {
        this(hueRange, HUE_VISIBLE | SAT_VISIBLE | VAL_VISIBLE);
    }

    public HSVRamp(double hueRange, int hsvVisibility) {
        this.hueRange = KMath.clampd(hueRange, 0, 360);
        this.hsvVisibility = hsvVisibility & (HUE_VISIBLE | SAT_VISIBLE | VAL_VISIBLE);

        container = new VBox(BAR_SPACING);
        container.setStyle(String.format("-fx-padding: %fpx; -fx-background-color: #f0f0f0;", PADDING));

        for (int i = 0; i < BAR_COUNT; i++) {
            canvases[i] = createBar();
        }
        container.getChildren().addAll(canvases);

        getChildren().add(container);
        setMinSize(MIN_WIDTH, MIN_HEIGHT);
        setPrefSize(PREF_WIDTH, PREF_HEIGHT);

        widthProperty().addListener((obs, old, val) -> draw());
        heightProperty().addListener((obs, old, val) -> draw());

        draw();
    }

    private Canvas createBar() {
        Canvas canvas = new Canvas();
        canvas.setStyle("-fx-cursor: hand;");
        canvas.setOnMousePressed(e -> {
            double x = e.getX();
            double w = canvas.getWidth();
            if (w <= 0) return;
            double ratio = clamp(x / w);
            int idx = container.getChildren().indexOf(canvas);
            draggingIndex = idx;
            updateComponent(idx, ratio);

            canvas.getScene().addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleGlobalMouseDragged);
            canvas.getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleGlobalMouseReleased);
        });
        return canvas;
    }

    private void handleGlobalMouseDragged(MouseEvent e) {
        if (draggingIndex == -1) {
            Scene scene = getScene();
            if (scene != null){
                scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleGlobalMouseDragged);
                scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, this::handleGlobalMouseReleased);
            }
            return;
        }
        Canvas canvas = canvases[draggingIndex];
        if (!canvas.isVisible()) {
            draggingIndex = -1;
            return;
        }
        double localX = canvas.sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
        double w = canvas.getWidth();
        if (w <= 0) return;
        double ratio = clamp(localX / w);
        updateComponent(draggingIndex, ratio);
    }

    private void handleGlobalMouseReleased(MouseEvent e) {
        if (draggingIndex != -1) {
            Canvas canvas = canvases[draggingIndex];
            Scene scene = canvas.getScene();
           if (scene != null) {
               scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleGlobalMouseDragged);
               scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, this::handleGlobalMouseReleased);
           }
            draggingIndex = -1;
        }
    }

    private void updateComponent(int idx, double ratio) {
        Component comp = Component.values()[idx];
        switch (comp) {
            case H: currentHue = ratio * hueRange; break;
            case S: currentSat = ratio; break;
            case V: currentVal = ratio; break;
            default: return;
        }
        Color color = Color.hsb(currentHue, currentSat, currentVal);
        setColorWithoutNotify(color);
        draw();
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 计算可见条带数量
        int visibleCount = 0;
        boolean[] visible = new boolean[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            int mask = 1 << i;
            visible[i] = (hsvVisibility & mask) != 0;
            if (visible[i]) visibleCount++;
        }

        if (visibleCount == 0) {
            // 无可见条带，隐藏所有并返回
            for (Canvas c : canvases) c.setVisible(false);
            return;
        }

        double totalSpacing = BAR_SPACING * (visibleCount - 1);
        double barH = (h - totalSpacing) / visibleCount;
        double barW = w;

        // 设置每个 Canvas 的尺寸和可见性
        int visibleIndex = 0;
        for (int i = 0; i < BAR_COUNT; i++) {
            Canvas canvas = canvases[i];
            if (visible[i]) {
                canvas.setVisible(true);
                canvas.setManaged(true);
                if (canvas.getWidth() != barW || canvas.getHeight() != barH) {
                    canvas.setWidth(barW);
                    canvas.setHeight(barH);
                }
                // 绘制对应的条带
                switch (Component.values()[i]) {
                    case H: drawHueBar(canvas); break;
                    case S: drawSatBar(canvas); break;
                    case V: drawValBar(canvas); break;
                }
                visibleIndex++;
            } else {
                canvas.setVisible(false);
                canvas.setManaged(false);
            }
        }
    }

    private void drawHueBar(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        for (int x = 0; x < w; x++) {
            double ratio = (double) x / w;
            double hue = ratio * hueRange;
            Color color = Color.hsb(hue, 1.0, 1.0);
            for (int y = 0; y < h; y++) {
                pw.setColor(x, y, color);
            }
        }
        drawIndicator(gc, w, h, currentHue / hueRange);
    }

    private void drawSatBar(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        double hue = currentHue;
        double val = currentVal;
        for (int x = 0; x < w; x++) {
            double sat = (double) x / w;
            Color color = Color.hsb(hue, sat, val);
            for (int y = 0; y < h; y++) {
                pw.setColor(x, y, color);
            }
        }
        drawIndicator(gc, w, h, currentSat);
    }

    private void drawValBar(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        double hue = currentHue;
        double sat = currentSat;
        for (int x = 0; x < w; x++) {
            double val = (double) x / w;
            Color color = Color.hsb(hue, sat, val);
            for (int y = 0; y < h; y++) {
                pw.setColor(x, y, color);
            }
        }
        drawIndicator(gc, w, h, currentVal);
    }

    private void drawIndicator(GraphicsContext gc, int w, int h, double ratio) {
        int xPos = (int) (ratio * w);
        gc.setStroke(Color.rgb(255, 255, 255, INDICATOR_OPACITY));
        gc.setLineWidth(INDICATOR_LINE_WIDTH);
        gc.strokeLine(xPos, 0, xPos, h);

        double triSize = TRIANGLE_SIZE;
        double yTop = 2.0;
        double yBottom = h - 2.0;

        gc.setFill(Color.BLACK);
        gc.fillPolygon(
                new double[]{xPos - triSize / 2, xPos, xPos + triSize / 2},
                new double[]{yTop, yTop + triSize, yTop},
                3
        );
        gc.setFill(Color.WHITE);
        gc.fillPolygon(
                new double[]{xPos - triSize / 2, xPos, xPos + triSize / 2},
                new double[]{yBottom, yBottom - triSize, yBottom},
                3
        );
    }

    @Override
    protected void onSetColor(Color color) {
        currentHue = color.getHue();
        currentSat = color.getSaturation();
        currentVal = color.getBrightness();
        draw();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}