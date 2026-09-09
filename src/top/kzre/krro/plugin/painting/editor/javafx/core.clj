(ns top.kzre.krro.plugin.painting.editor.javafx.core
  (:require
   [top.kzre.krro.core.core :as krro]
   [top.kzre.krro.plugin.painting.core.core]
   [top.kzre.krro.plugin.painting.editor.core.core]
   [top.kzre.krro.plugin.painting.editor.javafx.canvas.core :as canvas]
   [top.kzre.krro.plugin.painting.editor.javafx.ui.add-layer-popup :as add-layer-popup]
   [top.kzre.krro.plugin.painting.editor.javafx.ui.color-picker :as color-picker])
  (:import
    (top.kzre.krro.plugin.painting.editor.javafx.ui ColorWheel HSVRamp SVRect)))


(krro/reg-plugin!
  {:name :krro.plugin/painting
   :mount
   (fn
     []
     (krro/reg-plugin!
       {:id      :krro.painting/canvas-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/canvas
        :factory canvas/create-canvas})
     (krro/reg-plugin!
       {:id      :krro.painting/add-layer-popup-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/add-layer-popup
        :factory add-layer-popup/create-add-layer-popup})
     (krro/reg-plugin!
       {:id      :krro.painting/color-wheel-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/color-wheel
        :factory (color-picker/color-picker-component #(ColorWheel.))})
     (krro/reg-plugin!
       {:id      :krro.painting/hsv-ramp-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/hsv-ramp
        :factory (color-picker/color-picker-component #(HSVRamp.))})
     (krro/reg-plugin!
       {:id      :krro.painting/h-ramp-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/h-ramp
        :factory (color-picker/color-picker-component #(HSVRamp. 60 HSVRamp/HUE_VISIBLE))})
     (krro/reg-plugin!
       {:id      :krro.painting/sv-rect-tag
        :type    :krro.ui.javafx/tag
        :tag     :krro.painting/sv-rect
        :factory (color-picker/color-picker-component #(SVRect.))})
     )})