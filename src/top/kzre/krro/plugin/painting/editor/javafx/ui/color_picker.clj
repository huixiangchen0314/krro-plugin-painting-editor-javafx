(ns top.kzre.krro.plugin.painting.editor.javafx.ui.color-picker
  "颜色选择器组件工厂，支持色环、色带等自定义颜色控件。"
  (:require
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (top.kzre.krro.plugin.painting.editor.javafx.ui ColorPickerBase)
    (javafx.beans.value ChangeListener)
    (javafx.scene.paint Color)))

(defn- color->vec
  "将 JavaFX Color 对象转换为 [r g b] 向量，分量 0-255 整数。"
  [^Color c]
  [(int (* (.getRed c) 255))
   (int (* (.getGreen c) 255))
   (int (* (.getBlue c) 255))])

(defn- vec->color
  "将 [r g b] 向量（0-255 整数）转换为 JavaFX Color 对象。"
  [[r g b]]
  (Color/rgb r g b))

(defn color-picker-component
  "创建一个颜色选择器组件，包装 ColorPickerBase 子类。
   参数：
     - color-picker-factory: 无参函数，返回 ColorPickerBase 实例
   返回一个 Krrō 组件，接受 :krro.painting/color（[r g b] 向量或 Color 对象）和 :krro.painting/on-color-selected（回调函数，接收 [r g b] 向量）属性。"
  [color-picker-factory]
  (make-component
    [:krro.painting/color
     :krro.painting/on-color-selected]
    (fn [] (color-picker-factory))
    (fn [^ColorPickerBase picker old-props new-props _frame]
      ;; 初始化或颜色属性变化时更新控件
      (when (or (nil? old-props)
                (not= (:krro.painting/color old-props)
                      (:krro.painting/color new-props)))
        (let [color-val (:krro.painting/color new-props)
              color (cond
                      (vector? color-val) (vec->color color-val)
                      (instance? Color color-val) color-val
                      :else nil)]
          (when color
            (.setColor picker color))))
      ;; 管理颜色变化监听器
      (when-let [callback (:krro.painting/on-color-selected new-props)]
        (let [listener (reify ChangeListener
                         (changed [_ _ _ new-color]
                           (callback (color->vec new-color))))
              color-property (.colorProperty picker)]
          ;; 移除旧监听器（如果存在）
          (when-let [old-listener (.getUserData picker)]
            (.removeListener color-property ^ChangeListener old-listener))
          ;; 存储新监听器到 userData
          (.setUserData picker listener)
          (.addListener color-property listener))))))