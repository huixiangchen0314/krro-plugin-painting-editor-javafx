(ns top.kzre.krro.plugin.painting.editor.javafx.input.protocol)

(defprotocol IInputSource
  (start! [this]
    "启动输入源，开始向 callback 发送标准化事件。
     canvas 为平台画布组件（JavaFX Canvas / Android View / HTML Canvas）。
     canvas-id 为画布逻辑标识。
     callback 为 (fn [event])，event 符合 top.kzre.krro.plugin.painting.canvas.event 规范。")
  (stop! [this]
    "停止输入源，释放资源。通常由 start! 返回的清理函数调用。"))