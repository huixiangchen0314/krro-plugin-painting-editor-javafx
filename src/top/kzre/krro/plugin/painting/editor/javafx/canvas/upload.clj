(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.upload
  (:require [top.kzre.krro.plugin.painting.core.viewport])
  (:import (javafx.application Platform)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelWriter)
           (top.kzre.krro.plugin.painting.core.viewport ViewPort)
           (top.kzre.krro.plugin.painting.editor.javafx.canvas Upload)))


(defn make-uploader
  [^Canvas fx-canvas]
  (let [^PixelWriter pixel-writer (.getPixelWriter (.getGraphicsContext2D fx-canvas))]
    (fn [^floats data w h ^ViewPort viewport]
      (Platform/runLater
        (fn []
          (let [canvas-w (int (.getWidth fx-canvas))
                canvas-h (int (.getHeight fx-canvas))]
            (Upload/upload data w h
                           (:offset-x viewport) (:offset-y viewport) (:zoom viewport)
                           pixel-writer canvas-w canvas-h))

          nil)))))