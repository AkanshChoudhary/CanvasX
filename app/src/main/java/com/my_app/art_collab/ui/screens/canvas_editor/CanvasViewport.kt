package com.my_app.art_collab.ui.screens.canvas_editor

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CanvasViewport(
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    compositedBitmap: Bitmap?,
    isRendering: Boolean,
    layers: List<Layer>,
    selectedLayerId: String?,
    preloadedRemoteBitmaps: Map<String, Bitmap> = emptyMap(),
    getProcessedBitmap: (String) -> Bitmap?,
//    onMoveLayer: (layerId: String, dx: Float, dy: Float) -> Unit,
//    onScaleLayer: (layerId: String, newScaleX: Float, newScaleY: Float) -> Unit,
    onUpdateTransform: (layerId: String, transform: LayerTransform) -> Unit,
    onDragEnd: (layerId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var drawSize by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    val latestLayers = rememberUpdatedState(layers)
    val latestSelectedLayerId = rememberUpdatedState(selectedLayerId)

    val density = LocalDensity.current
    val appContext = LocalContext.current.applicationContext
    val imageCache = rememberImageCache(layers)
    val supplementalRemoteBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    val missingHttpSignature = remember(layers, preloadedRemoteBitmaps) {
        layers.mapNotNull { l ->
            val p = l.sourceBitmapPath ?: return@mapNotNull null
            if (!p.startsWith("http://") && !p.startsWith("https://")) return@mapNotNull null
            if (preloadedRemoteBitmaps[l.id] != null) return@mapNotNull null
            "${l.id}\u0000$p"
        }.sorted().joinToString("|")
    }

    LaunchedEffect(missingHttpSignature) {
        val targets = layers.mapNotNull { l ->
            val p = l.sourceBitmapPath ?: return@mapNotNull null
            if (!p.startsWith("http://") && !p.startsWith("https://")) return@mapNotNull null
            if (preloadedRemoteBitmaps[l.id] != null) return@mapNotNull null
            l.id to p
        }.toMap()
        if (targets.isNotEmpty()) {
            Log.d(
                LayerImageDebug.TAG,
                "CanvasViewport supplemental HTTP load: count=${targets.size} ids=${targets.keys}"
            )
        }
        supplementalRemoteBitmaps.keys.retainAll(targets.keys)
        coroutineScope {
            targets.forEach { (id, url) ->
                launch(Dispatchers.IO) {
                    val bmp = ImageCache.loadBitmapFromHttpUrl(appContext, url)
                    if (bmp == null) {
                        Log.w(
                            LayerImageDebug.TAG,
                            "CanvasViewport supplemental: FAIL layerId=$id url=${LayerImageDebug.pathPreview(url)}"
                        )
                        return@launch
                    }
                    withContext(Dispatchers.Main.immediate) {
                        val stillSame = latestLayers.value.any { it.id == id && it.sourceBitmapPath == url }
                        if (stillSame) {
                            supplementalRemoteBitmaps[id] = bmp
                            Log.d(
                                LayerImageDebug.TAG,
                                "CanvasViewport supplemental: OK layerId=$id ${bmp.width}x${bmp.height}"
                            )
                        } else {
                            Log.w(
                                LayerImageDebug.TAG,
                                "CanvasViewport supplemental: dropped (layer/url changed) layerId=$id"
                            )
                        }
                    }
                }
            }
        }
    }

    val httpBitmapsKey = buildString {
        preloadedRemoteBitmaps.entries.sortedBy { it.key }.forEach {
            append(it.key)
            append(':')
            append(it.value.width)
            append('x')
            append(it.value.height)
            append('|')
        }
        supplementalRemoteBitmaps.entries.sortedBy { it.key }.forEach {
            append(it.key)
            append(':')
            append(it.value.width)
            append('x')
            append(it.value.height)
            append('|')
        }
    }

    val getHttpBitmap: (String) -> Bitmap? = {
        preloadedRemoteBitmaps[it] ?: supplementalRemoteBitmaps[it]
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (containerSize != IntSize.Zero) {
            val paddingPx = with(density) { 24.dp.toPx() }
            val usableWidthPx = containerSize.width - paddingPx * 2
            val usableHeightPx = containerSize.height - paddingPx * 2

            val canvasRatio = canvasWidthPx.toFloat() / canvasHeightPx.toFloat()
            val availableRatio = usableWidthPx / usableHeightPx

            val (dispWidthPx, dispHeightPx) = if (canvasRatio > availableRatio) {
                usableWidthPx to (usableWidthPx / canvasRatio)
            } else {
                (usableHeightPx * canvasRatio) to usableHeightPx
            }

            val dispWidth = with(density) { dispWidthPx.toDp() }
            val dispHeight = with(density) { dispHeightPx.toDp() }

            val handleTouchRadius = with(density) { 24.dp.toPx() }
            val handleRadius = with(density) { 8.dp.toPx() }
            val strokeWidth = with(density) { 2.dp.toPx() }

            val displayToCanvasScale = canvasWidthPx / dispWidthPx

            Box(
                modifier = Modifier
                    .size(dispWidth, dispHeight)
                    .shadow(elevation = 16.dp, shape = RectangleShape)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { drawSize = it }
                        .pointerInput(drawSize, httpBitmapsKey) {
                            var activeLayerId: String? = null
                            var activeTransform = LayerTransform()
                            var layerBaseWidth = 0f
                            var layerBaseHeight = 0f

                            detectDragGestures(
                                onDragStart = { down ->
                                    val selectedId = latestSelectedLayerId.value
                                    val layer = latestLayers.value.find { it.id == selectedId }

                                    if (layer == null || drawSize == IntSize.Zero) {
                                        dragMode = DragMode.NONE
                                        activeLayerId = null
                                        return@detectDragGestures
                                    }

                                    val bounds = calculateLayerBounds(
                                        layer = layer,
                                        canvasWidth = canvasWidthPx,
                                        canvasHeight = canvasHeightPx,
                                        displayWidth = drawSize.width.toFloat(),
                                        displayHeight = drawSize.height.toFloat(),
                                        imageCache = imageCache,
                                        getProcessedBitmap = getProcessedBitmap,
                                        getHttpBitmap = getHttpBitmap
                                    )

                                    activeLayerId = layer.id
                                    activeTransform = layer.transform
                                    layerBaseWidth = bounds.width / layer.transform.scaleX
                                    layerBaseHeight = bounds.height / layer.transform.scaleY

                                    dragMode = determineDragMode(
                                        touchPoint = down,
                                        left = bounds.left,
                                        top = bounds.top,
                                        width = bounds.width,
                                        height = bounds.height,
                                        handleTouchRadius = handleTouchRadius
                                    )
                                },
                                onDragEnd = {
                                    activeLayerId?.let { onDragEnd(it) }
                                    dragMode = DragMode.NONE
                                    activeLayerId = null
                                },
                                onDragCancel = {
                                    dragMode = DragMode.NONE
                                    activeLayerId = null
                                }
                            ) { change, dragAmount ->
                                change.consume()

                                val layerId = activeLayerId ?: return@detectDragGestures

                                val result = applyDrag(
                                    mode = dragMode,
                                    dragAmountX = dragAmount.x,
                                    dragAmountY = dragAmount.y,
                                    currentScaleX = activeTransform.scaleX,
                                    currentScaleY = activeTransform.scaleY,
                                    layerWidth = layerBaseWidth,
                                    layerHeight = layerBaseHeight,
                                    displayToCanvasScale = displayToCanvasScale
                                )

                                val newTransform = activeTransform.copy(
                                    translateX = activeTransform.translateX + result.dx,
                                    translateY = activeTransform.translateY + result.dy,
                                    scaleX = (activeTransform.scaleX + result.dScaleX).coerceAtLeast(0.1f),
                                    scaleY = (activeTransform.scaleY + result.dScaleY).coerceAtLeast(0.1f)
                                )

                                activeTransform = newTransform
                                onUpdateTransform(layerId, newTransform)
                            }
                        }
                ) {
                    drawCheckerboard()

                    @Suppress("UNUSED_EXPRESSION")
                    compositedBitmap

                    layers
                        .sortedBy { it.zIndex }
                        .forEach { layer ->
                            drawLayer(
                                layer = layer,
                                canvasWidth = canvasWidthPx,
                                canvasHeight = canvasHeightPx,
                                displayWidth = size.width,
                                displayHeight = size.height,
                                imageCache = imageCache,
                                getProcessedBitmap = getProcessedBitmap,
                                getHttpBitmap = getHttpBitmap
                            )
                        }

                    val selected = layers.find { it.id == selectedLayerId }
                    if (selected != null) {
                        drawSelectionBorder(
                            layer = selected,
                            canvasWidth = canvasWidthPx,
                            canvasHeight = canvasHeightPx,
                            displayWidth = size.width,
                            displayHeight = size.height,
                            imageCache = imageCache,
                            handleRadius = handleRadius,
                            strokeWidth = strokeWidth,
                            getProcessedBitmap = getProcessedBitmap,
                            getHttpBitmap = getHttpBitmap
                        )
                    }
                }
            }
        }
    }
}
