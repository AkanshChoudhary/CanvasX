package com.my_app.art_collab.engine

import com.my_app.art_collab.domain.model.Layer
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirtyFlagTracker @Inject constructor() {
    
    private val layerStateHashes = ConcurrentHashMap<String, Int>()

    fun isDirty(layer: Layer): Boolean {
        val cached = layerStateHashes[layer.id] ?: return true
        return cached != computeHash(layer)
    }

    fun markClean(layerId: String, layer: Layer) {
        layerStateHashes[layerId] = computeHash(layer)
    }

    fun invalidate(layerId: String) {
        layerStateHashes.remove(layerId)
    }

    fun invalidateAll() {
        layerStateHashes.clear()
    }

    private fun computeHash(layer: Layer): Int = Objects.hash(
        layer.sourceBitmapPath,
        layer.effectChain,
        layer.opacity,
        layer.blendMode,
        layer.solidColor,
        layer.textContent,
        layer.type
    )
}
