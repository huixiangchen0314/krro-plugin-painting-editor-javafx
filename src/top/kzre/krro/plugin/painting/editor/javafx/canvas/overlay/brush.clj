(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.overlay.brush
  "画笔工具的叠加层绘制。"
  (:require
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
    (javafx.scene.paint Color)
    (top.kzre.krro.plugin.painting.core.state CanvasState)
    (top.kzre.krro.plugin.painting.core.tool.protocol ToolContext)))

(defn- logic->screen [viewport lx ly]
  (let [screen (vp/logic->screen viewport lx ly)]
    {:x (:x screen) :y (:y screen)}))

(defmethod tp/draw-overlay! :brush
  [tool ^CanvasState _state ^ToolContext ctx]
  (when-let [gc (frame/param (:frame ctx) :krro.painting.javafx/overlay-gc)]
    (let [overlay-desc (tp/overlay tool)
          radius   (:radius overlay-desc)
          viewport (vp/get-viewport (:frame ctx))
          screen-pt (logic->screen viewport (:x overlay-desc) (:y overlay-desc))]
      (.clearRect gc 0 0 (.getWidth (.getCanvas gc)) (.getHeight (.getCanvas gc)))
      (when screen-pt
        (let [screen-r (* radius (:zoom viewport))]
          (.setStroke gc Color/BLACK)
          (.setLineWidth gc 1.0)
          (.strokeOval gc (- (:x screen-pt) screen-r) (- (:y screen-pt) screen-r)
                       (* 2 screen-r) (* 2 screen-r)))))))