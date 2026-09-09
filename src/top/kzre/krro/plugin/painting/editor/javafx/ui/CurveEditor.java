package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.scene.layout.StackPane;
import top.kzre.curve.bezier2d.Curve;

/**
 * 曲线编辑器
 */
public class CurveEditor extends StackPane {
    private final Curve curve;

    public CurveEditor(Curve curve) {
        this.curve = curve;
    }
}
