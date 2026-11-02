(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.painting-canvas
  "画布组件的业务集成。连接 canvas-kernel 协议与 painting 业务逻辑——
   把图层数据、视口、store、hook 接到 kernel 的输入接口上。

   kernel-supplier 决定实际渲染后端（GL 或软件），本命名空间不感知。
   supplier 签名：(fn [opts] -> {:container StackPane :kernel ICanvasKernal})
   opts 至少包含 :on-event。

   生命周期：
     kernel + container —— 组件级。create-fn 创建一次，dispose-fn 释放。
     canvas-id / frame / hook / store —— props 级。init-fn 注册，
     cleanup 释放。canvas-id 变化时旧会话先清理，再以新 props 重建。"
  (:require
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.viewport :as viewport]
    [top.kzre.krro.plugin.painting.core.core]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.core]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]
    [top.kzre.krro.plugin.painting.editor.javafx.canvas.protocol :as proto]
    [top.kzre.krro.plugin.painting.editor.javafx.graph :as graph]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.scene.layout StackPane)))

;; ── 挂在 container.getProperties() 上的键 ──
(def ^:private kernel-fx-prop-key  ::kernel)
(def ^:private state-atom-fx-prop-key ::ctx-ref)

;; ═══════════════════════════════════════════════
;; 业务集成
;; ═══════════════════════════════════════════════

(defn- render!
  "把 canvas-data 推给 kernel，触发一次重绘。"
  [kernel frame {:keys [layers width height current-layer-id]} changes]
  (let [viewport (vp/get-viewport frame)]
    (proto/set-layers!           kernel layers)
    (proto/set-current-layer-id! kernel current-layer-id)
    (proto/set-image-size!       kernel width height)
    (proto/set-viewport!         kernel viewport)
    (proto/repaint               kernel changes)))

(defn- start-session
  "启动一次业务会话。kernel 由 create-fn 提供，本方法只绑定业务侧资源。

   返回清理函数——释放本次会话注册的 hook、store、frame param。
   不涉及 kernel——kernel 的生命周期由 dispose-fn 管理。"
  [^StackPane container frame canvas-id]
  (let [kernel  (.get (.getProperties container) kernel-fx-prop-key)
        ctx-ref (.get (.getProperties container) state-atom-fx-prop-key)

        on-render-canvas
        (fn [cid canvas-data changes]
          (when (= cid canvas-id)
            (render! kernel frame canvas-data changes)))]

    ;; 更新 on-event 的动态绑定——kernel 的 on-event 闭包从这里读
    (reset! ctx-ref {:canvas-id canvas-id :frame frame})
    ;; 画布状态绑定画布组件，不允许重复打开画布组件。不同画布不同创建
    ;; 这个怎么限制呢，需要统一入口，不能报错
    ;; 用户试图调用 active-painting-canvas-mode=>失败，窗口已经打开

    (store/reg-canvas-store canvas-id)
    (hook/add-hook! :krro.painting/render-canvas-hook on-render-canvas)

    (rf/dispatch store/app-id  [:tool/select-tool canvas-id :brush])
    (layer/auto-select-layer! canvas-id)

    (frame/set-param! frame :krro.painting/overlay-graph-context
                      (graph/make-javafx-graphics (proto/overlay-gc kernel)))

    ;; 首次渲染
    (render! kernel frame (pc/canvas-data! canvas-id) [(viewport/refreshed)])

    ;; 清理函数
    (fn []
      (hook/remove-hook! :krro.painting/render-canvas-hook on-render-canvas)
      (store/unreg-canvas-store canvas-id)
      (frame/set-param! frame :krro.painting/overlay-graph-context nil)
      (reset! ctx-ref {:canvas-id nil :frame nil}))))

;; ═══════════════════════════════════════════════
;; 组件工厂
;; ═══════════════════════════════════════════════
(defn make-factory
  "构造画布组件工厂。

   kernel-supplier —— 决定渲染后端。签名 (fn [opts] -> {:container :kernel})。
     opts 至少包含 :on-event。
     返回的 container 会被用作组件根节点。

   组件 props：
     :krro.painting/canvas-id —— 画布标识

   canvas-id 变化时，框架先调上次 cleanup 释放会话级资源，再以新
   props 重新调 init-fn——kernel 保持不变。"
  [kernel-supplier]
  (make-component
    [:krro.painting/canvas-id]

    ;; ── create-fn：组件级资源，只创建一次 ──
    (fn []
      (let [state-atom  (atom
                          {:canvas-id nil
                           :frame nil})
            on-event (fn [ev]
                       (let [{:keys [canvas-id frame]} @state-atom]
                         (when canvas-id
                           (rf/dispatch store/app-id [:canvas/set-cursor-position canvas-id ev])
                           (rf/dispatch store/app-id [:tool/dispatch-event canvas-id ev frame]))))

            {:keys [^StackPane container kernel]}
            (kernel-supplier {:on-event on-event})]

        (.put (.getProperties container) kernel-fx-prop-key  kernel)
        (.put (.getProperties container) state-atom-fx-prop-key state-atom)
        container))

    ;; ── init-fn：props 级资源，每次 props 变化重建 ──
    (fn [^StackPane container _old-props new-props frame]
      (let [canvas-id (:krro.painting/canvas-id new-props)]
        (if (nil? canvas-id)
          (fn [])
          (start-session container frame canvas-id))))

    ;; ── dispose-fn：卸载时释放组件级资源 ──
    :dispose-fn
    (fn [^StackPane container]
      (when-let [kernel (.get (.getProperties container) kernel-fx-prop-key)]
        (.remove (.getProperties container) kernel-fx-prop-key)
        (.remove (.getProperties container) state-atom-fx-prop-key)
        (proto/close kernel)))))