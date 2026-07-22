(ns top.kzre.krro.plugin.painting.editor.javafx.input.pointer
  "JavaFX 平台指针输入源（缓冲模式）：所有事件转换为标准事件并追加到原子缓冲区。"
  (:require [top.kzre.krro.plugin.painting.core.input :as input]
            [top.kzre.krro.plugin.painting.core.event :as event])
  (:import (javafx.event EventHandler)
           (javafx.scene.canvas Canvas)
           (javafx.scene.input MouseButton MouseEvent)))

(defn- javafx-button->keyword
  [^MouseButton btn]
  (cond
    (= btn MouseButton/PRIMARY)  :left
    (= btn MouseButton/SECONDARY) :right
    (= btn MouseButton/MIDDLE)   :middle
    :else nil))

(defrecord PointerBuffered [pressed? ^Canvas canvas buffer current-button]
  input/IInputSource
  (start! [_]
    (let [on-press (reify EventHandler
                     (handle [_ e]
                       (reset! pressed? true)
                       (let [btn (javafx-button->keyword (.getButton e))]
                         (reset! current-button btn)
                         (swap! buffer conj
                                (event/make-pointer-event :press (.getX e) (.getY e)
                                                          :mouse-button btn)))))
          on-drag  (reify EventHandler
                     (handle [_ e]
                       (when @pressed?
                         (swap! buffer conj
                                (event/make-pointer-event :drag (.getX e) (.getY e)
                                                          :mouse-button @current-button)))))
          on-move (reify EventHandler
                    (handle [_ e]
                      (swap! buffer conj
                             (event/make-pointer-event :move (.getX e) (.getY e)))))
          on-release (reify EventHandler
                       (handle [_ e]
                         (when @pressed?
                           (let [btn (javafx-button->keyword (.getButton e))]
                             (swap! buffer conj
                                    (event/make-pointer-event :release (.getX e) (.getY e)
                                                              :mouse-button btn))
                             (reset! pressed? false)
                             (reset! current-button nil)))))
          on-scroll (reify EventHandler
                      (handle [_ e]
                        (when-not (.isDirect e)
                          (swap! buffer conj
                                 (event/make-pointer-event :scroll (.getX e) (.getY e)
                                                           :delta-x (.getDeltaX e) :delta-y (.getDeltaY e)
                                                           :mouse-button :middle)))))]
      (.setOnMousePressed canvas on-press)
      (.setOnMouseDragged canvas on-drag)
      (.setOnMouseReleased canvas on-release)
      (.setOnMouseMoved canvas on-move)
      (.setOnScroll canvas on-scroll))
    nil)

  (stop! [_]
    (.setOnMousePressed canvas nil)
    (.setOnMouseDragged canvas nil)
    (.setOnMouseReleased canvas nil)
    (.setOnMouseMoved canvas nil)
    (.setOnScroll canvas nil)
    (reset! pressed? false)
    (reset! current-button nil)))

(defn make-pointer-input
  "创建缓冲式鼠标输入源。事件将追加到 buffer 原子中。"
  [^Canvas canvas buffer]
  (map->PointerBuffered {:canvas canvas :buffer buffer
                         :pressed? (atom false) :current-button (atom nil)}))