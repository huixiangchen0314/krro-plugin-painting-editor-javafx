package top.kzre.krro.plugin.painting.editor.javafx.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * 颜色选择器控件的基类，提供颜色属性的标准管理。
 * 子类应实现具体的 UI 绘制和交互逻辑。
 */
public abstract class ColorPickerBase extends StackPane {

    private final ObjectProperty<Color> color = new SimpleObjectProperty<>(this, "color", Color.WHITE);

    /**
     * 返回颜色属性，用于绑定和监听。
     *
     * @return 颜色 ObjectProperty
     */
    public final ObjectProperty<Color> colorProperty() {
        return color;
    }

    /**
     * 获取当前颜色。
     *
     * @return 当前 Color 对象
     */
    public final Color getColor() {
        return color.get();
    }

    /**
     * 设置当前颜色，并触发 {@link #onSetColor(Color)} 回调。
     *
     * @param value 新的 Color 对象
     */
    public final void setColor(Color value) {
        color.set(value);
        onSetColor(colorProperty().get());
    }

    /**
     * 设置当前颜色，但不触发 {@link #onSetColor(Color)} 回调。
     * 子类可在内部使用，避免递归更新。
     *
     * @param color 新的 Color 对象
     */
    protected final void setColorWithoutNotify(Color color) {
        this.color.set(color);
    }

    /**
     * 当颜色通过 {@link #setColor(Color)} 被设置后调用。
     * 子类可重写此方法以更新 UI 或触发其他逻辑。
     *
     * @param color 当前设置的颜色
     */
    protected abstract void onSetColor(Color color);

}