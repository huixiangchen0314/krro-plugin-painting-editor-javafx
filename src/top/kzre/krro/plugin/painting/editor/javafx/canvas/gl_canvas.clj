(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.util.channel :as ch]
    [top.kzre.krro.core.util.promise :as promise :refer [plet>]]
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
    [top.kzre.krro.plugin.painting.editor.javafx.graph :as graph]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (com.huskerdev.grapl.gl GLContext GLProfile)
    (com.huskerdev.openglfx.canvas GLCanvas GLCanvas$Builder GLCanvas$Builder$ContextDescription$New)
    (com.huskerdev.openglfx.canvas.events GLRenderEvent)
    (com.huskerdev.openglfx.lwjgl LWJGLExecutor)
    (java.lang AutoCloseable)
    (java.util Iterator Set)
    (java.util.concurrent Callable)
    (java.util.function Consumer)
    (javafx.application Platform)
    (javafx.beans.value ChangeListener)
    (javafx.event EventHandler)
    (javafx.scene.canvas Canvas)
    (javafx.scene.layout StackPane)
    (org.lwjgl.opengl GL32)
    (top.kzre.krro.canvas.gl.resource GLTexture)
    (top.kzre.krro.canvas.gl.tile AbstractGLTiledTexture$GLTextureTileData TiledTextureBlitter)
    (top.kzre.krro.core.util SerialExecutor)
    (top.kzre.krro.plugin.painting.editor.javafx.glfx GLRuntime)
    (top.kzre.krro.util.tile Tile TiledCanvas)))

;; ── 帧数据记录 ─────────────────────────────────

(defrecord FrameInfo
  [^GLTexture texture
   ^long layer
   ^TiledCanvas canvas
   ^long fence])

(defn- discard-frame!
  "帧未被消费（channel 丢弃 / dispose 清理），GPU 从未使用过 texture。
  直接删除 fence + close canvas，无需 fence wait。"
  [^FrameInfo finfo]
  (when finfo
    (let [f (.fence finfo)]
      (when (not= 0 f)
        (GL32/glDeleteSync f)))
    (when-let [^TiledCanvas c (.canvas finfo)]
      (when-not (.isClosed c)
        (.close c)))))

(defn- consume-frame!
  "帧已被 blit，GPU 可能还在异步读 texture。
  必须插 blit-fence 等 GPU 读完再 close（close 内部 dispatch 到离屏线程删 texture）。"
  [^FrameInfo finfo]
  (when finfo
    ;; 离屏 fence 已在 onRenderEvent 里 glWaitSync 过了，这里只删
    (let [f (.fence finfo)]
      (when (not= 0 f)
        (GL32/glDeleteSync f)))
    (when-let [^TiledCanvas c (.canvas finfo)]
      (when-not (.isClosed c)
        ;; ★ blit-fence：等 GPU 完成对 texture 的采样
        (GL32/glFinish)
        ;; ★ close dispatch 到离屏 GL 线程删 texture
        ;; 此时 GPU 已确认不再引用该 texture ✓
        (.close c)))))

;; ── 离屏渲染结果 → 纹理信息 ────────────────────

(defn- canvas->tex
  [^TiledCanvas canvas]
  (let [^Set ts (.getTiles canvas)]
    (when-not (.isEmpty ts)
      (let [^Iterator it (.iterator ts)
            tk (long (.next it))
            tile (.getTile canvas tk)
            ^AbstractGLTiledTexture$GLTextureTileData data
            (.queryData ^Tile tile AbstractGLTiledTexture$GLTextureTileData)]
        (when data
          {:texture ^GLTexture (.getTexture data)
           :layer   (.getLayer data)
           :canvas  canvas})))))

;; ── GLCanvas 创建 ──────────────────────────────

(defn- make-gl-canvas
  "创建与离屏 runtime 共享的 GLCanvas。fps=0——手动 repaint。"
  ^GLCanvas []
  (let [^GLRuntime runtime (GLRuntime/offscreen)
        ^GLContext ctx (.getContext runtime)]
    (-> (GLCanvas$Builder.)
        (.setExecutor LWJGLExecutor/LWJGL_MODULE)
        (.setFps (double 0))
        (.setContextDescription
          (-> (GLCanvas$Builder$ContextDescription$New.)
              (.setShareWith ctx)
              (.setProfile GLProfile/CORE)))
        (.build))))

;; ── 画布会话创建 ────────────────────────────────

(defn- start-canvas-session [^StackPane stack
                             ^GLCanvas gl-canvas
                             ^Canvas overlay-canvas
                             canvas-id
                             frame]
  (store/reg-canvas-store canvas-id)
  (let [render-task-id   (str canvas-id "-" (frame/frame-id frame))
        init-canvas-data (pc/canvas-data! canvas-id)
        overlay-gc       (.getGraphicsContext2D overlay-canvas)
        ^GLRuntime runtime (GLRuntime/offscreen)
        ^SerialExecutor gl-exec (.getExecutor runtime)
        blitter (TiledTextureBlitter. pc/global-tile-size)

        ;; ★ 帧管线：capacity=2，满了自动丢最旧帧
        ;; onDispose 用 discard-frame! —— 丢弃的帧 GPU 未使用，直接释放
        frame-chan (ch/chan 2 discard-frame!)

        ;; ★ repaint 节流：capacity=1，多余的 repaint 请求被合并
        repaint-chan (ch/chan 1)

        ^AutoCloseable scheduler
        (scheduler-impl/make-scheduler
          {:backend :gl
           :gl-composite-context-builder
           (gl/glfx-composite-context-builder)})]

    ;; ★ GLCanvas 渲染回调——运行在 OpenGLFX 的 GL 线程，上下文已 current。
    ;; poll 非阻塞：有帧就 blit，无帧就跳过。
    (.addOnRenderEvent
      gl-canvas
      (reify Consumer
        (accept [_ event]
          (when-let [^FrameInfo finfo (ch/<| frame-chan)]
            ;; 1. 等离屏侧 GPU 写入完成
            (let [fence (.fence finfo)]
              (when (not= 0 fence)
                (GL32/glWaitSync fence 0 GL32/GL_TIMEOUT_IGNORED)))
            ;; 2. blit（GPU 读源 texture）
            ;(log/info "stack size: "
            ;          "\n width: " (.getWidth stack)
            ;          "\n height: " (.getHeight stack))
            (.blit blitter
                   (.texture finfo)
                   (.layer finfo)
                   (.getWidth gl-canvas)
                   (.getHeight gl-canvas)
                   (.-fbo ^GLRenderEvent event)
                   (.-width ^GLRenderEvent event)
                   (.-height ^GLRenderEvent event))
            ;; 3. ★ fence + 延迟释放，确保 GPU 读完再删 texture
            (consume-frame! finfo)))))

    (.addOnDisposeEvent
      gl-canvas
      (reify Consumer
        (accept [_ _]
          ;; ★ 清理残留帧（discard-frame! 直接释放，无 fence 开销）
          (ch/clear! frame-chan)
          (.close blitter))))

    (let [on-event (fn [ev]
                     (rf/dispatch store/app-id [:canvas/set-cursor-position canvas-id ev])
                     (rf/dispatch store/app-id [:tool/dispatch-event canvas-id ev frame]))

          render-canvas-fn
          (fn [{:keys [layers width height current-layer-id] :as _canvas-data}
               changes]
            (let [viewport   (vp/get-viewport frame)
                  viewport-w (.getWidth  gl-canvas)
                  viewport-h (.getHeight gl-canvas)]
              (plet>
                [canvas
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

                 fence
                 (promise/from-completable-future
                   (.submit gl-exec
                            ^Callable
                            (fn []
                              (GL32/glFlush)
                              (GL32/glFenceSync
                                GL32/GL_SYNC_GPU_COMMANDS_COMPLETE 0))))]

                (when (and canvas (not (.isClosed canvas)))
                  (when-let [tex-info (canvas->tex canvas)]
                    (let [^FrameInfo frame
                          (->FrameInfo (:texture tex-info)
                                       (:layer tex-info)
                                       (:canvas tex-info)
                                       (long fence))]
                      ;; ★ ch/>| 提交到离屏 GL 线程执行
                      ;; 丢弃回调 discard-frame! 里的 glDeleteSync 必须在 GL 线程
                      (.submit gl-exec
                               ^Callable
                               (fn []
                                 (ch/>| frame-chan frame)
                                 (ch/>| repaint-chan :repaint)
                                 (Platform/runLater
                                   (reify Runnable
                                     (run [_]
                                       (when (ch/<| repaint-chan)
                                         (.repaint gl-canvas)))))))))))))

          force-rerender-canvas! (fn []
                                   (render-canvas-fn
                                     (pc/canvas-data! canvas-id)
                                     [(viewport/refreshed)]))
          mouse-input (pointer/make-pointer-input stack on-event)

          on-render-canvas
          (fn [cid canvas-data changes]
            (when (= cid canvas-id)
              (render-canvas-fn canvas-data changes)))

          resize-listener (reify ChangeListener
                            (changed [_ _ _ _]
                              (force-rerender-canvas!)))]

      (hook/add-hook! :krro.painting/render-canvas-hook on-render-canvas)
      (state/set-current-tool! canvas-id :brush)
      (frame/set-param! frame :krro.painting/overlay-graph-context
                        (graph/make-javafx-graphics overlay-gc))

      ;; 鼠标事件交给 StackPane 捕获
      (.setMouseTransparent gl-canvas true)
      (.setMouseTransparent overlay-canvas true)

      (.addListener (.widthProperty stack) resize-listener)
      (.addListener (.heightProperty stack) resize-listener)

      (doto stack
        (.setOnZoom (reify EventHandler (handle [_ e] (.consume e))))
        (.setOnRotate (reify EventHandler (handle [_ e] (.consume e))))
        (.setFocusTraversable false))

      (layer/auto-select-layer! canvas-id)
      (render-canvas-fn init-canvas-data [(viewport/refreshed)])

      (input/start! mouse-input)

      ;; 清理
      (fn []
        (.close scheduler)
        (ch/close! frame-chan)
        (ch/close! repaint-chan)
        (.dispose gl-canvas)
        (store/unreg-canvas-store canvas-id)
        (hook/remove-hook! :krro.painting/render-canvas-hook on-render-canvas)
        (input/stop! mouse-input)
        (.removeListener (.widthProperty stack) resize-listener)
        (.removeListener (.heightProperty stack) resize-listener)))))


(def create-gl-canvas
  (make-component [:krro.painting/canvas-id]
                  (fn []
                    (let [stack     (StackPane.)
                          ^GLCanvas gl-canvas (make-gl-canvas)
                          overlay   (Canvas.)]
                      (.setId gl-canvas "painting-gl-canvas")
                      (.setId overlay   "painting-overlay")

                      (.bind (.widthProperty  overlay) (.widthProperty  stack))
                      (.bind (.heightProperty overlay) (.heightProperty stack))

                      (doto stack
                        (.setMinWidth 0)
                        (.setMinHeight 0))

                      (doto (.getChildren stack)
                        (.add gl-canvas)
                        (.add overlay))
                      stack))
                  (fn [^StackPane stack old-props new-props f]
                    (let [canvas-id (:krro.painting/canvas-id new-props)]
                      (when (not= canvas-id (:krro.painting/canvas-id old-props))
                        (let [children         (.getChildren stack)
                              ^GLCanvas gl-canvas      (.get children 0)
                              ^Canvas overlay-canvas   (.get children 1)]
                          (start-canvas-session stack gl-canvas overlay-canvas canvas-id f)))))))