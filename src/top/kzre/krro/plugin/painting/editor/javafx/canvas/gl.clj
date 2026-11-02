(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.gl
  (:require
   [top.kzre.krro.core.util.promise :as promise :refer [plet]]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
    (top.kzre.krro.canvas.gl.composite GLCompositeContext GLCompositeContextBuilder GLCompositeFlyweight)
   (top.kzre.krro.canvas.gl.resource NoGLTexturePool PixelFormat)
   (top.kzre.krro.canvas.gl.tile GLRgba8AtlasFactory GLTiledTextureLayout)
   (top.kzre.krro.plugin.painting.editor.javafx.glfx  GLRuntime)))

(defn- composite-flyweight-promise
  "从 GLRuntime 获取或惰性创建 GLCompositeFlyweight。

   flyweight 与 GL 上下文同寿、全局一份——通过 computeIfAbsent 保证。
   构造发生在 GL 线程，GL 上下文已 current。

   返回 Promise<GLCompositeFlyweight>。"
  [^GLRuntime gl-runtime]
  (let [tile-size pc/global-tile-size
        executor  (.getExecutor gl-runtime)
        layout    (GLTiledTextureLayout/square 8 (int tile-size) 10)
        tex-pool  (NoGLTexturePool. executor)
        factory   (GLRgba8AtlasFactory. layout tex-pool)]
    (promise/from-completable-future
      ;; 整个进程唯一
      (.computeIfAbsent
        gl-runtime
        GLCompositeFlyweight
        (reify Callable
          (call [_]
            (-> (GLCompositeFlyweight/builder)
                (.glExecutor executor)
                (.tileSize (int tile-size))
                (.atlasCapacity 3)
                (.atlasFactory factory)
                (.build)
                (.join))))))))

(defn glfx-composite-context-promise []
  (plet [flyweight (composite-flyweight-promise (GLRuntime/offscreen))]
        (GLCompositeContext. flyweight 4 PixelFormat/RGBA8)))