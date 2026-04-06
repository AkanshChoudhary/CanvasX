package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderCache @Inject constructor() {
    
    private val cache = LruCache<String, Bitmap>(20)

    fun get(layerId: String): Bitmap? = cache.get(layerId)

    fun put(layerId: String, bitmap: Bitmap) {
        cache.put(layerId, bitmap)
    }

    fun remove(layerId: String) {
        cache.remove(layerId)
    }

    fun clear() {
        cache.evictAll()
    }

    fun onLowMemory() {
        cache.evictAll()
    }
}
