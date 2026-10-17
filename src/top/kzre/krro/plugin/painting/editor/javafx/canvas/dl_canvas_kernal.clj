(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas-kernal
  "基于 JavaFX Canvas 的画布会话内核。走软件合成路径——合成结果
   通过 Upload 采样到 JavaFX Canvas 的像素缓冲。无 GL 依赖。

   与 gl-canvas-kernal 实现同一协议——外部通过 ICanvasKernal 的
   方法操作，不感知底层是 GL 还是软件。"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport]
    [top.kzre.krro.plugin.painting.core.input :as input]
    [top.kzre.krro.plugin.painting.core.schedule.executor :as executor]
    [top.kzre.krro.plugin.painting.core.schedule.scheduler-impl :as scheduler-impl]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.protocol :refer [ICanvasKernal]]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.editor.javafx.input.pointer :as pointer])
  (:import
    (java.lang AutoCloseable)
    (javafx.beans.value ChangeListener)
    (javafx.scene.canvas Canvas)
    (javafx.scene.layout StackPane)
    (top.kzre.krro.util.tile TiledCanvas)))

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
  [{:keys [main-canvas scheduler render-task-id upload-fn state-atom]
    :as _kernal} changes]
  (let [{:keys [layers current-layer-id image-width image-height viewport]}
        @state-atom
        viewport-w (.getWidth  main-canvas)
        viewport-h (.getHeight main-canvas)
        image-w    (if (pos? image-width)  image-width  viewport-w)
        image-h    (if (pos? image-height) image-height viewport-h)]
    (promise/tap
      (executor/submit!
        render-task-id scheduler layers
        :changes          changes
        :image-width      image-w
        :image-height     image-h
        :current-layer-id current-layer-id
        :viewport         viewport
        :viewport-w       viewport-w
        :viewport-h       viewport-h)
      (fn [^TiledCanvas c]
        (upload-fn c {:width image-w :height image-h} viewport)))))

;; ═══════════════════════════════════════════════
;; 协议实现
;; ═══════════════════════════════════════════════

(defrecord DlCanvasKernalImpl
  [
   ;; 显示相关
   ^Canvas    main-canvas
   ^Canvas    overlay-canvas
   ^StackPane fx-container
   upload-fn
   ;; 合成相关
   scheduler
   render-task-id
   ;; 业务相关
   on-event
   mouse-input
   resize-listener
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

  (close [{:keys [^StackPane fx-container mouse-input resize-listener
                  scheduler state-atom]
           :as _kernal}]
    (let [[old _] (swap-vals! state-atom assoc :closed true)]
      (when-not (:closed old)
        (doseq [step [(when mouse-input
                        #(input/stop! mouse-input))
                      (when (and resize-listener fx-container)
                        #(do
                           (.removeListener (.widthProperty  fx-container) ^ChangeListener resize-listener)
                           (.removeListener (.heightProperty fx-container) ^ChangeListener resize-listener)))
                      (when scheduler
                        #(.close ^AutoCloseable scheduler))]]
          (when step
            (try
              (step)
              (catch Throwable t
                (log/error t "dl-canvas kernal close step failed")))))))))

;; ═══════════════════════════════════════════════
;; 布局
;; ═══════════════════════════════════════════════

(defn- build-layout []
  (let [fx-stack (StackPane.)
        main     (Canvas.)
        overlay  (Canvas.)]
    (.setId main    "painting-canvas")
    (.setId overlay "painting-overlay")

    (.bind (.widthProperty  main)    (.widthProperty  fx-stack))
    (.bind (.heightProperty main)    (.heightProperty fx-stack))
    (.bind (.widthProperty  overlay) (.widthProperty  fx-stack))
    (.bind (.heightProperty overlay) (.heightProperty fx-stack))

    (doto fx-stack
      (.setMinWidth 0)
      (.setMinHeight 0))

    (doto (.getChildren fx-stack)
      (.add main)
      (.add overlay))

    {:container      fx-stack
     :main-canvas    main
     :overlay-canvas overlay}))

;; ═══════════════════════════════════════════════
;; 构造
;; ═══════════════════════════════════════════════

(defn build-kernal
  "构造软件合成画布会话内核。

   opts —— {:on-event (fn [ev] ...)}，输入事件回调。

   返回 {:container StackPane, :kernal DlCanvasKernalImpl}。"
  [{:keys [on-event]}]
  (let [{:keys [container main-canvas overlay-canvas]} (build-layout)

        render-task-id  (str "dl-canvas-kernal-" (random-uuid))
        upload-fn       (upload/make-uploader main-canvas)
        mouse-input     (pointer/make-pointer-input container on-event)

        ;; 软件合成——不走 GL
        scheduler       (scheduler-impl/make-scheduler
                          {:backend :default})

        kernal-ref      (atom nil)
        resize-listener (reify ChangeListener
                          (changed [_ _ _ _]
                            (when-let [k @kernal-ref]
                              (request-render! k [(viewport/refreshed)]))))

        kernal
        (map->DlCanvasKernalImpl
          {:main-canvas     main-canvas
           :overlay-canvas  overlay-canvas
           :fx-container    container
           :upload-fn       upload-fn
           :scheduler       scheduler
           :render-task-id  render-task-id
           :on-event        on-event
           :mouse-input     mouse-input
           :resize-listener resize-listener
           :state-atom      (make-state)})]

    (reset! kernal-ref kernal)

    (.addListener (.widthProperty  container) resize-listener)
    (.addListener (.heightProperty container) resize-listener)

    (input/start! mouse-input)

    {:container container
     :kernal    kernal}))