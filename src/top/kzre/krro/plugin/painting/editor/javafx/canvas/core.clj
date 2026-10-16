(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.core
  (:require
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.util.re-export :refer [re-export]]
    [top.kzre.krro.plugin.painting.core.core]
    [top.kzre.krro.plugin.painting.core.project.core]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas]))

(re-export
  [top.kzre.krro.plugin.painting.editor.javafx.canvas.dl-canvas
   :refer [create-canvas]])


(re-export
  [top.kzre.krro.plugin.painting.editor.javafx.canvas.gl-canvas
   :refer [create-gl-canvas]])