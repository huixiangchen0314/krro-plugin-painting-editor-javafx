(ns top.kzre.krro.plugin.painting.editor.javafx.input.pen
  "数位笔输入源 (pen4j) 的缓冲实现。
   笔线程将标准化事件追加到 buffer 原子，渲染线程每帧消费。"
  (:require
    [top.kzre.krro.plugin.painting.core.event :as event]
    [top.kzre.krro.plugin.painting.editor.javafx.input.protocol :as input])
  (:import
    (top.kzre.pen4j.api PenEvent PenListener PenState)
    (top.kzre.pen4j.core PenContext)
    (top.kzre.pen4j.windows.rawinput RawInputDriver)))


(defrecord PenBuffered [hwnd fx-pane on-event pressed? pen-context]
  input/IInputSource
  (start! [_]
    (try
      (let [driver (RawInputDriver.)
            context (PenContext/create driver)]
        (.addListener context
                      (reify PenListener
                        (^void onPenData [this ^PenEvent e]
                          (let [^PenState s (.getState e)
                                 sx (.getX s)
                                 sy (.getY s)
                                fx-pane-bounds (.localToScreen fx-pane (.getBoundsInLocal fx-pane))
                                fx-pane-min-x  (.getMinX fx-pane-bounds)
                                fx-pane-min-y  (.getMinY fx-pane-bounds)

                                ;; 2. 如果笔驱动返回的已经是屏幕绝对坐标，直接相减
                                cx (- sx fx-pane-min-x)
                                cy (- sy fx-pane-min-y)
                                pressure (.getPressure s)
                                tilt-x   (.getTiltX s)
                                tilt-y   (.getTiltY s)
                                twist    (.getTwist s)
                                near?    (.isNear s)
                                tip-pressed (.isTipPressed s)
                                event-type (cond
                                             (and near? tip-pressed (not @pressed?)) :press
                                             (and near? tip-pressed @pressed?) :drag
                                             (and near? (not tip-pressed) @pressed?) :release
                                             (and near? (not tip-pressed)) :hover
                                             :else :hover)]
                            (when (= event-type :press) (reset! pressed? true))
                            (when (= event-type :release) (reset! pressed? false))
                            (on-event
                                   (event/make-pen-event
                                     event-type cx cy pressure tilt-x tilt-y twist near?
                                     :timestamp (.getTimestampMicros e)
                                     :pointer-id 0))))
                        (onDeviceAdded [this device])
                        (onDeviceRemoved [this  device])))
        (.start context)
        (reset! pen-context context))
      (catch Exception e
        (throw (RuntimeException. "Failed to start pen input" e))))
    nil)

  (stop! [_]
    (when-let [ctx @pen-context]
      (.close ctx)
      (reset! pen-context nil))
    (reset! pressed? false)
    nil))

(defn make-pen-input
  "创建缓冲式数位笔输入源。事件将追加到 on-event 原子中，由外部轮询消费。
   hwnd   : 窗口句柄
   fx-pane : 监听事件的 区域节点
   on-event : 原子，用于存放累积的笔事件向量"
  [fx-pane on-event]
  (map->PenBuffered {:fx-pane fx-pane
                     :on-event on-event
                     :pressed? (atom false)
                     :pen-context (atom nil)}))