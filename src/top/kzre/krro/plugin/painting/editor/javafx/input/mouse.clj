(ns top.kzre.krro.plugin.painting.editor.javafx.input.mouse
  "JavaFX 平台输入源实现。将 JavaFX Canvas 上的鼠标事件转换为标准事件。"
  (:require [top.kzre.krro.plugin.painting.core.input :as input]
            [top.kzre.krro.plugin.painting.core.event :as event])
  (:import (javafx.event EventHandler)
           (javafx.scene.canvas Canvas)))

(defrecord MouseInput [pressed? ^Canvas canvas callback]
  input/IInputSource
  (start! [_this]
    (let [on-press (reify EventHandler
                     (handle [_ e]
                       (reset! pressed? true)
                       (callback (event/make-pointer-event :press (.getX e) (.getY e)))))
          on-drag  (reify EventHandler
                     (handle [_ e]
                       (when @pressed?
                         (callback (event/make-pointer-event :drag (.getX e) (.getY e))))))
          on-release (reify EventHandler
                       (handle [_ e]
                         (when @pressed?
                           (callback (event/make-pointer-event :release (.getX e) (.getY e)))
                           (reset! pressed? false))))]
      (.setOnMousePressed canvas on-press)
      (.setOnMouseDragged canvas on-drag)
      (.setOnMouseReleased canvas on-release)))

  (stop! [_this]
    (.setOnMousePressed canvas nil)
    (.setOnMouseDragged canvas nil)
    (.setOnMouseReleased canvas nil)
    (reset! pressed? false)))

(defn make-mouse-input
  "创建 JavaFX 鼠标输入源。"
  [canvas callback]
  (->MouseInput (atom false) canvas callback))