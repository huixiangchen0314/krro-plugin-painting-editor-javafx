(ns top.kzre.krro.plugin.painting.editor.javafx.util.windows
  "Windows 平台专用工具：从 JavaFX Stage 中获取原生窗口句柄 (HWND)。
   实际逻辑已移至 Java 类 Windows 中，此处仅作封装调用。"
  (:import (javafx.stage Stage)
           (top.kzre.krro.plugin.painting.editor.javafx.util Windows)))


(defn get-hwnd
  "返回给定 Stage 的 Windows HWND (原生窗口句柄)。
   通过调用 Windows/getHwnd 实现，失败时抛出 RuntimeException。"
  [^Stage stage]
  (Windows/getHwnd stage))

(defn get-window-rect [^long hwnd]
  (let [rect (Windows/getWindowRect hwnd)]
    {:left (.left rect) :top (.top rect) :right (.right rect) :bottom (.bottom rect)}))
