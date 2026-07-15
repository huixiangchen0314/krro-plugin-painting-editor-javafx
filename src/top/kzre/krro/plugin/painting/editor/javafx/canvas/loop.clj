(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.loop
  "全局动画循环管理，与具体工具解耦。"
  (:require
    [taoensso.timbre :as log])
  (:import
    (javafx.animation AnimationTimer)))

(defonce animation-timers (atom {}))

(defn start-loop! [canvas-id render-fn]
  (when-not (get @animation-timers canvas-id)
    (let [timer (proxy [AnimationTimer] []
                  (handle [_]
                    (try
                      (render-fn)
                      (catch Exception e
                        (log/error e (str "Render loop error: " (.getMessage e)))))))]
      (swap! animation-timers assoc canvas-id timer)
      (.start timer))))

(defn stop-loop! [canvas-id]
  (when-let [timer (get @animation-timers canvas-id)]
    (.stop timer)
    (swap! animation-timers dissoc canvas-id)))