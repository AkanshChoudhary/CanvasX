package com.my_app.art_collab.ui.screens.canvas_editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

enum class DragMode {
    NONE,
    MOVE,
    RESIZE_TOP_LEFT,
    RESIZE_TOP_RIGHT,
    RESIZE_BOTTOM_LEFT,
    RESIZE_BOTTOM_RIGHT
}

fun determineDragMode(
    touchPoint: Offset,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    handleTouchRadius: Float
): DragMode {
    val right = left + width
    val bottom = top + height

    val corners = listOf(
        DragMode.RESIZE_TOP_LEFT to Offset(left, top),
        DragMode.RESIZE_TOP_RIGHT to Offset(right, top),
        DragMode.RESIZE_BOTTOM_LEFT to Offset(left, bottom),
        DragMode.RESIZE_BOTTOM_RIGHT to Offset(right, bottom)
    )

    for ((mode, corner) in corners) {
        if (abs(touchPoint.x - corner.x) <= handleTouchRadius &&
            abs(touchPoint.y - corner.y) <= handleTouchRadius
        ) {
            return mode
        }
    }

    if (touchPoint.x in left..right && touchPoint.y in top..bottom) {
        return DragMode.MOVE
    }

    return DragMode.NONE
}

data class DragResult(
    val dx: Float,
    val dy: Float,
    val dScaleX: Float,
    val dScaleY: Float
)

fun applyDrag(
    mode: DragMode,
    dragAmountX: Float,
    dragAmountY: Float,
    currentScaleX: Float,
    currentScaleY: Float,
    layerWidth: Float,
    layerHeight: Float,
    displayToCanvasScale: Float
): DragResult {
    return when (mode) {
        DragMode.MOVE -> DragResult(
            dx = dragAmountX * displayToCanvasScale,
            dy = dragAmountY * displayToCanvasScale,
            dScaleX = 0f,
            dScaleY = 0f
        )
        DragMode.RESIZE_BOTTOM_RIGHT -> {
            val scaleDeltaX = (dragAmountX * displayToCanvasScale) / layerWidth
            val scaleDeltaY = (dragAmountY * displayToCanvasScale) / layerHeight
            DragResult(
                dx = 0f,
                dy = 0f,
                dScaleX = scaleDeltaX,
                dScaleY = scaleDeltaY
            )
        }
        DragMode.RESIZE_TOP_LEFT -> {
            val scaleDeltaX = -(dragAmountX * displayToCanvasScale) / layerWidth
            val scaleDeltaY = -(dragAmountY * displayToCanvasScale) / layerHeight
            DragResult(
                dx = dragAmountX * displayToCanvasScale,
                dy = dragAmountY * displayToCanvasScale,
                dScaleX = scaleDeltaX,
                dScaleY = scaleDeltaY
            )
        }
        DragMode.RESIZE_TOP_RIGHT -> {
            val scaleDeltaX = (dragAmountX * displayToCanvasScale) / layerWidth
            val scaleDeltaY = -(dragAmountY * displayToCanvasScale) / layerHeight
            DragResult(
                dx = 0f,
                dy = dragAmountY * displayToCanvasScale,
                dScaleX = scaleDeltaX,
                dScaleY = scaleDeltaY
            )
        }
        DragMode.RESIZE_BOTTOM_LEFT -> {
            val scaleDeltaX = -(dragAmountX * displayToCanvasScale) / layerWidth
            val scaleDeltaY = (dragAmountY * displayToCanvasScale) / layerHeight
            DragResult(
                dx = dragAmountX * displayToCanvasScale,
                dy = 0f,
                dScaleX = scaleDeltaX,
                dScaleY = scaleDeltaY
            )
        }
        DragMode.NONE -> DragResult(0f, 0f, 0f, 0f)
    }
}
