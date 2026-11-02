(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.protocol)

(defprotocol ICanvasKernel
  (set-layers! [_ layers] "设置图层栈")
  (set-current-layer-id! [_ layer-id] "设置当前图层id")
  (set-image-size! [_ width height] "设置图像大小范围")
  (set-viewport! [_ viewport] "设置视口变换")
  (overlay-gc [_] "获取叠加层图形上下文")
  (repaint [_ changes] "重绘请求")
  (close [_] "释放"))