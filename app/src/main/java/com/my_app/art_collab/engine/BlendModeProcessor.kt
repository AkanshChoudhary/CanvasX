package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.domain.model.BlendMode
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.createBitmap

@Singleton
class BlendModeProcessor @Inject constructor() {

    companion object {
        private const val TAG = "BlendModeProcessor"
    }

    fun composite(
        base: Bitmap,
        blend: Bitmap,
        blendMode: BlendMode,
        opacity: Float
    ): Bitmap {
        LayerImageDebug.d(TAG, "composite() called: mode=$blendMode, opacity=$opacity, base=${base.width}x${base.height}, blend=${blend.width}x${blend.height}")

        if (blendMode == BlendMode.NORMAL) {
            LayerImageDebug.d(TAG, "Using NORMAL blend (no shader)")
            return compositeNormal(base, blend, opacity)
        }

        val shaderSource = when (blendMode) {
            BlendMode.MULTIPLY -> AgslShaders.MULTIPLY_BLEND
            BlendMode.SCREEN -> AgslShaders.SCREEN_BLEND
            BlendMode.OVERLAY -> AgslShaders.OVERLAY_BLEND
            BlendMode.SOFT_LIGHT -> AgslShaders.SOFT_LIGHT_BLEND
            BlendMode.NORMAL -> throw IllegalStateException("Handled above")
        }

        val w = base.width
        val h = base.height
        
        return try {
            val runtimeShader = RuntimeShader(shaderSource)
            LayerImageDebug.d(TAG, "RuntimeShader created successfully for $blendMode")

            runtimeShader.setInputBuffer(
                "base",
                BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            )
            runtimeShader.setInputBuffer(
                "blend",
                BitmapShader(blend, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            )
            runtimeShader.setFloatUniform("opacity", opacity)

            val paint = Paint().apply { shader = runtimeShader }

            val result = HardwareShaderRenderer.render(w, h) { canvas ->
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            if (result != null) {
                LayerImageDebug.d(TAG, "AGSL shader rendered successfully for $blendMode")
                result
            } else {
                LayerImageDebug.w(TAG, "HardwareShaderRenderer returned null for $blendMode, falling back to normal")
                compositeNormal(base, blend, opacity)
            }
        } catch (e: Exception) {
            LayerImageDebug.e(TAG, "AGSL shader failed for $blendMode: ${e.message}", e)
            compositeNormal(base, blend, opacity)
        }
    }

    private fun compositeNormal(base: Bitmap, blend: Bitmap, opacity: Float): Bitmap {
        val output = createBitmap(base.width, base.height)
        val canvas = Canvas(output)

        canvas.drawBitmap(base, 0f, 0f, null)

        val paint = Paint().apply {
            alpha = (opacity * 255).toInt()
        }
        canvas.drawBitmap(blend, 0f, 0f, paint)

        return output
    }
}
