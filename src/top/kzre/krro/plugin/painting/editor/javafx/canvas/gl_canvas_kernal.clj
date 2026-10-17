(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas-kernal
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.core.util.channel :refer [chan  <| >| close! clear!]]
    [top.kzre.krro.core.util.promise :as promise :refer [plet>]]
    [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport]
    [top.kzre.krro.plugin.painting.core.input :as input]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.schedule.executor :as executor]
    [top.kzre.krro.plugin.painting.core.schedule.scheduler-impl :as scheduler-impl]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl :as gl]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.util :as util]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.protocol :refer [ICanvasKernal]])
  (:import
   (com.huskerdev.grapl.gl GLContext GLProfile)
   (com.huskerdev.openglfx.canvas GLCanvas GLCanvas$Builder GLCanvas$Builder$ContextDescription$New)
   (com.huskerdev.openglfx.canvas.events GLRenderEvent GLReshapeEvent)
   (com.huskerdev.openglfx.lwjgl LWJGLExecutor)
   (java.lang AutoCloseable)
   (java.util.concurrent Callable)
   (java.util.function Consumer)
   (javafx.application Platform)
   (javafx.scene.canvas Canvas)
   (javafx.scene.layout StackPane)
   (org.lwjgl.opengl GL32)
   (top.kzre.krro.canvas.gl.tile TiledTextureBlitter)
   (top.kzre.krro.core.util SerialExecutor)
   (top.kzre.krro.plugin.painting.editor.javafx.glfx FrameDraw GLRuntime)))



;; ═══════════════════════════════════════════════
;; 状态
;; ═══════════════════════════════════════════════

(defn- make-state []
  (atom {:layers           nil
         :current-layer-id nil
         :image-width      0
         :image-height     0
         :viewport         nil
         :closed           false}))

;; ═══════════════════════════════════════════════
;; 渲染请求
;; ═══════════════════════════════════════════════

(defn- request-render!
  [{:keys [frame-chan
           repaint-chan
           gl-runtime
           gl-canvas
           scheduler
           render-task-id
           state-atom]
    :as _kernal} changes]
  (let [{:keys [layers current-layer-id
                image-width image-height
                viewport]}
        @state-atom
        ^SerialExecutor gl-exec (.getExecutor ^GLRuntime gl-runtime)
        viewport-w (.getWidth  gl-canvas)
        viewport-h (.getHeight gl-canvas)]
    (plet>
      [canvas
       (executor/submit!
         render-task-id
         scheduler
         layers
         :changes          changes
         :image-width      (if (pos? image-width)
                             image-width
                             viewport-w)
         :image-height     (if (pos? image-height)
                             image-height
                             viewport-h)
         :current-layer-id current-layer-id
         :viewport         viewport
         :viewport-w       viewport-w
         :viewport-h       viewport-h)

       _
       (promise/from-completable-future
         (.submit
           gl-exec
           ^Callable
           (fn []
             (GL32/glFlush)
             (let [fence (long (GL32/glFenceSync
                                 GL32/GL_SYNC_GPU_COMMANDS_COMPLETE 0))]
               (when (and canvas (not (.isClosed canvas)))
                 (when-let [tex-info (util/canvas->tex canvas)]
                   (let [frame
                         (FrameDraw.
                           (:canvas tex-info)
                           (:texture tex-info)
                           (:layer tex-info)
                           (long viewport-w)
                           (long viewport-h)
                           fence)]
                     (>| frame-chan frame))))))))]
      (>| repaint-chan :dirty)
      (Platform/runLater
        (reify Runnable
          (run [_]
            (when (<| repaint-chan)
              (.repaint gl-canvas))))))))

;; ═══════════════════════════════════════════════
;; 协议实现
;; ═══════════════════════════════════════════════

(defrecord GLCanvasKernalImpl
  [
   ;; 显示相关
   ^GLCanvas gl-canvas
   ^Canvas overlay-canvas
   ^StackPane fx-container
   ^TiledTextureBlitter blitter
   ;; 合成相关
   ^GLRuntime gl-runtime
   scheduler
   render-task-id
   frame-chan
   repaint-chan
   ;; 业务相关
   on-event
   mouse-input
   ;; 状态
   state-atom]

  ICanvasKernal

  (set-layers! [_ layers]
    (swap! state-atom assoc :layers layers))

  (set-current-layer-id! [_ layer-id]
    (swap! state-atom assoc :current-layer-id layer-id))

  (set-image-size! [_ width height]
    (swap! state-atom assoc
           :image-width  width
           :image-height height))

  (set-viewport! [_ viewport]
    (swap! state-atom assoc :viewport viewport))

  (overlay-gc [_]
    (.getGraphicsContext2D overlay-canvas))

  (repaint [this changes]
    (request-render! this changes))

  (close [_]
    (let [[old _] (swap-vals! state-atom assoc :closed true)]
      (when-not (:closed old)
        (doseq [step [(when mouse-input #(input/stop! mouse-input))
                      #(clear! frame-chan)
                      #(close! frame-chan)
                      #(close! repaint-chan)
                      (when scheduler #(.close ^AutoCloseable scheduler))
                      (when gl-canvas #(.dispose ^GLCanvas gl-canvas))]]
          (when step
            (try
              (step)
              (catch Throwable t
                (log/error t "kernal close step failed")))))))))

;; ═══════════════════════════════════════════════
;; GLCanvas 创建
;; ═══════════════════════════════════════════════

(defn- make-gl-canvas
  "创建与离屏 runtime 共享的 GLCanvas。fps=0——手动 repaint。"
  ^GLCanvas [^GLRuntime gl-runtime]
  (let [^GLContext ctx (.getContext gl-runtime)]
    (-> (GLCanvas$Builder.)
        (.setExecutor LWJGLExecutor/LWJGL_MODULE)
        (.setFps (double 0))
        (.setContextDescription
          (-> (GLCanvas$Builder$ContextDescription$New.)
              (.setShareWith ctx)
              (.setProfile GLProfile/CORE)))
        (.build))))

(defn- build-layout
  [^GLRuntime gl-runtime]
  (let [fx-stack     (StackPane.)
        ^GLCanvas gl-canvas (make-gl-canvas gl-runtime)
        overlay      (Canvas.)]
    (.bind (.widthProperty  overlay) (.widthProperty  fx-stack))
    (.bind (.heightProperty overlay) (.heightProperty fx-stack))

    (doto fx-stack
      (.setMinWidth 0)
      (.setMinHeight 0))

    (doto (.getChildren fx-stack)
      (.add gl-canvas)
      (.add overlay))

    {:container      fx-stack
     :gl-canvas      gl-canvas
     :overlay-canvas overlay}))

;; ═══════════════════════════════════════════════
;; 构造
;; ═══════════════════════════════════════════════

(defn build-kernal
  [^GLRuntime gl-runtime
   {:keys [on-event]}]
  (let [{:keys [container gl-canvas overlay-canvas]}
        (build-layout gl-runtime)

        render-task-id (str "gl-canvas-kernal-" (random-uuid))
        blitter        (TiledTextureBlitter. pc/global-tile-size)
        mouse-input    (pointer/make-pointer-input container on-event)

        scheduler      (scheduler-impl/make-scheduler
                         {:backend :gl
                          :gl-composite-context-builder
                          (gl/glfx-composite-context-builder)})

        frame-chan     (chan 2 (fn [^FrameDraw fd] (.discard fd)))
        repaint-chan   (chan 1)

        kernal
        (map->GLCanvasKernalImpl
          {:gl-canvas      gl-canvas
           :overlay-canvas overlay-canvas
           :fx-container   container
           :blitter        blitter
           :gl-runtime     gl-runtime
           :scheduler      scheduler
           :render-task-id render-task-id
           :frame-chan     frame-chan
           :repaint-chan   repaint-chan
           :on-event       on-event
           :mouse-input    mouse-input
           :state-atom     (make-state)})]

    ;; ── onRenderEvent：GLCanvas 的 GL 线程，消费最新一帧 ──
    (.addOnRenderEvent
      gl-canvas
      (reify Consumer
        (accept [_ event]
          (when-let [^FrameDraw fd (<| frame-chan)]
            (.consume fd blitter ^GLRenderEvent event)))))

    ;; ── onReshapeEvent：surface 尺寸变化，触发一次重渲染 ──
    (.addOnReshapeEvent
      gl-canvas
      (reify Consumer
        (accept [_ _]
          (request-render! kernal [(viewport/refreshed)]))))

    ;; ── onDisposeEvent：清理残留帧 + 关 blitter ──
    (.addOnDisposeEvent
      gl-canvas
      (reify Consumer
        (accept [_ _]
          (close! frame-chan)
          (.close blitter))))

    (input/start! mouse-input)

    {:container container
     :kernal    kernal}))