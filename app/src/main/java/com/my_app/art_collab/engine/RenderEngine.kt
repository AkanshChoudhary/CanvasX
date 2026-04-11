package com.my_app.art_collab.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerType
import com.my_app.art_collab.domain.model.TextLayerContent
import com.my_app.art_collab.data.image.HttpBitmapLoader
import com.my_app.art_collab.debug.LayerImageDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RenderRequest(
    val layers: List<Layer>,
    val canvasWidthPx: Int,
    val canvasHeightPx: Int
)

@Singleton
class RenderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val effectProcessor: EffectProcessor,
    private val blendModeProcessor: BlendModeProcessor,
    private val transformProcessor: TransformProcessor,
    private val dirtyFlagTracker: DirtyFlagTracker,
    private val renderCache: RenderCache
) {
    private val renderChannel = Channel<RenderRequest>(Channel.CONFLATED)

    private val _compositedBitmap = MutableStateFlow<Bitmap?>(null)
    val compositedBitmap: StateFlow<Bitmap?> = _compositedBitmap.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    suspend fun startRenderLoop() {
        for (request: RenderRequest in renderChannel) {
            _isRendering.value = true
            try {
                val result = renderFrame(request)
                _compositedBitmap.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RenderEngine", "Render failed", e)
                Log.e(LayerImageDebug.TAG, "startRenderLoop: renderFrame failed", e)
            } finally {
                _isRendering.value = false
            }
        }
    }

    fun requestRender(layers: List<Layer>, canvasWidthPx: Int, canvasHeightPx: Int) {
        renderChannel.trySend(RenderRequest(layers, canvasWidthPx, canvasHeightPx))
    }

    fun invalidateLayer(layerId: String) {
        dirtyFlagTracker.invalidate(layerId)
        // Keep the old cached bitmap so the UI can show the previous
        // effect result while the new one is being processed.
        // The render loop will overwrite it once the new bitmap is ready.
    }

    fun invalidateAll() {
        dirtyFlagTracker.invalidateAll()
        renderCache.clear()
    }

    private suspend fun renderFrame(request: RenderRequest): Bitmap =
        withContext(Dispatchers.Default) {
            val visibleLayers = request.layers
                .sortedBy { it.zIndex }

            val imageLayers = visibleLayers.filter { it.type == LayerType.IMAGE || it.type == LayerType.AI_GENERATED }
            val dirtyImage = imageLayers.count { dirtyFlagTracker.isDirty(it) }
            val cachedImage = imageLayers.count { renderCache.get(it.id) != null }
            Log.d(
                LayerImageDebug.TAG,
                "renderFrame: ${request.canvasWidthPx}x${request.canvasHeightPx} total=${visibleLayers.size} " +
                    "image/ai=${imageLayers.size} dirtyImage=$dirtyImage cachedImage=$cachedImage"
            )

            // Step 1: Process dirty layers in parallel
            val parallelJobs = visibleLayers
                .filter { layer -> dirtyFlagTracker.isDirty(layer) }
                .map { layer ->
                    async {
                        try {
                            val sourceBitmap = loadSourceBitmap(
                                layer,
                                request.canvasWidthPx,
                                request.canvasHeightPx
                            ) ?: return@async null

                            val effectApplied = if (layer.effectChain.isNotEmpty()) {
                                effectProcessor.apply(
                                    source = sourceBitmap,
                                    chain = layer.effectChain
                                )
                            } else {
                                sourceBitmap
                            }

                            dirtyFlagTracker.markClean(layer.id, layer)
                            Log.d(
                                LayerImageDebug.TAG,
                                "renderFrame: processed layer=${layer.id} src=${sourceBitmap.width}x${sourceBitmap.height} " +
                                    "out=${effectApplied.width}x${effectApplied.height}"
                            )
                            layer.id to effectApplied
                        } catch (e: Exception) {
                            Log.e(
                                LayerImageDebug.TAG,
                                "renderFrame: layer pipeline FAILED id=${layer.id} " +
                                    "path=${LayerImageDebug.pathPreview(layer.sourceBitmapPath)}",
                                e
                            )
                            throw e
                        }
                    }
                }

            val freshBitmaps: Map<String, Bitmap> = parallelJobs.awaitAll().filterNotNull().toMap()
            freshBitmaps.forEach { (id, bitmap) -> renderCache.put(id, bitmap) }

            // Step 2: Composite all layers bottom-to-top (sequential)
            var composite = Bitmap.createBitmap(request.canvasWidthPx, request.canvasHeightPx, Bitmap.Config.ARGB_8888)

            visibleLayers.forEach { layer ->
                val layerBitmap = renderCache.get(layer.id)
                if (layerBitmap == null) {
                    if (layer.type == LayerType.IMAGE || layer.type == LayerType.AI_GENERATED) {
                        Log.w(
                            LayerImageDebug.TAG,
                            "renderFrame: COMPOSITE SKIP (no cache) id=${layer.id} " +
                                "dirtyWas=${dirtyFlagTracker.isDirty(layer)} " +
                                "path=${LayerImageDebug.pathPreview(layer.sourceBitmapPath)}"
                        )
                    }
                    return@forEach
                }

                val transformed = transformProcessor.apply(
                    layerBitmap = layerBitmap,
                    transform = layer.transform,
                    canvasWidth = request.canvasWidthPx,
                    canvasHeight = request.canvasHeightPx
                )

                composite = blendModeProcessor.composite(
                    base = composite,
                    blend = transformed,
                    blendMode = layer.blendMode,
                    opacity = layer.opacity
                )
            }

            Log.d(LayerImageDebug.TAG, "renderFrame: composite done")
            composite
        }

    private suspend fun loadSourceBitmap(
        layer: Layer,
        canvasWidth: Int,
        canvasHeight: Int
    ): Bitmap? {
        return when (layer.type) {
            LayerType.SOLID_COLOR -> {
                createSolidBitmap(
                    color = layer.solidColor ?: Color.WHITE,
                    width = canvasWidth,
                    height = canvasHeight
                )
            }
            LayerType.IMAGE, LayerType.AI_GENERATED -> {
                withContext(Dispatchers.IO) {
                    val path = layer.sourceBitmapPath
                    val decoded = path?.let { loadBitmapFromPath(it, layer.id) }
                    if (decoded == null) {
                        Log.w(
                            LayerImageDebug.TAG,
                            "loadSourceBitmap: decode failed, layer stays dirty for retry id=${layer.id} " +
                                "path=${LayerImageDebug.pathPreview(path)}"
                        )
                    }
                    decoded
                }
            }
            LayerType.TEXT -> {
               renderTextToBitmap(layer.textContent!!,canvasWidth,canvasHeight)
            }
        }
    }

    private fun loadBitmapFromPath(path: String, layerIdForLog: String = "?"): Bitmap? {
        return try {
            val bitmap = when {
                path.startsWith("content://") -> {
                    Log.d(LayerImageDebug.TAG, "loadBitmapFromPath: branch=content layer=$layerIdForLog")
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
                    Log.d(LayerImageDebug.TAG, "loadBitmapFromPath: branch=file layer=$layerIdForLog")
                    BitmapFactory.decodeFile(path.removePrefix("file://"))
                }
                path.startsWith("http://") || path.startsWith("https://") -> {
                    Log.d(LayerImageDebug.TAG, "loadBitmapFromPath: branch=http layer=$layerIdForLog")
                    HttpBitmapLoader.load(context, path)
                }
                File(path).exists() -> {
                    Log.d(LayerImageDebug.TAG, "loadBitmapFromPath: branch=absoluteFile layer=$layerIdForLog")
                    BitmapFactory.decodeFile(path)
                }
                else -> {
                    Log.w(
                        LayerImageDebug.TAG,
                        "loadBitmapFromPath: branch=NONE (unmatched path) layer=$layerIdForLog " +
                            "path=${LayerImageDebug.pathPreview(path)} exists=${File(path).exists()}"
                    )
                    null
                }
            }
            if (bitmap != null) {
                Log.d(
                    LayerImageDebug.TAG,
                    "loadBitmapFromPath: OK layer=$layerIdForLog ${bitmap.width}x${bitmap.height}"
                )
            } else {
                Log.w(
                    LayerImageDebug.TAG,
                    "loadBitmapFromPath: decode returned NULL layer=$layerIdForLog " +
                        "path=${LayerImageDebug.pathPreview(path)}"
                )
            }
            bitmap
        } catch (e: Exception) {
            Log.e(
                LayerImageDebug.TAG,
                "loadBitmapFromPath: exception layer=$layerIdForLog path=${LayerImageDebug.pathPreview(path)}",
                e
            )
            Log.e("RenderEngine", "Failed to load bitmap: $path", e)
            null
        }
    }

    private fun createSolidBitmap(color: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return bitmap
    }

    private fun renderTextToBitmap(content: TextLayerContent, canvasWidth: Int, canvasHeight: Int): Bitmap {
        val typefaceStyle = when {
            content.isBold && content.isItalic -> android.graphics.Typeface.BOLD_ITALIC
            content.isBold -> android.graphics.Typeface.BOLD
            content.isItalic -> android.graphics.Typeface.ITALIC
            else -> android.graphics.Typeface.NORMAL
        }

        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = content.color
            textSize = content.fontSize * context.resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.create(content.fontFamily, typefaceStyle)
            if (content.isUnderline) isUnderlineText = true
        }

        val lines = content.text.split('\n')
        val maxLineWidth = lines.maxOf { textPaint.measureText(it) }
        val layoutWidth = (maxLineWidth + 1).toInt().coerceAtLeast(1)

        val layout = android.text.StaticLayout.Builder
            .obtain(content.text, 0, content.text.length, textPaint, layoutWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()

        val padding = 4
        val bitmapWidth = (layout.width + padding * 2).coerceAtLeast(1)
        val bitmapHeight = (layout.height + padding * 2).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)

        return bitmap
    }
}
