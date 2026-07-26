(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.upload
  (:require [top.kzre.krro.plugin.painting.core.viewport]
            [taoensso.timbre :as log])
  (:import (javafx.application Platform)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelWriter)
           (top.kzre.krro.plugin.painting.core.viewport ViewPort)
           (top.kzre.krro.plugin.painting.editor.javafx.canvas Upload)
           (top.kzre.krro.util.tile TiledCanvas)))

(defn make-uploader [^Canvas fx-canvas]
  (let [^PixelWriter pixel-writer (.getPixelWriter (.getGraphicsContext2D fx-canvas))]
    (fn [^TiledCanvas canvas w h ^ViewPort viewport]
      (Platform/runLater
        (fn []
          (try
            (let [canvas-w (.getWidth fx-canvas)
                  canvas-h (.getHeight fx-canvas)]
              (if (and canvas-w canvas-h (pos? (int canvas-w)) (pos? (int canvas-h)))
                (let [offset-x (int (or (:offset-x viewport) 0))
                      offset-y (int (or (:offset-y viewport) 0))
                      zoom     (double (or (:zoom viewport) 1.0))]
                  (Upload/upload canvas w h offset-x offset-y zoom pixel-writer (int canvas-w) (int canvas-h))
                  (log/debug (str "Uploaded canvas to JavaFX")))
                (log/warn "Canvas size is zero or null, skipping upload")))
            (catch Exception e
              (log/error e "Failed to upload canvas to JavaFX")))
          nil)))))