(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.gl
  (:require [top.kzre.krro.core.util.promise :as promise]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import (top.kzre.krro.canvas.gl.composite GLCompositeContextBuilder GLCompositeFlyweight)
           (top.kzre.krro.canvas.gl.resource NoGLTexturePool PixelFormat)
           (top.kzre.krro.canvas.gl.tile GLRgba8AtlasFactory GLTiledTextureLayout)
           (top.kzre.krro.plugin.painting.editor.javafx.glfx  GLRuntime)))

(defn composite-flyweight
  "从 GLRuntime 获取或惰性创建 GLCompositeFlyweight。

   flyweight 与 GL 上下文同寿、全局一份——通过 computeIfAbsent 保证。
   构造发生在 GL 线程，GL 上下文已 current。

   返回 Promise<GLCompositeFlyweight>。"
  [^GLRuntime rt]
  (let [tile-size pc/global-tile-size
        executor  (.getExecutor rt)
        layout    (GLTiledTextureLayout/square 8 (int tile-size) 4)
        tex-pool  (NoGLTexturePool. executor)
        factory   (GLRgba8AtlasFactory. layout tex-pool)]
    (promise/from-completable-future
      (.computeIfAbsent
        rt
        GLCompositeFlyweight
        (reify Callable
          (call [_]
            (-> (GLCompositeFlyweight/builder)
                (.glExecutor executor)
                (.tileSize (int tile-size))
                (.atlasCapacity 3)
                (.atlasFactory factory)
                (.pixelFormat PixelFormat/RGBA8)
                (.build)
                (.join))))))))

(defn glfx-composite-context-builder
  []
  (let [tile-size pc/global-tile-size
        gl-runtime (GLRuntime/offscreen)
        executor  (.getExecutor gl-runtime)
        layout    (GLTiledTextureLayout/square 8 (int tile-size) 4)
        tex-pool  (NoGLTexturePool. executor)
        factory   (GLRgba8AtlasFactory. layout tex-pool)]
    (-> (GLCompositeContextBuilder.)
        (.glExecutor executor)
        (.tileSize (int tile-size))
        (.atlasCapacity 3)
        (.atlasFactory factory)
        (.viewFboPoolCapacity 4)
        (.pixelFormat PixelFormat/RGBA8))))