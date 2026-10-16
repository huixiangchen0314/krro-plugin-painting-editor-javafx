(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas
  (:require
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport]
    [top.kzre.krro.plugin.painting.core.core]
    [top.kzre.krro.plugin.painting.core.input :as input]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.core]
    [top.kzre.krro.plugin.painting.core.schedule.executor :as executor]
    [top.kzre.krro.plugin.painting.core.schedule.scheduler-impl :as scheduler-impl]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl :as gl]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.editor.javafx.graph :as graph]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.beans.value ChangeListener)
    (javafx.event EventHandler)
    (javafx.scene.canvas Canvas)
    (javafx.scene.layout StackPane)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ── 画布会话创建 ─────────────────────────────
(defn- start-canvas-session [^StackPane stack
                             ^Canvas main-canvas
                             ^Canvas overlay-canvas
                             canvas-id
                             frame]
  (store/reg-canvas-store canvas-id)
  (let [render-task-id (str canvas-id "-" (frame/frame-id frame))
        init-canvas-data (pc/canvas-data! canvas-id)
        upload-fn (upload/make-uploader main-canvas)
        overlay-gc (.getGraphicsContext2D overlay-canvas)
        scheduler (scheduler-impl/make-scheduler
                    {:backend :gl
                     :gl-composite-context-builder
                     (gl/glfx-composite-context-builder)})
        on-event (fn [ev]
                   (rf/dispatch store/app-id [:canvas/set-cursor-position canvas-id ev])
                   (rf/dispatch store/app-id [:tool/dispatch-event canvas-id ev frame]))
        render-canvas-fn
        (fn [{:keys [layers width height current-layer-id]
              :as canvas-data}
             changes]
          (let [viewport (vp/get-viewport frame)
                viewport-w (.getWidth main-canvas)
                viewport-h (.getHeight main-canvas)
                ]
            ;; ── 提交到 executor——克隆、set-layers!、渲染、合并全在 executor 内部
            (promise/tap
              (executor/submit!
                render-task-id
                scheduler
                layers
                :changes          changes
                :image-width      width
                :image-height     height
                :current-layer-id current-layer-id
                :viewport         viewport
                :viewport-w       viewport-w
                :viewport-h       viewport-h)
              (fn [^TiledCanvas c] (upload-fn c canvas-data viewport)))))
        ;; TODO reframe 事件完成
        force-rerender-canvas! (fn [] (render-canvas-fn
                                        (pc/canvas-data! canvas-id)
                                        [(viewport/refreshed)]))
        mouse-input (pointer/make-pointer-input stack on-event)

        on-render-canvas
        (fn [cid canvas-data changes]
          (when (= cid canvas-id)
            (render-canvas-fn canvas-data changes)))
        resize-listener (reify ChangeListener
                          (changed [_ _ old new]
                            (force-rerender-canvas!)))]

    (hook/add-hook! :krro.painting/render-canvas-hook on-render-canvas)
    (state/set-current-tool! canvas-id :brush)
    (frame/set-param! frame :krro.painting/overlay-graph-context (graph/make-javafx-graphics overlay-gc))
    ;; 让 Canvas 对鼠标透明，所有事件由 StackPane 捕获
    (.setMouseTransparent main-canvas true)
    (.setMouseTransparent overlay-canvas true)

    ;; 添加尺寸监听
    (.addListener (.widthProperty stack) resize-listener)
    (.addListener (.heightProperty stack) resize-listener)

    ;; 阻止系统手势（在 StackPane 上）
    (doto stack
      (.setOnZoom (reify EventHandler (handle [_ e] (.consume e))))
      (.setOnRotate (reify EventHandler (handle [_ e] (.consume e))))
      (.setFocusTraversable false))

    (layer/auto-select-layer! canvas-id)
    (render-canvas-fn init-canvas-data [(viewport/refreshed)])

    ;; 启动输入源
    (input/start! mouse-input)

    ;; 清理函数
    (fn []
      (.close scheduler)
      (store/unreg-canvas-store canvas-id)
      (hook/remove-hook! :krro.painting/render-canvas-hook on-render-canvas)
      (input/stop! mouse-input)
      (.removeListener (.widthProperty stack) resize-listener)
      (.removeListener (.heightProperty stack) resize-listener)
      )))

;; ── 组件定义 ───────────────────────────────────
(def create-canvas
  (make-component [:krro.painting/canvas-id]
                  (fn []
                    (let [stack (StackPane.)
                          main-canvas (Canvas.)
                          overlay (Canvas.)]
                      ;; 设置 ID（便于调试）
                      (.setId main-canvas "painting-canvas")
                      (.setId overlay "painting-overlay")

                      ;; 绑定 Canvas 尺寸到 StackPane 尺寸
                      (.bind (.widthProperty main-canvas) (.widthProperty stack))
                      (.bind (.heightProperty main-canvas) (.heightProperty stack))
                      (.bind (.widthProperty overlay) (.widthProperty stack))
                      (.bind (.heightProperty overlay) (.heightProperty stack))

                      (doto stack
                        (.setMinWidth 0)
                        (.setMinHeight 0))
                      ;; 将 Canvas 添加到 StackPane
                      (doto (.getChildren stack)
                        (.add main-canvas)
                        (.add overlay))
                      stack))
                  (fn [^StackPane stack old-props new-props f]
                    ;; 画布绑定到 canvas-id上
                    (let [canvas-id (:krro.painting/canvas-id new-props)]
                      (when (not= canvas-id (:krro.painting/canvas-id old-props))
                        (let [
                              children (.getChildren stack)
                              main-canvas (.get children 0)
                              overlay-canvas (.get children 1)]
                          (start-canvas-session stack main-canvas overlay-canvas canvas-id f)))))))