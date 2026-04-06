
package com.my_app.art_collab.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.my_app.art_collab.domain.model.Effect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EffectProcessor @Inject constructor() {

    suspend fun apply(source: Bitmap, chain: List<Effect>): Bitmap =
        withContext(Dispatchers.Default) {
            chain.fold(source) { current, effect ->
                if (effect.isEnabled) applyEffect(current, effect) else current
            }
        }

    private fun applyEffect(source: Bitmap, effect: Effect): Bitmap {
        return when (effect) {
            is Effect.BrightnessContrast -> applyShader(
                source = source,
                shaderSource = AgslShaders.BRIGHTNESS_CONTRAST
            ) { shader ->
                shader.setFloatUniform("brightness", effect.brightness)
                shader.setFloatUniform("contrast", effect.contrast)
            }
            is Effect.Exposure -> applyShader(
                source = source,
                shaderSource = AgslShaders.EXPOSURE
            ) { shader ->
                shader.setFloatUniform("stops", effect.stops)
            }
            is Effect.GaussianBlur -> applyTwoPassBlur(source, effect.radius)
            is Effect.Sharpen -> applyShader(
                source = source,
                shaderSource = AgslShaders.SHARPEN
            ) { shader ->
                shader.setFloatUniform("amount", effect.amount)
                shader.setFloatUniform("resolution", floatArrayOf(
                    source.width.toFloat(),
                    source.height.toFloat()
                ))
            }
            is Effect.Vignette -> applyShader(
                source = source,
                shaderSource = AgslShaders.VIGNETTE
            ) { shader ->
                shader.setFloatUniform("intensity", effect.intensity)
                shader.setFloatUniform("feather", effect.feather)
                shader.setFloatUniform("resolution", floatArrayOf(
                    source.width.toFloat(),
                    source.height.toFloat()
                ))
            }
            is Effect.Saturation -> applyShader(
                source = source,
                shaderSource = AgslShaders.SATURATION
            ) { shader ->
                shader.setFloatUniform("amount", effect.amount)
            }
            is Effect.ColorTemperature -> applyShader(
                source = source,
                shaderSource = AgslShaders.COLOR_TEMPERATURE
            ) { shader ->
                shader.setFloatUniform("temperature", effect.temperature)
                shader.setFloatUniform("tint", effect.tint)
            }
            is Effect.Grain -> applyShader(
                source = source,
                shaderSource = AgslShaders.GRAIN
            ) { shader ->
                shader.setFloatUniform("amount", effect.amount)
                shader.setFloatUniform("size", effect.size)
                shader.setFloatUniform("time", (System.currentTimeMillis() %
                        10000).toFloat() / 10000f)
            }
            is Effect.Pixelate -> applyShader(
                source = source,
                shaderSource = AgslShaders.PIXELATE
            ) { shader ->
                shader.setFloatUniform("blockSize", effect.blockSize)
            }
        }
    }

    private fun applyShader(
        source: Bitmap,
        shaderSource: String,
        uniforms: (RuntimeShader) -> Unit
    ): Bitmap {
        val w = source.width
        val h = source.height
        val runtimeShader = RuntimeShader(shaderSource)
        runtimeShader.setInputBuffer(
            "source",
            BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        uniforms(runtimeShader)
        val paint = Paint().apply { shader = runtimeShader }

        return HardwareShaderRenderer.render(w, h) { canvas ->
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        } ?: source
    }

    private fun applyTwoPassBlur(source: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return source
        val horizontal = applyShader(source, AgslShaders.GAUSSIAN_BLUR_HORIZONTAL) { shader ->
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("imageWidth", source.width.toFloat())
        }
        return applyShader(horizontal, AgslShaders.GAUSSIAN_BLUR_VERTICAL) { shader ->
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("imageHeight", source.height.toFloat())
        }
    }
}