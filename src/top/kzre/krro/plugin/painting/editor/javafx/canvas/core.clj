(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.core
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.core.input :as input]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.layer-undo :as layer-undo]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.core]
    [top.kzre.krro.plugin.painting.core.core]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.tool.brush :as brush-tool]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]
    [top.kzre.krro.plugin.painting.editor.core.tool.viewport :as viewport-tool]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.overlay.core]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pen :as pen]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.animation AnimationTimer)
    (javafx.event EventHandler)
    (javafx.scene.canvas Canvas)
    (javafx.scene.layout StackPane)))

;; ── 工具动作处理 ──────────────────────────────
(defn- dispatch-tool-action
  [canvas-id f action current-tool current-layer current-state ctx drawing?]
  (case action
    :start
    (reset! drawing? true)

    :continue
    (reset! drawing? true)

    :no-replace
    (do
      (let [{:keys [_layer state]} (tp/commit! current-tool current-layer current-state ctx)]
        (when state (swap! state/canvas-runtimes assoc canvas-id state)))
      (layer/refresh-canvas-and-layer! canvas-id)
      (reset! drawing? false))

    :commit
    (do
      (let [{:keys [layer state]} (tp/commit! current-tool current-layer current-state ctx)]
        (when layer (layer-undo/commit-layer-undo! canvas-id layer current-state state)))

      (reset! drawing? false))

    :update
    (let [runtime (state/canvas-runtime canvas-id)
          ctx     (tp/make-context canvas-id f (pc/canvas-data! canvas-id))]
      (tp/draw-overlay! current-tool runtime ctx))

    :idle nil))

;; ── 事件分派（视口工具独立） ─────────────────
(defn- dispatch-event
  [canvas-id f ev drawing? upload-fn viewport-tool-instance]
  (let [btn (:mouse-button ev)]
    (if (= btn :middle)
      ;; 中键 → 视口工具
      (when-let [runtime (state/canvas-runtime canvas-id)]
        (let [data (pc/canvas-data! canvas-id)
              ctx  (tp/make-context canvas-id f data)
              action (tp/apply! viewport-tool-instance nil runtime ev ctx)]
          (when (= action :continue)
            (let [preview (state/preview-canvas runtime)
                  vp      (vp/get-viewport f)
                  [w h]   (pc/canvas-size canvas-id)]
              (upload-fn preview w h vp)))))
      ;; 其他事件 → 当前绘画工具
      (when-let [current-tool (state/current-tool canvas-id)]
        (when-let [current-layer (state/current-layer! canvas-id)]
          (let [runtime   (state/canvas-runtime canvas-id)
                data      (pc/canvas-data! canvas-id)
                ctx       (tp/make-context canvas-id f data)
                viewport  (vp/get-viewport f)
                logic     (vp/screen->logic viewport (:x ev) (:y ev))
                logic-ev  (assoc ev :x (:x logic) :y (:y logic))
                action    (tp/apply! current-tool current-layer runtime logic-ev ctx)]
            (dispatch-tool-action canvas-id f action current-tool current-layer runtime ctx drawing?)))))))

;; ── 画布会话创建 ─────────────────────────────
(defn- start-canvas-session [^StackPane stack ^Canvas main-canvas ^Canvas overlay-canvas canvas-id f]
  (let [[w h] (pc/canvas-size canvas-id)
        upload-fn (upload/make-uploader main-canvas)
        overlay-gc (.getGraphicsContext2D overlay-canvas)

        event-buffer (atom [])
        viewport-tool-instance (viewport-tool/make-viewport-tool)
        drawing? (atom false)

        ;; 动画循环（始终运行）
        timer (proxy [AnimationTimer] []
                (handle [_]
                  (try
                    ;; 消费缓冲事件
                    (when-let [events (seq @event-buffer)]
                      (swap! event-buffer empty)
                      (doseq [ev events]
                        (dispatch-event canvas-id f ev drawing? upload-fn viewport-tool-instance)))
                    ;; 绘制叠加层
                    (when-let [current-tool (state/current-tool canvas-id)]
                      (let [runtime (state/canvas-runtime canvas-id)
                            ctx     (tp/make-context canvas-id f (pc/canvas-data! canvas-id))]
                        (tp/draw-overlay! current-tool runtime ctx)))
                    ;; 预览更新
                    (when @drawing?
                      (when-let [current-tool (state/current-tool canvas-id)]
                        (when-let [current-layer (state/current-layer! canvas-id)]
                          (let [runtime (state/canvas-runtime canvas-id)
                                data    (pc/canvas-data! canvas-id)
                                ctx     (tp/make-context canvas-id f data)
                                {:keys [layer state]} (tp/preview! current-tool current-layer runtime ctx)]
                            (when layer
                              (layer/replace-layer! canvas-id layer))
                            (when state
                              (swap! state/canvas-runtimes assoc canvas-id state))))))
                    (catch Exception e
                      (log/error e "Render loop error")))))

        mouse-input (pointer/make-pointer-input stack event-buffer)
        pen-input (try
                    (pen/make-pen-input main-canvas event-buffer)   ;; 注意：不再传递 canvas
                    (catch Exception e
                      (log/warn e "Cannot create pen input")
                      nil))]

    (state/set-current-tool! canvas-id (brush-tool/make-brush))
    (frame/set-param! f :krro.painting.javafx/overlay-gc overlay-gc)

    ;; 让 Canvas 对鼠标透明，所有事件由 StackPane 捕获
    (.setMouseTransparent main-canvas true)
    (.setMouseTransparent overlay-canvas true)

    ;; 启动输入源
    (input/start! mouse-input)
    (when pen-input
      (try
        (input/start! pen-input)
        (catch Exception e
          (log/warn e "Failed to start pen input, continuing without pen"))))

    (.start timer)

    ;; 阻止系统手势（在 StackPane 上）
    (doto stack
      (.setOnZoom (reify EventHandler (handle [_ e] (.consume e))))
      (.setOnRotate (reify EventHandler (handle [_ e] (.consume e))))
      (.setFocusTraversable false))

    ;; 设置画布尺寸
    (.setWidth main-canvas 800)
    (.setHeight main-canvas 600)
    (.setWidth overlay-canvas 800)
    (.setHeight overlay-canvas 600)

    (layer/auto-select-layer! canvas-id)

    (let [runtime (state/canvas-runtime canvas-id)
          preview (state/preview-canvas runtime)]
      (upload-fn preview w h (vp/get-viewport f)))

    (frame/set-param! f spec/update-fn-key upload-fn)

    ;; 清理函数
    (fn []
      (.stop timer)
      (input/stop! mouse-input)
      (when pen-input (input/stop! pen-input))
      (frame/remove-param! f spec/update-fn-key))))

;; ── 组件定义 ───────────────────────────────────
(def create-canvas
  (make-component [:krro.painting/canvas-id]
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
                          children (.getChildren stack)
                          main-canvas (.get children 0)
                          overlay-canvas (.get children 1)]
                      (if (nil? old-props)
                        (start-canvas-session stack main-canvas overlay-canvas canvas-id f)
                        (when-let [runtime (state/canvas-runtime canvas-id)]
                          (let [[w h] (pc/canvas-size canvas-id)
                                preview (state/preview-canvas runtime)
                                viewport (vp/get-viewport f)
                                upload-fn (frame/param f spec/update-fn-key)]
                            (when upload-fn
                              (upload-fn preview w h viewport)))))))))