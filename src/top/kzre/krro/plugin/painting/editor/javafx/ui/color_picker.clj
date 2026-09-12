(ns top.kzre.krro.plugin.painting.editor.javafx.ui.color-picker
  "颜色选择器组件工厂，支持色环、色带等自定义颜色控件。"
  (:require
    [top.kzre.krro.ui.javafx.core :refer [make-component]]
    [top.kzre.krro.ui.javafx.util :as javafx.util])
  (:import
    (javafx.beans.value ChangeListener)
    (javafx.scene.paint Color)
    (top.kzre.krro.plugin.painting.editor.javafx.ui ColorPickerBase)))



(def ^:private debounce-delay-ms 33)

;; ── 颜色转换 ─────────────────────────────────────
(defn- color->vec
  "JavaFX Color → [r g b]（0-1 浮点）。"
  [^Color c]
  [(.getRed c) (.getGreen c) (.getBlue c)])

(defn- vec->color
  " [r g b]（0-1 浮点）→ JavaFX Color。"
  [[r g b]]
  (Color/rgb (int (* r 255)) (int (* g 255)) (int (* b 255))))

(defn- resolve-color [color-val]
  (if (vector? color-val) (vec->color color-val) Color/BLACK))


;; ── 每个 picker 实例的防抖状态 ────────────────────
(def ^:private state-key ::debounce-state)

(defn- ensure-state!
  "确保 picker 拥有稳定的防抖状态。
   状态包含 :callback-atom（可变引用，props 变化时更新）和 :listener。
   状态存放在 picker.getProperties() 中，跨 props 变化保留。"
  [^ColorPickerBase picker]
  (let [props (.getProperties picker)]
    (or (.get props state-key)
        (let [cb-atom   (atom nil)
              debounced (javafx.util/debounced
                          #(when-let [cb @cb-atom] (cb %))
                          debounce-delay-ms)
              color-prop (.colorProperty picker)
              listener  (reify ChangeListener
                          (changed [_ _ _ new-color]
                            (debounced (color->vec new-color))))
              state     {:callback-atom cb-atom
                         :listener      listener
                         :color-prop    color-prop}]
          (.addListener color-prop listener)
          (.put props state-key state)
          state))))

;; ── 组件工厂 ─────────────────────────────────────
(defn color-picker-component
  "创建一个颜色选择器组件，包装 ColorPickerBase 子类。
   接受属性：
     :krro.painting/color              颜色（[r g b] 向量）
     :krro.painting/on-color-selected  回调（[r g b] 向量），已防抖"
  [color-picker-factory]
  (make-component
    [:krro.painting/color :krro.painting/on-color-selected]
    (fn [] (color-picker-factory))
    (fn [^ColorPickerBase picker old-props new-props _frame]
      ;; 1. 颜色属性变化 → 更新控件
      (when (or (nil? old-props)
                (not= (:krro.painting/color old-props)
                      (:krro.painting/color new-props)))
        (.setColor picker (resolve-color (:krro.painting/color new-props))))
      ;; 2. 回调变化 → 更新防抖状态中的 callback 引用
      (when-let [callback (:krro.painting/on-color-selected new-props)]
        (let [state (ensure-state! picker)]
          (reset! (:callback-atom state) callback)))
      nil)
    :bind (fn [^ColorPickerBase picker color]
            (.setColor picker (resolve-color color)))))