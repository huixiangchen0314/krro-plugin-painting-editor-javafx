(ns top.kzre.krro.plugin.painting.editor.javafx.graph
  (:require [top.kzre.krro.plugin.painting.editor.core.graph :as g]
            [top.kzre.krro.core.resources :as ress])
  (:import
    (javafx.application Platform)
    [javafx.scene.canvas GraphicsContext]
    [javafx.scene.paint Color]
    [javafx.scene.text Font]
    (top.kzre.colorutils.color RGB)))

(defn- color->javafx [color]
  (cond
    (instance? ress/float-array-class color)
    (let [r (RGB/red color)
          g (RGB/green color)
          b (RGB/blue color)
          a (RGB/alpha color)]
      (Color. (double r) (double g) (double b) (double (or a 1.0))))
    (vector? color)
    (let [[r g b a] (concat color (repeat 4 1.0))]  ;; 补齐 alpha
      (Color. (double r) (double g) (double b) (double (or a 1.0))))
    :else Color/BLACK))

(defn- font->javafx [font-desc]
  (if (string? font-desc)
    (Font/font ^String font-desc)
    (Font/getDefault)))

(defrecord JavaFXGraphContext [^GraphicsContext gc]
  g/IGraphicsContext
  (submit! [_ f] (Platform/runLater f))
  ;; ---- 清除与画布信息 ----
  (clear! [_]
    ;; TODO 补丁, GraphicsContext 状态重置失败,做状态检查
    (try
      (.clearRect gc 0 0 (.getWidth (.getCanvas gc)) (.getHeight (.getCanvas gc)))
      (catch NullPointerException
             e (str "caught exception: " (.getMessage e)))))

  (get-width [_]
    (.getWidth (.getCanvas gc)))

  (get-height [_]
    (.getHeight (.getCanvas gc)))

  ;; ---- 路径构建 ----
  (begin-path! [_] (.beginPath gc))
  (move-to! [_ x y] (.moveTo gc (double x) (double y)))
  (line-to! [_ x y] (.lineTo gc (double x) (double y)))
  (quadratic-curve-to! [_ cpx cpy x y]
    (.quadraticCurveTo gc (double cpx) (double cpy) (double x) (double y)))
  (bezier-curve-to! [_ cp1x cp1y cp2x cp2y x y]
    (.bezierCurveTo gc (double cp1x) (double cp1y) (double cp2x) (double cp2y) (double x) (double y)))
  (close-path! [_] (.closePath gc))
  (stroke-path! [_] (.stroke gc))
  (fill-path! [_] (.fill gc))
  (clip-path! [_] (.clip gc))

  ;; ---- 直接绘制 ----
  (draw-line! [_ x1 y1 x2 y2]
    (.strokeLine gc (double x1) (double y1) (double x2) (double y2)))

  (draw-rect! [_ x y w h]
    (.strokeRect gc (double x) (double y) (double w) (double h)))

  (fill-rect! [_ x y w h]
    (.fillRect gc (double x) (double y) (double w) (double h)))

  (draw-oval! [_ x y rx ry]
    (let [cx (double x) cy (double y) rx (double rx) ry (double ry)]
      (.strokeOval gc (- cx rx) (- cy ry) (* 2 rx) (* 2 ry))))

  (fill-oval! [_ x y rx ry]
    (let [cx (double x) cy (double y) rx (double rx) ry (double ry)]
      (.fillOval gc (- cx rx) (- cy ry) (* 2 rx) (* 2 ry))))

  (draw-text! [_ text x y]
    (.fillText gc text (double x) (double y)))

  ;; ---- 样式设置（转换通用类型） ----
  (set-stroke-color! [_ color]
    (.setStroke gc (color->javafx color)))

  (set-stroke-width! [_ width]
    (.setLineWidth gc (double width)))

  (set-stroke-dash! [_ dash-array]
    (let [dashes (double-array (map double dash-array))]
      (.setLineDashes gc dashes)))

  (set-stroke-dash-offset! [_ offset]
    (.setLineDashOffset gc (double offset)))

  (set-fill-color! [_ color]
    (.setFill gc (color->javafx color)))

  (set-font! [_ font-desc]
    (.setFont gc (font->javafx font-desc)))

  ;; ---- 状态管理 ----
  (save! [_] (.save gc))
  (restore! [_] (.restore gc))

  ;; ---- 变换 ----
  (translate! [_ tx ty] (.translate gc (double tx) (double ty)))
  (scale! [_ sx sy] (.scale gc (double sx) (double sy)))
  (rotate! [_ angle] (.rotate gc (double angle))))

(defn make-javafx-graphics [^GraphicsContext gc]
  (->JavaFXGraphContext gc))