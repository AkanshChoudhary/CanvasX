package com.my_app.art_collab.data.image

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Local JPEG previews: [Context.getFilesDir]/canvas_thumbnails/{canvasId}.jpg
 */
@Singleton
class CanvasThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun saveJpeg(canvasId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) throw IllegalStateException("Bitmap recycled")
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) throw IllegalStateException("Invalid bitmap size")

        val maxSide = 512
        val scale = min(min(maxSide / w.toFloat(), maxSide / h.toFloat()), 1f)
        val toEncode = if (scale < 1f) {
            val nw = max(1, (w * scale).toInt())
            val nh = max(1, (h * scale).toInt())
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } else {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }
        try {
            val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
            val file = File(dir, "$canvasId.jpg")
            FileOutputStream(file).use { out ->
                if (!toEncode.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IllegalStateException("JPEG compress failed")
                }
            }
            file.absolutePath
        } finally {
            if (toEncode !== bitmap) {
                toEncode.recycle()
            }
        }
    }

    fun deleteThumbnail(canvasId: String) {
        val dir = File(context.filesDir, THUMB_DIR)
        File(dir, "$canvasId.jpg").takeIf { it.exists() }?.delete()
    }

    companion object {
        private const val THUMB_DIR = "canvas_thumbnails"
        private const val JPEG_QUALITY = 88
    }
}
