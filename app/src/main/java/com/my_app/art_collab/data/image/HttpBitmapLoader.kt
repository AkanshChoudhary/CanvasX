package com.my_app.art_collab.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import com.my_app.art_collab.debug.LayerImageDebug
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and decodes an image from an HTTP(S) URL (e.g. Firebase Storage download links).
 * Call only from a background thread.
 */
object HttpBitmapLoader {

    private const val TAG = "HttpBitmapLoader"

    fun load(context: Context, urlString: String): Bitmap? {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty()) {
            Log.w(LayerImageDebug.TAG, "HttpBitmapLoader: empty URL")
            return null
        }
        Log.d(LayerImageDebug.TAG, "HttpBitmapLoader: GET ${LayerImageDebug.pathPreview(trimmed)}")
        return try {
            val connection = URL(trimmed).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CanvasX/1.0 (Android)")
            connection.doInput = true
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                val errSnippet = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(400)
                Log.w(TAG, "HTTP $code for ${trimmed.take(96)} snippet=$errSnippet")
                connection.disconnect()
                return null
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            if (bytes.isEmpty()) {
                Log.w(TAG, "Empty body for ${trimmed.take(96)}")
                return null
            }

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tempFile = File.createTempFile("layer_", ".img", context.cacheDir)
                try {
                    tempFile.writeBytes(bytes)
                    val source = ImageDecoder.createSource(tempFile)
                    bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } finally {
                    tempFile.delete()
                }
            }
            if (bitmap == null) {
                Log.w(TAG, "Decode failed for ${trimmed.take(96)} (${bytes.size} bytes)")
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "load failed: ${trimmed.take(120)}", e)
            null
        }
    }
}
