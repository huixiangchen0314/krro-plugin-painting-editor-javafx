package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

/**
 * 色环控件，继承自 ColorPickerBase，提供 HSV 色相/饱和度选择。
 */
public final class ColorWheel extends ColorPickerBase {

    private final Canvas canvas;
    private double centerX;
    private double centerY;
    private double radius;

    public ColorWheel() {
        canvas = new Canvas();
        // 将 Canvas 添加到 StackPane 中，自动居中
        this.getChildren().add(canvas);
        this.setMinWidth(0);
        this.setMinHeight(0);
        this.setPrefWidth(200);
        this.setPrefHeight(200);

        // 绑定 Canvas 尺寸到父容器，并监听变化重绘
        canvas.widthProperty().bind(this.widthProperty());
        canvas.heightProperty().bind(this.heightProperty());

        // 宽高变化时重新计算并重绘
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> draw());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> draw());

        // 鼠标事件
        canvas.setOnMousePressed(e -> handleMouse(e.getX(), e.getY()));
        canvas.setOnMouseDragged(e -> handleMouse(e.getX(), e.getY()));

        // 初始绘制
        draw();
    }

    private void handleMouse(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;
        double distance = Math.hypot(dx, dy);

        if (distance > radius) {
            // 超出色环范围，忽略
            return;
        }

        // 计算色相 (0-360)
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        double hue = angle;

        // 计算饱和度 (0-1)
        double saturation = distance / radius;
        if (saturation > 1) saturation = 1;

        // 明度固定为 1.0
        Color color = Color.hsb(hue, saturation, 1.0);
        setColor(color);
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        centerX = w / 2;
        centerY = h / 2;
        radius = Math.min(w, h) / 2;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        // 绘制色环
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.hypot(dx, dy);
                if (distance <= radius) {
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) angle += 360;
                    double hue = angle;
                    double saturation = distance / radius;
                    if (saturation > 1) saturation = 1;
                    Color color = Color.hsb(hue, saturation, 1.0);
                    pw.setColor(x, y, color);
                } else {
                    // 透明背景
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
    }

    @Override
    protected void onSetColor(Color color) {
        System.out.println(color);
    }
}