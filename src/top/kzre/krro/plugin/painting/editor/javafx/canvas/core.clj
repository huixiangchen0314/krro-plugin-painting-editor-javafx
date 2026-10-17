(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.core
  (:require
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas-kernal :as dl-kernal]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas-kernal :as gl-kernal]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.painting-canvas :as painting-canvas])
  (:import (top.kzre.krro.plugin.painting.editor.javafx.glfx GLRuntime)))

(def create-canvas
  (painting-canvas/make-factory dl-kernal/build-kernal))

(def create-gl-canvas
  (painting-canvas/make-factory
    (fn [opts]
      (gl-kernal/build-kernal (GLRuntime/offscreen) opts))))