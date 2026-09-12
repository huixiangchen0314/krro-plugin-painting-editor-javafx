(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.upload
  (:require
    [taoensso.timbre :as log]
    [taoensso.tufte :refer [p profile]]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.viewport]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.upload :as upload])
  (:import
   (javafx.application Platform)
   (javafx.scene.canvas Canvas)
   (javafx.scene.image PixelWriter)
   (top.kzre.krro.plugin.painting.core.viewport ViewPort)
   (top.kzre.krro.plugin.painting.editor.javafx.canvas Upload)
   (top.kzre.krro.util.tile TiledCanvas)))
(Upload/setCheckerGrid pc/global-tile-size)

(defn make-uploader [^Canvas fx-canvas]
  (let [^PixelWriter pixel-writer (.getPixelWriter (.getGraphicsContext2D fx-canvas))
        pending-params (atom nil)
        running? (atom false)
        last-upload-time (atom 0)
        latest-params (atom nil)   ;; 保存完整参数 [snapshot w h viewport]
        min-interval (int (/ 1000 120))                     ;; 最高 120px
        delayed-task (atom nil)
        int-pixels (atom nil)]

    (letfn [(upload-task []
              (let [[params _] (swap-vals! pending-params (fn [_] nil))
                    now (System/currentTimeMillis)]
                (profile {:id :kroo.painting/javafx-upload-task}
                         (if params
                           (let [[snapshot image-w image-h viewport] params]
                             (if (< (- now @last-upload-time) min-interval)
                               ;; 限流：保存完整参数，安排延迟任务
                               (do
                                 (reset! latest-params params)
                                 (when @delayed-task (Platform/runLater @delayed-task))  ;; 取消旧任务
                                 (reset! delayed-task
                                         (Platform/runLater
                                           (fn []
                                             (when-let [snap-params @latest-params]
                                               (reset! pending-params snap-params)
                                               (when (compare-and-set! running? false true)
                                                 (Platform/runLater upload-task))))))
                                 (reset! running? false))
                               ;; 正常上传
                               (p :upload-javafx-canvas
                                  (do
                                    (reset! last-upload-time now)
                                    (reset! latest-params nil)
                                    (try
                                      (let [canvas-w (.getWidth fx-canvas)
                                            canvas-h (.getHeight fx-canvas)]
                                        (if (and canvas-w canvas-h (pos? (int canvas-w)) (pos? (int canvas-h)))
                                          (let [offset-x (int (or (:offset-x viewport) 0))
                                                offset-y (int (or (:offset-y viewport) 0))
                                                zoom     (double (or (:zoom viewport) 1.0))
                                                viewport-w (int canvas-w)
                                                viewport-h (int canvas-h)
                                                len (* viewport-w viewport-h)]
                                            (if-let [pixels @int-pixels]
                                              (when-not (= (alength pixels) len)
                                                (reset! int-pixels (int-array len)))
                                              (reset! int-pixels (int-array len)))
                                            (Upload/sampleViewport snapshot viewport-w viewport-h offset-x offset-y zoom image-w image-h @int-pixels)
                                            (Upload/writePixels pixel-writer viewport-w viewport-h @int-pixels)

                                            (log/debug "Uploaded canvas to JavaFX"))
                                          (log/warn "Canvas size is zero or null, skipping upload")))
                                      (catch Exception e
                                        (log/error e "Failed to upload canvas to JavaFX"))
                                      (finally
                                        (.clear ^TiledCanvas snapshot)
                                        (reset! running? false)
                                        (when @pending-params
                                          (Platform/runLater upload-task))))))))
                           (reset! running? false)))))]

      (fn [^TiledCanvas canvas {:keys [width height]} ^ViewPort viewport]
        (let [snapshot (.copy canvas)]
          (reset! pending-params [snapshot width height viewport])
          (when (compare-and-set! running? false true)
            (Platform/runLater upload-task)))))))