(ns top.kzre.krro.plugin.painting.editor.javafx.core
  (:require
   [top.kzre.krro.core.core :as krro]
   [top.kzre.krro.plugin.painting.core.core]
   [top.kzre.krro.plugin.painting.editor.core.core]
   [top.kzre.krro.plugin.painting.editor.javafx.canvas.core :as canvas]
   [top.kzre.krro.plugin.painting.editor.javafx.ui.add-layer-popup :as add-layer-popup]))


(krro/reg-plugin!
  {:name :krro.plugin/painting
   :mount
   (fn
     []
     (krro/reg-plugin!
       {:id      :krro.painting/canvas-tag
        :type    :krro.plugin/javafx-tag
        :tag     :krro.painting/canvas
        :handler canvas/create-canvas})
     (krro/reg-plugin!
       {:id      :krro.painting/add-layer-popup-tag
        :type    :krro.plugin/javafx-tag
        :tag     :krro.painting/add-layer-popup
        :handler add-layer-popup/create-add-layer-popup})
     )})