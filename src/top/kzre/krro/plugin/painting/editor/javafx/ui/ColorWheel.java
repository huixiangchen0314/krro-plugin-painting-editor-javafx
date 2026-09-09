package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

/**
 * 色环控件：外部色相环带 + 内部 SV 三角形选择区。
 * 继承自 ColorPickerBase，提供 HSV 颜色选择。
 * 优化交互：全局跟踪，无范围限制；完善的边界容差。
 */
public final class ColorWheel extends ColorPickerBase {

    public static final double INNER_RADIUS_RATIO = 0.8;
    public static final double EPSILON = 1e-9;

    private final Canvas canvas;
    private double centerX, centerY;
    private double outerRadius, innerRadius;

    // 当前 HSV 分量
    private double currentHue = 0.0;
    private double currentSat = 1.0;
    private double currentBright = 1.0;

    // 三角形顶点
    private final double[] triX = new double[3];
    private final double[] triY = new double[3];

    // 交互状态
    private boolean hueMoving = false;
    private boolean svMoving = false;

    public ColorWheel() {
        canvas = new Canvas();
        getChildren().add(canvas);
        setMinSize(0, 0);
        setPrefSize(200, 200);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((obs, old, val) -> draw());
        canvas.heightProperty().addListener((obs, old, val) -> draw());

        canvas.setOnMousePressed(this::handleMousePressed);
//        canvas.setOnMouseDragged(this::handleMouseDragged);

        draw();
    }

    private void handleMousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();
        double dx = x - centerX;
        double dy = y - centerY;
        double dist = Math.hypot(dx, dy);

        if (dist > outerRadius) {
            return; // 超出色环忽略
        }

        if (dist >= innerRadius) {
            hueMoving = true;
            svMoving = false;
            updateHueFromPoint(x, y);
        } else {
            svMoving = true;
            hueMoving = false;
            double[] bary = getBarycentric(x, y);
            if (bary == null) {
                bary = projectToTriangle(x, y);
            }
            if (bary != null) {
                updateSVFromBarycentric(bary);
            }
        }

        canvas.getScene().addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        canvas.getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
    }

    private void handleMouseDragged(MouseEvent e) {
        double sceneX = e.getSceneX();
        double sceneY = e.getSceneY();
        double localX = canvas.sceneToLocal(sceneX, sceneY).getX();
        double localY = canvas.sceneToLocal(sceneX, sceneY).getY();

        if (hueMoving) {
            updateHueFromPoint(localX, localY);
        } else if (svMoving) {
            double[] bary = getBarycentric(localX, localY);
            if (bary == null) {
                bary = projectToTriangle(localX, localY);
            }
            if (bary != null) {
                updateSVFromBarycentric(bary);
            }
        }
        draw();
    }

    private void handleMouseReleased(MouseEvent e) {
        canvas.getScene().removeEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        canvas.getScene().removeEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        hueMoving = false;
        svMoving = false;
    }

    private double[] projectToTriangle(double px, double py) {
        double[][] edges = {
                {triX[0], triY[0], triX[1], triY[1]},
                {triX[1], triY[1], triX[2], triY[2]},
                {triX[2], triY[2], triX[0], triY[0]}
        };
        double minDist = Double.MAX_VALUE;
        double bestProjX = 0, bestProjY = 0;
        for (double[] edge : edges) {
            double x1 = edge[0], y1 = edge[1], x2 = edge[2], y2 = edge[3];
            double ex = x2 - x1, ey = y2 - y1;
            double t = ((px - x1) * ex + (py - y1) * ey) / (ex * ex + ey * ey);
            t = Math.max(0, Math.min(1, t));
            double projX = x1 + t * ex;
            double projY = y1 + t * ey;
            double dx = px - projX;
            double dy = py - projY;
            double dist = dx * dx + dy * dy;
            if (dist < minDist) {
                minDist = dist;
                bestProjX = projX;
                bestProjY = projY;
            }
        }
        return getBarycentric(bestProjX, bestProjY);
    }

    private void updateHueFromPoint(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        currentHue = angle;
        Color color = Color.hsb(currentHue, currentSat, currentBright);
        setColorWithoutNotify(color);
    }

    private void updateSVFromBarycentric(double[] coords) {
        double a = coords[0];
        double b = coords[1];
        double s = Math.max(0, Math.min(1, a));
        double v = Math.max(0, Math.min(1, a + b));
        // 保证 V >= S（三角形区域约束）
        if (v < s) v = s;
        currentSat = s;
        currentBright = v;
        Color color = Color.hsb(currentHue, currentSat, currentBright);
        setColorWithoutNotify(color);
    }

    private double[] getBarycentric(double px, double py) {
        double x1 = triX[0], y1 = triY[0];
        double x2 = triX[1], y2 = triY[1];
        double x3 = triX[2], y3 = triY[2];

        double denom = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (Math.abs(denom) < EPSILON) return null;

        double a = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / denom;
        double b = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / denom;
        double c = 1 - a - b;

        if (a >= -EPSILON && b >= -EPSILON && c >= -EPSILON) {
            return new double[]{Math.max(0, a), Math.max(0, b), Math.max(0, c)};
        }
        return null;
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        centerX = w / 2;
        centerY = h / 2;
        outerRadius = Math.min(w, h) / 2;
        innerRadius = outerRadius * INNER_RADIUS_RATIO;

        double angleOffset = -Math.PI / 2;
        for (int i = 0; i < 3; i++) {
            double angle = angleOffset + i * 2 * Math.PI / 3;
            triX[i] = centerX + innerRadius * Math.cos(angle);
            triY[i] = centerY + innerRadius * Math.sin(angle);
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                double dist = Math.hypot(dx, dy);

                if (dist > outerRadius) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }

                if (dist >= innerRadius) {
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) angle += 360;
                    pw.setColor(x, y, Color.hsb(angle, 1.0, 1.0));
                } else {
                    double[] bary = getBarycentric(x, y);
                    if (bary != null) {
                        double s = bary[0];
                        double v = bary[0] + bary[1];
                        s = Math.max(0, Math.min(1, s));
                        v = Math.max(0, Math.min(1, v));
                        pw.setColor(x, y, Color.hsb(currentHue, s, v));
                    } else {
                        pw.setColor(x, y, Color.TRANSPARENT);
                    }
                }
            }
        }

        drawHueIndicator(gc);
        drawSVIndicator(gc);
    }

    private void drawSVIndicator(GraphicsContext gc) {
        // 从当前 S,V 计算重心坐标，确保在三角形内
        double s = Math.max(0, Math.min(1, currentSat));
        double v = Math.max(0, Math.min(1, currentBright));
        // 强制 V >= S
        if (v < s) v = s;
        double a = s;
        double b = v - s;
        double c = 1 - v;

        // 截断并归一化，防止浮点误差
        if (a < 0) a = 0;
        if (b < 0) b = 0;
        if (c < 0) c = 0;
        double sum = a + b + c;
        if (sum > EPSILON) {
            a /= sum;
            b /= sum;
            c /= sum;
        } else {
            // 极端情况（理论上不会发生）
            a = 1.0 / 3.0;
            b = 1.0 / 3.0;
            c = 1.0 / 3.0;
        }

        double px = a * triX[0] + b * triX[1] + c * triX[2];
        double py = a * triY[0] + b * triY[1] + c * triY[2];
        gc.setFill(Color.WHITE);
        gc.fillOval(px - 4, py - 4, 8, 8);
        gc.setStroke(Color.BLACK);
        gc.strokeOval(px - 4, py - 4, 8, 8);
    }

    private void drawHueIndicator(GraphicsContext gc) {
        double angleRad = Math.toRadians(currentHue);
        double midR = (innerRadius + outerRadius) / 2;
        double lineLen = (outerRadius - innerRadius) * 0.6;

        double midX = centerX + midR * Math.cos(angleRad);
        double midY = centerY + midR * Math.sin(angleRad);
        double outerX = centerX + (midR + lineLen / 2) * Math.cos(angleRad);
        double outerY = centerY + (midR + lineLen / 2) * Math.sin(angleRad);
        double innerX = centerX + (midR - lineLen / 2) * Math.cos(angleRad);
        double innerY = centerY + (midR - lineLen / 2) * Math.sin(angleRad);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2.0);
        gc.strokeLine(midX, midY, outerX, outerY);
        gc.setStroke(Color.WHITE);
        gc.strokeLine(midX, midY, innerX, innerY);
    }

    @Override
    protected void onSetColor(Color color) {
        currentHue = color.getHue();
        currentSat = color.getSaturation();
        currentBright = color.getBrightness();
        draw();
    }
}