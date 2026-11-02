(ns top.kzre.krro.plugin.painting.editor.javafx.input.pointer
  "JavaFX 平台指针输入源（缓冲模式）：所有事件转换为标准事件并追加到原子缓冲区。"
  (:require [top.kzre.krro.plugin.painting.core.event :as event]
            [top.kzre.krro.plugin.painting.editor.javafx.input.protocol :as input])
  (:import (javafx.event EventHandler)
           (javafx.scene.input MouseButton)))

(defn- javafx-button->keyword
  [^MouseButton btn]
  (cond
    (= btn MouseButton/PRIMARY)  :left
    (= btn MouseButton/SECONDARY) :right
    (= btn MouseButton/MIDDLE)   :middle
    :else nil))

(defn- event->modifiers
  [e]
  {:shift (.isShiftDown e)
   :ctrl (.isControlDown e)
   :alt (.isAltDown e)})


(defrecord PointerBuffered [pressed?
                            fx-pane
                            on-event
                            current-button]
  input/IInputSource
  (start! [_]
    (let [on-press (reify EventHandler
                     (handle [_ e]
                       (reset! pressed? true)
                       (let [btn (javafx-button->keyword (.getButton e))]
                         (reset! current-button btn)

                         (on-event
                                (event/make-pointer-event :press (.getX e) (.getY e)
                                                          :mouse-button btn
                                                          :modifiers (event->modifiers e))))))
          on-drag  (reify EventHandler
                     (handle [_ e]
                       (when @pressed?
                         (on-event
                                (event/make-pointer-event :drag (.getX e) (.getY e)
                                                          :mouse-button @current-button
                                                          :modifiers (event->modifiers e))))))
          on-move (reify EventHandler
                    (handle [_ e]
                      (on-event
                             (event/make-pointer-event :move (.getX e) (.getY e)
                                                       :modifiers (event->modifiers e)))))
          on-release (reify EventHandler
                       (handle [_ e]
                         (when @pressed?
                           (let [btn (javafx-button->keyword (.getButton e))]
                             (on-event
                                    (event/make-pointer-event :release (.getX e) (.getY e)
                                                              :mouse-button btn
                                                              :modifiers (event->modifiers e)))
                             (reset! pressed? false)
                             (reset! current-button nil)))))
          on-scroll (reify EventHandler
                      (handle [_ e]
                        (when-not (.isDirect e)
                          (on-event
                                 (event/make-pointer-event :scroll (.getX e) (.getY e)
                                                           :delta-x (.getDeltaX e) :delta-y (.getDeltaY e)
                                                           :mouse-button :middle
                                                           :modifiers (event->modifiers e))))))]
      (.setOnMousePressed fx-pane on-press)
      (.setOnMouseDragged fx-pane on-drag)
      (.setOnMouseReleased fx-pane on-release)
      (.setOnMouseMoved fx-pane on-move)
      (.setOnScroll fx-pane on-scroll))
    nil)

  (stop! [_]
    (.setOnMousePressed fx-pane nil)
    (.setOnMouseDragged fx-pane nil)
    (.setOnMouseReleased fx-pane nil)
    (.setOnMouseMoved fx-pane nil)
    (.setOnScroll fx-pane nil)
    (reset! pressed? false)
    (reset! current-button nil)))

(defn make-pointer-input
  "创建缓冲式鼠标输入源。事件将追加到 on-event 原子中。"
  [fx-pane on-event]
  (map->PointerBuffered {:fx-pane fx-pane :on-event on-event
                         :pressed? (atom false)
                         :current-button (atom nil)}))