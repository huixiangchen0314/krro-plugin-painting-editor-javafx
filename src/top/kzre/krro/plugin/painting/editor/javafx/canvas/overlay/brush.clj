(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.overlay.brush
  "画笔工具的叠加层绘制。"
  (:require
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
    (javafx.scene.paint Color)
    (top.kzre.krro.plugin.painting.core.state CanvasRuntime)
    (top.kzre.krro.plugin.painting.core.tool.protocol ToolContext)))

(defn- logic->screen [viewport lx ly]
  (let [screen (vp/logic->screen viewport lx ly)]
    {:x (:x screen) :y (:y screen)}))

(defmethod tp/draw-overlay! :brush
  [tool ^CanvasRuntime _state ^ToolContext ctx]
  (when-let [gc (frame/param (:frame ctx) :krro.painting.javafx/overlay-gc)]
    (let [brush    (or @brush/global-brush brush/default-brush)
          radius   (:radius brush 10)
          viewport (vp/get-viewport (:frame ctx))
          pos      @(:current-pos tool)   ;; 从独立原子获取当前鼠标位置
          screen-pt (when pos (logic->screen viewport (:x pos) (:y pos)))]
      (.clearRect gc 0 0 (.getWidth (.getCanvas gc)) (.getHeight (.getCanvas gc)))
      (when screen-pt
        (let [screen-r (* radius (:zoom viewport))]
          (.setStroke gc Color/BLACK)
          (.setLineWidth gc 1.0)
          (.strokeOval gc (- (:x screen-pt) screen-r) (- (:y screen-pt) screen-r)
                       (* 2 screen-r) (* 2 screen-r)))))))