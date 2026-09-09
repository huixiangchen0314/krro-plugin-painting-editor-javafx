package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

/**
 * SV 矩形选择器：横轴饱和度 (S)，纵轴明度 (V)，色相 (H) 由当前颜色决定。
 * 用户点击或拖动选择 S/V，指示器显示当前位置。
 * 继承自 ColorPickerBase，与颜色属性同步。
 */
public final class SVRect extends ColorPickerBase {

    private final Canvas canvas;

    private double currentHue = 0.0;
    private double currentSat = 0.5;
    private double currentVal = 0.5;

    private boolean dragging = false;

    public SVRect() {
        canvas = new Canvas();
        getChildren().add(canvas);
        setMinSize(100, 100);
        setPrefSize(200, 200);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((obs, old, val) -> draw());
        canvas.heightProperty().addListener((obs, old, val) -> draw());

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        draw();
    }

    private void handleMousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();
        updateFromPoint(x, y);
        dragging = true;
        canvas.getScene().addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleGlobalMouseDragged);
        canvas.getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleGlobalMouseReleased);
    }

    private void handleMouseDragged(MouseEvent e) {
        if (!dragging) return;
        updateFromPoint(e.getX(), e.getY());
    }

    private void handleGlobalMouseDragged(MouseEvent e) {
        if (!dragging) return;
        double localX = canvas.sceneToLocal(e.getSceneX(), e.getSceneY()).getX();
        double localY = canvas.sceneToLocal(e.getSceneX(), e.getSceneY()).getY();
        updateFromPoint(localX, localY);
    }

    private void handleGlobalMouseReleased(MouseEvent e) {
        dragging = false;
        canvas.getScene().removeEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleGlobalMouseDragged);
        canvas.getScene().removeEventFilter(MouseEvent.MOUSE_RELEASED, this::handleGlobalMouseReleased);
    }

    private void updateFromPoint(double x, double y) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        double s = clamp(x / w);
        double v = clamp(1.0 - y / h); // 纵轴反转：顶部为明度 1，底部为 0
        currentSat = s;
        currentVal = v;
        Color color = Color.hsb(currentHue, currentSat, currentVal);
        setColorWithoutNotify(color);
        draw();
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();
        int width = (int) w;
        int height = (int) h;

        for (int y = 0; y < height; y++) {
            double v = 1.0 - (double) y / height; // 顶部为 1，底部为 0
            for (int x = 0; x < width; x++) {
                double s = (double) x / width;
                Color color = Color.hsb(currentHue, s, v);
                pw.setColor(x, y, color);
            }
        }

        // 绘制指示器
        int sx = (int) (currentSat * w);
        int sy = (int) ((1.0 - currentVal) * h);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeOval(sx - 6, sy - 6, 12, 12);
        gc.setFill(Color.BLACK);
        gc.fillOval(sx - 2, sy - 2, 4, 4);
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