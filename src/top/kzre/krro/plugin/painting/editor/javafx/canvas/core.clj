(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.core
  (:require
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas-kernel :as dl-kernel]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas-kernel :as gl-kernel]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.painting-canvas :as painting-canvas])
  (:import (top.kzre.krro.plugin.painting.editor.javafx.glfx GLRuntime)))

(def create-canvas
  (painting-canvas/make-factory dl-kernel/build-kernel))

(def create-gl-canvas
  (painting-canvas/make-factory
    (fn [opts]
      (gl-kernel/build-kernel (GLRuntime/offscreen) opts))))