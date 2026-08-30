(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.core
  (:require
   [top.kzre.krro.canvas.vector.core]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.core]
   [top.kzre.krro.plugin.painting.core.input :as input]
   [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.core]
   [top.kzre.krro.plugin.painting.core.render :as render]
   [top.kzre.krro.plugin.painting.core.spec :as spec]
   [top.kzre.krro.plugin.painting.core.state :as state]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.plugin.painting.editor.javafx.canvas.upload :as upload]
   [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer]
   [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.event EventHandler)
   (javafx.scene.canvas Canvas)
   (javafx.scene.layout StackPane)
   (top.kzre.krro.util.tile TiledCanvas)))

(defonce preview-canvas-key ::preview-canvas)

(defn cleanup-preview-canvas
  [frame]
  (when-let [canvas (frame/param frame preview-canvas-key)]
    (.clear canvas)))

(defn get-preview-canvas [frame]
  (if-let [canvas (frame/param frame preview-canvas-key)]
    canvas
    (let [new-canvas (TiledCanvas. pc/global-tile-size)]
      (frame/set-param! frame preview-canvas-key new-canvas)
      new-canvas)))



;; ── 画布会话创建 ─────────────────────────────
(defn- start-canvas-session [^StackPane stack
                             ^Canvas main-canvas
                             ^Canvas overlay-canvas
                             canvas-id
                             canvas-w
                             canvas-h
                             frame]
  (let [render-task-id (str canvas-id "-" (frame/frame-id frame))
        init-canvas-data (pc/canvas-data! canvas-id)
        upload-fn (upload/make-uploader main-canvas)
        overlay-gc (.getGraphicsContext2D overlay-canvas)
        on-event (fn [ev] (rf/dispatch store/app-id [:tool/dispatch-event canvas-id ev frame]))
        render-canvas-fn
        (fn [canvas-data dirty-tilesa]
          (let [viewport (vp/get-viewport frame)
                canvas (get-preview-canvas frame)]
            (render/request-render-viewport! render-task-id
                                    canvas canvas-data dirty-tilesa
                                    viewport canvas-w canvas-h
                                    upload-fn)))
        mouse-input (pointer/make-pointer-input stack on-event)

        on-render-canvas
        (fn [cid canvas-data dirty-tiles]
          (when (= cid canvas-id)
            (render-canvas-fn canvas-data dirty-tiles)))]

    (hook/add-hook! :krro.painting/render-canvas-hook on-render-canvas)
    (state/set-current-tool! canvas-id :brush)
    (frame/set-param! frame :krro.painting.javafx/overlay-gc overlay-gc)

    ;; 让 Canvas 对鼠标透明，所有事件由 StackPane 捕获
    (.setMouseTransparent main-canvas true)
    (.setMouseTransparent overlay-canvas true)


    ;; 阻止系统手势（在 StackPane 上）
    (doto stack
      (.setOnZoom (reify EventHandler (handle [_ e] (.consume e))))
      (.setOnRotate (reify EventHandler (handle [_ e] (.consume e))))
      (.setFocusTraversable false))

    ;; 设置画布尺寸
    (.setWidth main-canvas canvas-w)
    (.setHeight main-canvas canvas-h)
    (.setWidth overlay-canvas canvas-w)
    (.setHeight overlay-canvas canvas-h)

    (layer/auto-select-layer! canvas-id)
    (render-canvas-fn init-canvas-data nil)

    ;; 启动输入源
    (input/start! mouse-input)

    ;; 清理函数
    (fn []
      (hook/remove-hook! :krro.painting/render-canvas-hook on-render-canvas)
      (input/stop! mouse-input)
      (cleanup-preview-canvas frame))))

;; ── 组件定义 ───────────────────────────────────
(def create-canvas
  (make-component [:krro.painting/canvas-id
                   :krro.painting/canvas-width
                   :krro.painting/canvas-height]
                  (fn []
                    (let [main-canvas   (doto (Canvas.) (.setId "painting-canvas"))
                          overlay       (doto (Canvas.) (.setId "painting-overlay"))
                          stack         (StackPane.)]
                      (let [children (.getChildren stack)]
                        (.add children main-canvas)
                        (.add children overlay))
                      stack))
                  (fn [^StackPane stack old-props new-props f]
                    (let [canvas-id (:krro.painting/canvas-id new-props)
                          canvas-w (:krro.painting/canvas-width new-props 800)
                          canvas-h (:krro.painting/canvas-height new-props 600)
                          children (.getChildren stack)
                          main-canvas (.get children 0)
                          overlay-canvas (.get children 1)]
                      (if (nil? old-props)
                        (start-canvas-session stack main-canvas overlay-canvas canvas-id canvas-w canvas-h f)
                        (when-let [runtime (state/canvas-runtime canvas-id)]
                          (let [[w h] (pc/canvas-size canvas-id)
                                preview (state/preview-canvas runtime)
                                viewport (vp/get-viewport f)
                                upload-fn (frame/param f spec/update-fn-key)]
                            (when upload-fn
                              (upload-fn preview w h viewport)))))))))