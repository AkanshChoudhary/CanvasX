package com.my_app.art_collab.ui.screens.canvas_editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.data.image.HttpBitmapLoader
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerType
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ImageCache(private val context: Context) {
    private val cache = mutableMapOf<String, Bitmap?>()

    fun getBitmap(layer: Layer): Bitmap? {
        if (layer.type == LayerType.SOLID_COLOR) return null

        val path = layer.sourceBitmapPath ?: run {
            if (loggedNoPath.add(layer.id)) {
                Log.d(LayerImageDebug.TAG, "ImageCache.getBitmap: no path layer=${layer.id} type=${layer.type}")
            }
            return null
        }
        // Remote URLs must be loaded off the main thread (see CanvasViewport); drawLayer runs on UI.
        if (path.startsWith("http://") || path.startsWith("https://")) {
            if (loggedHttpSkips.add(layer.id)) {
                Log.d(
                    LayerImageDebug.TAG,
                    "ImageCache.getBitmap: skip http(s) on UI thread layer=${layer.id} (preload/render cache must supply)"
                )
            }
            return null
        }

        return cache.getOrPut(path) {
            loadBitmap(path)
        }
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            when {
                path.startsWith("content://") -> {
                    val uri = Uri.parse(path)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }
                path.startsWith("file://") -> {
                    BitmapFactory.decodeFile(path.removePrefix("file://"))
                }
                File(path).exists() -> {
                    BitmapFactory.decodeFile(path)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(LayerImageDebug.TAG, "ImageCache.loadBitmap failed path=${LayerImageDebug.pathPreview(path)}", e)
            e.printStackTrace()
            null
        }
    }

    fun clear() {
        cache.clear()
    }

    fun invalidate(layerId: String, layers: List<Layer>) {
        val layer = layers.find { it.id == layerId }
        layer?.sourceBitmapPath?.let { cache.remove(it) }
    }

    companion object {
        private val loggedHttpSkips = ConcurrentHashMap.newKeySet<String>()
        private val loggedNoPath = ConcurrentHashMap.newKeySet<String>()

        /** Call only from a background thread (e.g. [kotlinx.coroutines.Dispatchers.IO]). */
        fun loadBitmapFromHttpUrl(context: Context, path: String): Bitmap? =
            HttpBitmapLoader.load(context, path)
    }
}

@Composable
fun rememberImageCache(layers: List<Layer>): ImageCache {
    val context = LocalContext.current
    return remember(context) { ImageCache(context) }
}
