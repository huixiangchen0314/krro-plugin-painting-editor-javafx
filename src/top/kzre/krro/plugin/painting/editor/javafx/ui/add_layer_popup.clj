(ns top.kzre.krro.plugin.painting.editor.javafx.ui.add-layer-popup
  (:require
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.event EventHandler)
    (javafx.scene.control Button)
    (javafx.scene.layout VBox)
    (javafx.stage Popup)))

(defn- create-popup-content [canvas-id ^Popup popup]
  (let [content (doto (VBox.) (.setSpacing  5.0)
                  (.setStyle "-fx-padding: 10; -fx-background-color: white; -fx-border-color: gray;"))
        children (.getChildren content)]
    (.add children (doto (Button. "光栅图层")
                     (.setOnAction (reify EventHandler
                                     (handle [_ e]
                                       (rf/dispatch store/app-id [:new-raster-layer canvas-id])
                                       (.hide popup))))))
    (.add children (doto (Button. "矢量图层")
                     (.setOnAction (reify EventHandler
                                     (handle [_ e]
                                       (rf/dispatch  store/app-id [:new-vector-layer canvas-id])
                                       (.hide popup))))))
    content))

(def create-add-layer-popup
  (make-component [:krro.painting/canvas-id]
                  (fn [] (doto (Button. "＋") (.setId "add-layer-btn")))
                  (fn [^Button btn _old-props new-props frame]
                    (let [canvas-id (:krro.painting/canvas-id new-props)
                          popup (Popup.)]
                      (.setAutoHide popup true)
                      (.add (.getContent popup) (create-popup-content canvas-id popup))
                      (.setOnAction btn (reify EventHandler
                                          (handle [_ e]
                                            (let [bounds (.localToScreen btn (.getBoundsInLocal btn))]
                                              (.show popup btn (.getMinX bounds) (.getMaxY bounds))))))))))