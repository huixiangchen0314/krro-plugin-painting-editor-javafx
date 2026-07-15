(ns top.kzre.krro.plugin.painting.editor.javafx.core
  (:require [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.painting.editor.core.core]
            [top.kzre.krro.plugin.painting.editor.javafx.canvas.core :as canvas]))

(defn init
  []
  (plugin/register-plugin!
    {:id      :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas}))


(plugin/register-plugin! {:name :krro.plugin/painting :init init})