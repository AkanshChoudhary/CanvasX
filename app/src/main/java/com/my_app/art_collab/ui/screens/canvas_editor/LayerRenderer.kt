package com.my_app.art_collab.ui.screens.canvas_editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.my_app.art_collab.domain.model.BlendMode
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerType
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

fun DrawScope.drawCheckerboard() {
    val checkerSize = 10f
    val cols = (size.width / checkerSize).toInt() + 1
    val rows = (size.height / checkerSize).toInt() + 1
    
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val isLight = (row + col) % 2 == 0
            drawRect(
                color = if (isLight) Color(0xFFE0E0E0) else Color(0xFFBDBDBD),
                topLeft = Offset(col * checkerSize, row * checkerSize),
                size = Size(checkerSize, checkerSize)
            )
        }
    }
}

fun DrawScope.drawLayer(
    layer: Layer,
    canvasWidth: Int,
    canvasHeight: Int,
    displayWidth: Float,
    displayHeight: Float,
    imageCache: ImageCache,
    getProcessedBitmap: ((String) -> android.graphics.Bitmap?)? = null,
    getHttpBitmap: ((String) -> android.graphics.Bitmap?)? = null
) {
    val scaleToDisplay = displayWidth / canvasWidth

    when (layer.type) {
        LayerType.SOLID_COLOR -> {
            // Match RenderEngine: missing color (e.g. RTDB parse edge case) still shows a fill;
            // otherwise collaborators only see the selection stroke with an empty interior.
            val argb = layer.solidColor ?: 0xFFFFFFFF.toInt()
            val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
            val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay
            val layerW = displayWidth * layer.transform.scaleX
            val layerH = displayHeight * layer.transform.scaleY

            drawRect(
                color = Color(argb),
                topLeft = Offset(centerX - layerW / 2, centerY - layerH / 2),
                size = Size(layerW, layerH),
                alpha = layer.opacity,
                blendMode = mapBlendMode(layer.blendMode)
            )
        }
        LayerType.IMAGE, LayerType.AI_GENERATED -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
                ?: getHttpBitmap?.invoke(layer.id)
                ?: imageCache.getBitmap(layer)
            if (bitmap != null) {
                val imageBitmap = bitmap.asImageBitmap()

                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay

                val scaledWidth = baseWidth * layer.transform.scaleX
                val scaledHeight = baseHeight * layer.transform.scaleY

                val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
                val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay

                val destLeft = (centerX - scaledWidth / 2).toInt()
                val destTop = (centerY - scaledHeight / 2).toInt()

                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(destLeft, destTop),
                    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                    alpha = layer.opacity,
                    blendMode = mapBlendMode(layer.blendMode)
                )
            }
        }
        LayerType.TEXT -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
            if (bitmap != null) {
                val imageBitmap = bitmap.asImageBitmap()

                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay

                val scaledWidth = baseWidth * layer.transform.scaleX
                val scaledHeight = baseHeight * layer.transform.scaleY

                val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
                val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay

                val destLeft = (centerX - scaledWidth / 2).toInt()
                val destTop = (centerY - scaledHeight / 2).toInt()

                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(destLeft, destTop),
                    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                    alpha = layer.opacity,
                    blendMode = mapBlendMode(layer.blendMode)
                )
            }
        }
    }
}

fun DrawScope.drawSelectionBorder(
    layer: Layer,
    canvasWidth: Int,
    canvasHeight: Int,
    displayWidth: Float,
    displayHeight: Float,
    imageCache: ImageCache,
    handleRadius: Float,
    strokeWidth: Float,
    getProcessedBitmap: ((String) -> android.graphics.Bitmap?)? = null,
    getHttpBitmap: ((String) -> android.graphics.Bitmap?)? = null
) {
    val scaleToDisplay = displayWidth / canvasWidth
    
    val (layerW, layerH) = when (layer.type) {
        LayerType.SOLID_COLOR -> {
            Pair(displayWidth * layer.transform.scaleX, displayHeight * layer.transform.scaleY)
        }
        LayerType.IMAGE, LayerType.AI_GENERATED -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
                ?: getHttpBitmap?.invoke(layer.id)
                ?: imageCache.getBitmap(layer)
            if (bitmap != null) {
                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay
                Pair(baseWidth * layer.transform.scaleX, baseHeight * layer.transform.scaleY)
            } else {
                Pair(100f, 100f)
            }
        }
        LayerType.TEXT -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
            if (bitmap != null) {
                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay
                Pair(baseWidth * layer.transform.scaleX, baseHeight * layer.transform.scaleY)
            } else {
                Pair(100f, 100f)
            }
        }
    }

    val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
    val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay

    val left = centerX - layerW / 2
    val top = centerY - layerH / 2
    val right = centerX + layerW / 2
    val bottom = centerY + layerH / 2

    // Draw selection rectangle
    drawRect(
        color = Color(0xFF2196F3),
        topLeft = Offset(left, top),
        size = Size(layerW, layerH),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )

    // Draw corner handles
    val corners = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom)
    )

    corners.forEach { corner ->
        drawCircle(
            color = Color.White,
            radius = handleRadius,
            center = corner
        )
        drawCircle(
            color = Color(0xFF2196F3),
            radius = handleRadius,
            center = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
    }
}

data class LayerBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun mapBlendMode(mode: BlendMode): ComposeBlendMode = when (mode) {
    BlendMode.NORMAL -> ComposeBlendMode.SrcOver
    BlendMode.MULTIPLY -> ComposeBlendMode.Multiply
    BlendMode.SCREEN -> ComposeBlendMode.Screen
    BlendMode.OVERLAY -> ComposeBlendMode.Overlay
    BlendMode.SOFT_LIGHT -> ComposeBlendMode.Softlight
}

fun calculateLayerBounds(
    layer: Layer,
    canvasWidth: Int,
    canvasHeight: Int,
    displayWidth: Float,
    displayHeight: Float,
    imageCache: ImageCache,
    getProcessedBitmap: ((String) -> android.graphics.Bitmap?)? = null,
    getHttpBitmap: ((String) -> android.graphics.Bitmap?)? = null
): LayerBounds {
    val scaleToDisplay = displayWidth / canvasWidth
    
    val (layerW, layerH) = when (layer.type) {
        LayerType.SOLID_COLOR -> {
            Pair(displayWidth * layer.transform.scaleX, displayHeight * layer.transform.scaleY)
        }
        LayerType.IMAGE, LayerType.AI_GENERATED -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
                ?: getHttpBitmap?.invoke(layer.id)
                ?: imageCache.getBitmap(layer)
            if (bitmap != null) {
                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay
                Pair(baseWidth * layer.transform.scaleX, baseHeight * layer.transform.scaleY)
            } else {
                Pair(100f, 100f)
            }
        }
        LayerType.TEXT -> {
            val bitmap = getProcessedBitmap?.invoke(layer.id)
            if (bitmap != null) {
                val baseWidth = bitmap.width * scaleToDisplay
                val baseHeight = bitmap.height * scaleToDisplay
                Pair(baseWidth * layer.transform.scaleX, baseHeight * layer.transform.scaleY)
            } else {
                Pair(100f, 100f)
            }
        }
    }

    val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
    val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay

    return LayerBounds(
        left = centerX - layerW / 2,
        top = centerY - layerH / 2,
        width = layerW,
        height = layerH
    )
}
