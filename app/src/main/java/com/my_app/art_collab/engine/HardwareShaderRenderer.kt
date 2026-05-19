package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import com.my_app.art_collab.debug.LayerImageDebug

object HardwareShaderRenderer {
    private const val TAG = "HardwareShaderRenderer"

    /**
     * [Bitmap.wrapHardwareBuffer] requires the buffer to include [HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE].
     * Default [ImageReader] buffers often omit it, which throws on API 31+.
     * [HardwareBuffer.USAGE_GPU_COLOR_OUTPUT] is required for [HardwareRenderer] to draw into the surface.
     */
    private val imageReaderUsage: Long =
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
            HardwareBuffer.USAGE_CPU_READ_RARELY

    /** Parallel effect passes can briefly need more than one outstanding buffer. */
    private const val MAX_IMAGES = 3

    fun render(width: Int, height: Int, drawBlock: (Canvas) -> Unit): Bitmap? {
        var imageReader: ImageReader? = null
        var hardwareRenderer: HardwareRenderer? = null
        val renderNode = RenderNode("shader_render")

        return try {
            if (width <= 0 || height <= 0) return null

            renderNode.setPosition(0, 0, width, height)
            val canvas = renderNode.beginRecording()
            drawBlock(canvas)
            renderNode.endRecording()

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                MAX_IMAGES,
                imageReaderUsage
            )
            hardwareRenderer = HardwareRenderer()
            hardwareRenderer.setContentRoot(renderNode)
            hardwareRenderer.setSurface(imageReader.surface)
            hardwareRenderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = imageReader.acquireLatestImage() ?: return null
            image.use { copyImageToSoftwareBitmap(it, width, height) }
        } catch (e: Exception) {
            LayerImageDebug.e(TAG, "Hardware shader render failed", e)
            null
        } finally {
            imageReader?.close()
            hardwareRenderer?.setSurface(null)
            hardwareRenderer?.destroy()
            renderNode.discardDisplayList()
        }
    }

    private fun copyImageToSoftwareBitmap(image: Image, width: Int, height: Int): Bitmap {
        val hwBuffer = image.hardwareBuffer
        if (hwBuffer != null) {
            try {
                val wrapped = Bitmap.wrapHardwareBuffer(hwBuffer, null)
                if (wrapped != null) {
                    val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    if (wrapped != copy && !wrapped.isRecycled) wrapped.recycle()
                    return copy
                }
            } catch (e: IllegalArgumentException) {
                LayerImageDebug.w(TAG, "wrapHardwareBuffer failed; using CPU readback")
            } finally {
                hwBuffer.close()
            }
        }
        return readRgba8888ImageToBitmap(image, width, height)
    }

    /** Fallback when wrapHardwareBuffer is not allowed or returns null. */
    private fun readRgba8888ImageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (pixelStride == 4 && rowStride == width * 4) {
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }
        val pixels = IntArray(width * height)
        var out = 0
        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                val i = rowStart + x * pixelStride
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                val a = buffer.get(i + 3).toInt() and 0xFF
                pixels[out++] = Color.argb(a, r, g, b)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
