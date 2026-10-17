(ns top.kzre.krro.plugin.painting.editor.javafx.canvas.util
  (:import
    [java.util Iterator Set]
    (top.kzre.krro.canvas.gl.resource GLTexture)
    (top.kzre.krro.canvas.gl.tile AbstractGLTiledTexture$GLTextureTileData)
    [top.kzre.krro.util.tile Tile TiledCanvas]))


(defn canvas->tex
  [^TiledCanvas canvas]
  (let [^Set ts (.getTiles canvas)]
    (when-not (.isEmpty ts)
      (let [^Iterator it (.iterator ts)
            tk (long (.next it))
            tile (.getTile canvas tk)
            ^AbstractGLTiledTexture$GLTextureTileData data
            (.queryData ^Tile tile AbstractGLTiledTexture$GLTextureTileData)]
        (when data
          {:texture ^GLTexture (.getTexture data)
           :layer   (.getLayer data)
           :canvas  canvas})))))