package com.my_app.art_collab.ui.screens.canvas_editor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.my_app.art_collab.debug.LayerImageDebug
import com.my_app.art_collab.data.image.GalleryExporter
import com.my_app.art_collab.domain.model.BlendMode
import com.my_app.art_collab.domain.model.Effect
import com.my_app.art_collab.domain.model.Layer
import com.my_app.art_collab.domain.model.LayerOp
import com.my_app.art_collab.domain.model.LayerTransform
import com.my_app.art_collab.domain.model.LayerType
import com.my_app.art_collab.domain.model.TextLayerContent
import com.my_app.art_collab.data.repository.asFirebaseFloat
import com.my_app.art_collab.data.repository.asFirebaseInt
import com.my_app.art_collab.domain.usecase.canvas.DeleteLayerImageUseCase
import com.my_app.art_collab.domain.usecase.canvas.FetchLayersUseCase
import com.my_app.art_collab.domain.usecase.canvas.PersistCanvasThumbnailUseCase
import com.my_app.art_collab.domain.usecase.canvas.PushLayerOpUseCase
import com.my_app.art_collab.domain.usecase.canvas.UploadLayerImageUseCase
import com.my_app.art_collab.engine.GeminiApiClient
import com.my_app.art_collab.engine.RenderCache
import com.my_app.art_collab.engine.RenderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface AiGenerationState {
    data object Idle : AiGenerationState
    data object Loading : AiGenerationState
    data object Success : AiGenerationState
    data class Error(val message: String) : AiGenerationState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class CanvasEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val renderEngine: RenderEngine,
    private val renderCache: RenderCache,
    private val firebaseAuth: FirebaseAuth,
    private val uploadLayerImageUseCase: UploadLayerImageUseCase,
    private val deleteLayerImageUseCase: DeleteLayerImageUseCase,
    private val pushLayerOpUseCase: PushLayerOpUseCase,
    private val fetchLayersUseCase: FetchLayersUseCase,
    private val persistCanvasThumbnailUseCase: PersistCanvasThumbnailUseCase,
) : ViewModel() {

    private val _layers = MutableStateFlow<List<Layer>>(emptyList())
    val layers: StateFlow<List<Layer>> = _layers.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _canvasWidth = MutableStateFlow(0)
    private val _canvasHeight = MutableStateFlow(0)

    private val _aiState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val aiState: StateFlow<AiGenerationState> = _aiState.asStateFlow()

    private val _isLoadingLayers = MutableStateFlow(false)
    val isLoadingLayers: StateFlow<Boolean> = _isLoadingLayers.asStateFlow()

    /** True while fetching, preloading remote images, and waiting for the render pipeline to settle. */
    private val _canvasInteractionBlocked = MutableStateFlow(false)
    val canvasInteractionBlocked: StateFlow<Boolean> = _canvasInteractionBlocked.asStateFlow()

    private val _preloadedRemoteBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val preloadedRemoteBitmaps: StateFlow<Map<String, Bitmap>> = _preloadedRemoteBitmaps.asStateFlow()

    val compositedBitmap: StateFlow<Bitmap?> = renderEngine.compositedBitmap
    val isRendering: StateFlow<Boolean> = renderEngine.isRendering

    private val _exportMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportMessages: SharedFlow<String> = _exportMessages.asSharedFlow()
    private val _layerBlobUrls = mutableMapOf<String, String>()

    private val kickFromCanvasPending = AtomicBoolean(false)
    private val _kickedFromCanvasOverlay = MutableStateFlow(false)
    val kickedFromCanvasOverlay: StateFlow<Boolean> = _kickedFromCanvasOverlay.asStateFlow()
    private val _exitCanvasAfterKick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitCanvasAfterKick: SharedFlow<Unit> = _exitCanvasAfterKick.asSharedFlow()

    private val transformThrottle = OpThrottle(intervalMs = 50L)
    private val effectThrottle = OpThrottle(intervalMs = 50L)
    private var opsListenerJob: Job? = null
    private var thumbnailIdleJob: Job? = null

    private val thumbnailSaveMutex = Mutex()
    private var lastSavedCompositeGeneration: Long = -1L

    /** Ensures [loadCanvasLayers] and [refreshHydrationAfterBackground] never interleave (clearing preload/cache). */
    private val canvasHydrationMutex = Mutex()

    init {
        viewModelScope.launch {
            renderEngine.startRenderLoop()
        }

        viewModelScope.launch {
            _layers.collect { layers ->
                val width = _canvasWidth.value
                val height = _canvasHeight.value
                if (width > 0 && height > 0) {
                    renderEngine.requestRender(layers, width, height)
                }
            }
        }
    }
    private fun Effect.toPayloadMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "effectId" to id,
            "isEnabled" to isEnabled
        )
        when (this) {
            is Effect.BrightnessContrast -> {
                base["effectType"] = "brightness_contrast"
                base["brightness"] = brightness.toDouble()
                base["contrast"] = contrast.toDouble()
            }
            is Effect.Exposure -> {
                base["effectType"] = "exposure"
                base["stops"] = stops.toDouble()
            }
            is Effect.GaussianBlur -> {
                base["effectType"] = "gaussian_blur"
                base["radius"] = radius.toDouble()
            }
            is Effect.Sharpen -> {
                base["effectType"] = "sharpen"
                base["amount"] = amount.toDouble()
            }
            is Effect.Vignette -> {
                base["effectType"] = "vignette"
                base["intensity"] = intensity.toDouble()
                base["feather"] = feather.toDouble()
            }
            is Effect.Saturation -> {
                base["effectType"] = "saturation"
                base["amount"] = amount.toDouble()
            }
            is Effect.ColorTemperature -> {
                base["effectType"] = "color_temperature"
                base["temperature"] = temperature.toDouble()
                base["tint"] = tint.toDouble()
            }
            is Effect.Grain -> {
                base["effectType"] = "grain"
                base["amount"] = amount.toDouble()
                base["size"] = size.toDouble()
            }
            is Effect.Pixelate -> {
                base["effectType"] = "pixelate"
                base["blockSize"] = blockSize.toDouble()
            }
        }
        return base
    }

    fun setCanvasSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _canvasWidth.value = width
            _canvasHeight.value = height
            renderEngine.invalidateAll()
            renderEngine.requestRender(_layers.value, width, height)
        }
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAD LAYERS FROM FIREBASE ON CANVAS OPEN
    // ══════════════════════════════════════════════════════════════════════════

    fun loadCanvasLayers(canvasId: String) {
        opsListenerJob?.cancel()
        kickFromCanvasPending.set(false)
        _kickedFromCanvasOverlay.value = false
        val joinTime = System.currentTimeMillis()
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        // Fetch baseline layer list first, then subscribe to ops. If both run in parallel,
        // a slow fetch can overwrite _layers after remote ops were already applied (dropping
        // collaborator-added solid/text/image layers until reload).
        viewModelScope.launch {
            canvasHydrationMutex.withLock {
                lastSavedCompositeGeneration = -1L
                _canvasInteractionBlocked.value = true
                _preloadedRemoteBitmaps.value = emptyMap()
                _isLoadingLayers.value = true
                var fetchOk = false
                try {
                    val remoteLayers = fetchLayersUseCase(canvasId)
                    _layers.value = remoteLayers
                    renderEngine.invalidateAll()
                    fetchOk = true
                    remoteLayers.filter { it.type == LayerType.IMAGE || it.type == LayerType.AI_GENERATED }.forEach { l ->
                        Log.d(
                            LayerImageDebug.TAG,
                            "loadCanvasLayers: in-memory layer id=${l.id} path=${LayerImageDebug.pathPreview(l.sourceBitmapPath)} " +
                                "effects=${l.effectChain.size}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(LayerImageDebug.TAG, "loadCanvasLayers: fetch failed", e)
                    e.printStackTrace()
                } finally {
                    _isLoadingLayers.value = false
                }

                opsListenerJob = viewModelScope.launch {
                    fetchLayersUseCase.observeOps(canvasId, joinTime).collect { op ->
                        if (op.type == "delete_collab") {
                            val target = op.payload["targetUserId"] as? String
                            if (currentUserId.isNotEmpty() && target == currentUserId) {
                                onKickedAsCollaborator()
                            }
                            return@collect
                        }
                        if (op.userId != currentUserId) {
                            applyRemoteOp(op)
                        }
                    }
                }

                try {
                    if (fetchOk) {
                        val hydrationOk = withTimeoutOrNull(HYDRATION_TIMEOUT_MS) {
                            preloadRemoteLayerImages(_layers.value)
                            awaitRenderSettled()
                        }
                        if (hydrationOk == null) {
                            Log.w(
                                LayerImageDebug.TAG,
                                "loadCanvasLayers: hydration TIMEOUT ${HYDRATION_TIMEOUT_MS}ms canvasId=$canvasId " +
                                    "(preload/render may be incomplete)"
                            )
                        } else {
                            Log.d(LayerImageDebug.TAG, "loadCanvasLayers: hydration finished canvasId=$canvasId")
                        }
                    }
                } finally {
                    _canvasInteractionBlocked.value = false
                }
            }
            persistThumbnailSnapshot(canvasId)
        }
    }

    /**
     * Re-fetch layers, preload remote images, and wait for render after the app returns from
     * background (see [androidx.lifecycle.ProcessLifecycleOwner]).
     */
    fun refreshHydrationAfterBackground(canvasId: String) {
        viewModelScope.launch {
            canvasHydrationMutex.withLock {
                _canvasInteractionBlocked.value = true
                _preloadedRemoteBitmaps.value = emptyMap()
                try {
                    var fetchOk = false
                    val hydrationOk = withTimeoutOrNull(HYDRATION_TIMEOUT_MS) {
                        try {
                            val remoteLayers = fetchLayersUseCase(canvasId)
                            _layers.value = remoteLayers
                            renderEngine.invalidateAll()
                            fetchOk = true
                        } catch (e: Exception) {
                            Log.e(LayerImageDebug.TAG, "refreshHydrationAfterBackground: fetch failed", e)
                            e.printStackTrace()
                        }
                        if (fetchOk) {
                            preloadRemoteLayerImages(_layers.value)
                            awaitRenderSettled()
                        }
                    }
                    if (hydrationOk == null) {
                        Log.w(LayerImageDebug.TAG, "refreshHydrationAfterBackground: TIMEOUT ${HYDRATION_TIMEOUT_MS}ms")
                    }
                } finally {
                    _canvasInteractionBlocked.value = false
                }
            }
            persistThumbnailSnapshot(canvasId)
        }
    }

    /**
     * Debounced on [RenderEngine.compositeGeneration] — one save after ~[THUMBNAIL_IDLE_DEBOUNCE_MS] quiet.
     */
    fun startIdleThumbnailCollector(canvasId: String) {
        thumbnailIdleJob?.cancel()
        thumbnailIdleJob = viewModelScope.launch {
            renderEngine.compositeGeneration
                .debounce(THUMBNAIL_IDLE_DEBOUNCE_MS)
                .collect {
                    if (!_canvasInteractionBlocked.value) {
                        persistThumbnailSnapshot(canvasId)
                    }
                }
        }
    }

    fun onLocalLifecycleStopThumbnail(canvasId: String) {
        viewModelScope.launch {
            persistThumbnailSnapshot(canvasId)
        }
    }

    suspend fun awaitPersistThumbnailForLeave(canvasId: String) {
        persistThumbnailSnapshot(canvasId)
    }

    private suspend fun persistThumbnailSnapshot(canvasId: String) {
        if (canvasId.isBlank()) return
        thumbnailSaveMutex.withLock {
            while (true) {
                val gen = renderEngine.compositeGeneration.value
                if (gen <= lastSavedCompositeGeneration) return@withLock
                val source = renderEngine.compositedBitmap.value
                if (source == null || source.isRecycled) {
                    lastSavedCompositeGeneration = gen
                    return@withLock
                }
                val copy = withContext(Dispatchers.Default) {
                    source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
                }
                try {
                    persistCanvasThumbnailUseCase(canvasId, copy)
                    lastSavedCompositeGeneration = gen
                } catch (e: Exception) {
                    Log.w(LayerImageDebug.TAG, "persistThumbnailSnapshot failed canvasId=$canvasId", e)
                    return@withLock
                } finally {
                    copy.recycle()
                }
            }
        }
    }

    private suspend fun preloadRemoteLayerImages(layers: List<Layer>) {
        val targets = layers.mapNotNull { l ->
            val p = l.sourceBitmapPath ?: return@mapNotNull null
            if (l.type != LayerType.IMAGE && l.type != LayerType.AI_GENERATED) return@mapNotNull null
            if (!p.startsWith("http://") && !p.startsWith("https://")) return@mapNotNull null
            l.id to p
        }
        if (targets.isEmpty()) {
            Log.d(
                LayerImageDebug.TAG,
                "preloadRemoteLayerImages: no http(s) image targets (layers=${layers.size}) " +
                    "(paths may be empty, local file, or content://)"
            )
            _preloadedRemoteBitmaps.value = emptyMap()
            return
        }
        Log.d(LayerImageDebug.TAG, "preloadRemoteLayerImages: loading ${targets.size} URL(s)")
        val loaded = coroutineScope {
            targets.map { (id, url) ->
                async(Dispatchers.IO) {
                    val bmp = ImageCache.loadBitmapFromHttpUrl(appContext, url)
                    if (bmp != null) {
                        Log.d(
                            LayerImageDebug.TAG,
                            "preloadRemoteLayerImages: OK layerId=$id ${bmp.width}x${bmp.height} url=${LayerImageDebug.pathPreview(url)}"
                        )
                        id to bmp
                    } else {
                        Log.w(
                            LayerImageDebug.TAG,
                            "preloadRemoteLayerImages: FAIL layerId=$id url=${LayerImageDebug.pathPreview(url)}"
                        )
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        Log.d(
            LayerImageDebug.TAG,
            "preloadRemoteLayerImages: done loaded=${loaded.size}/${targets.size} ids=${loaded.keys}"
        )
        _preloadedRemoteBitmaps.value = loaded
    }

    private suspend fun awaitRenderSettled() {
        val w = _canvasWidth.value
        val h = _canvasHeight.value
        if (w <= 0 || h <= 0) {
            Log.w(
                LayerImageDebug.TAG,
                "awaitRenderSettled: canvas size not set yet w=$w h=$h — render may be skipped"
            )
            delay(120)
            return
        }
        renderEngine.requestRender(_layers.value, w, h)
        yield()
        var idleFrames = 0
        while (idleFrames < 4) {
            delay(16)
            if (!isRendering.value) idleFrames++ else idleFrames = 0
        }
    }

    /**
     * Text layers bake pinch-scale into [TextLayerContent.fontSize] on finger-up locally
     * ([finalizeTextSizeAfterGesture]); collaborators only see [transform] ops, so we also
     * send the authoritative font size on every text transform or their scale resets to 1
     * without a matching font bump (size "pops" wrong).
     */
    private fun buildTransformPayload(layer: Layer, transform: LayerTransform): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "x" to transform.translateX.toDouble(),
            "y" to transform.translateY.toDouble(),
            "scaleX" to transform.scaleX.toDouble(),
            "scaleY" to transform.scaleY.toDouble()
        )
        val tc = layer.textContent
        if (layer.type == LayerType.TEXT && tc != null) {
            payload["textFontSize"] = tc.fontSize.toDouble()
        }
        return payload
    }

    /** Initial transform for new layers in layer_add ops (collaborators only see /ops, not live /layers). */
    private fun transformFromLayerAddPayload(payload: Map<String, Any>): LayerTransform =
        LayerTransform(
            translateX = payload["x"].asFirebaseFloat() ?: 0f,
            translateY = payload["y"].asFirebaseFloat() ?: 0f,
            scaleX = payload["scaleX"].asFirebaseFloat() ?: 1f,
            scaleY = payload["scaleY"].asFirebaseFloat() ?: 1f
        )

    private fun transformFieldsForLayerAdd(t: LayerTransform): Map<String, Any> = mapOf(
        "x" to t.translateX.toDouble(),
        "y" to t.translateY.toDouble(),
        "scaleX" to t.scaleX.toDouble(),
        "scaleY" to t.scaleY.toDouble()
    )

    private fun onKickedAsCollaborator() {
        if (!kickFromCanvasPending.compareAndSet(false, true)) return
        _kickedFromCanvasOverlay.value = true
        viewModelScope.launch {
            delay(5_000L)
            _kickedFromCanvasOverlay.value = false
            _exitCanvasAfterKick.emit(Unit)
        }
    }

    private fun applyRemoteOp(op: LayerOp) {
        when (op.type) {
            "transform" -> {
                val payload = op.payload
                val remoteTextFontSize = payload["textFontSize"].asFirebaseFloat()
                _layers.value = _layers.value.map { layer ->
                    if (layer.id != op.layerId) return@map layer
                    val withFont = if (
                        layer.type == LayerType.TEXT &&
                        layer.textContent != null &&
                        remoteTextFontSize != null
                    ) {
                        layer.copy(
                            textContent = layer.textContent!!.copy(fontSize = remoteTextFontSize),
                            updatedAt = op.timestamp
                        )
                    } else {
                        layer
                    }
                    withFont.copy(
                        transform = withFont.transform.copy(
                            translateX = payload["x"].asFirebaseFloat() ?: withFont.transform.translateX,
                            translateY = payload["y"].asFirebaseFloat() ?: withFont.transform.translateY,
                            scaleX = payload["scaleX"].asFirebaseFloat() ?: withFont.transform.scaleX,
                            scaleY = payload["scaleY"].asFirebaseFloat() ?: withFont.transform.scaleY
                        ),
                        updatedAt = op.timestamp
                    )
                }
                if (remoteTextFontSize != null) {
                    renderEngine.invalidateLayer(op.layerId)
                }
            }
            "opacity" -> {
                val value = (op.payload["value"] as? Number)?.toFloat() ?: return
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) layer.copy(opacity = value, updatedAt = op.timestamp) else layer
                }
            }
            "blend_mode" -> {
                val modeName = op.payload["mode"] as? String ?: return
                val blendMode = try {
                    BlendMode.valueOf(modeName.uppercase())
                } catch (_: Exception) { BlendMode.NORMAL }
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) layer.copy(blendMode = blendMode, updatedAt = op.timestamp) else layer
                }
            }
            "effect_add" -> {
                val effect = parseEffectFromPayload(op.payload) ?: return
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) {
                        layer.copy(
                            effectChain = layer.effectChain + effect,
                            updatedAt = op.timestamp
                        )
                    } else layer
                }
                renderEngine.invalidateLayer(op.layerId)
            }
            "effect_update" -> {
                val effect = parseEffectFromPayload(op.payload) ?: return
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) {
                        layer.copy(
                            effectChain = layer.effectChain.map { if (it.id == effect.id) effect else it },
                            updatedAt = op.timestamp
                        )
                    } else layer
                }
                renderEngine.invalidateLayer(op.layerId)
            }
            "effect_remove" -> {
                val effectId = op.payload["effectId"] as? String ?: return
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) {
                        layer.copy(
                            effectChain = layer.effectChain.filter { it.id != effectId },
                            updatedAt = op.timestamp
                        )
                    } else layer
                }
                renderEngine.invalidateLayer(op.layerId)
            }
            "effect_toggle" -> {
                val effectId = op.payload["effectId"] as? String ?: return
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == op.layerId) {
                        layer.copy(
                            effectChain = layer.effectChain.map { effect ->
                                if (effect.id == effectId) toggleEffectEnabled(effect) else effect
                            },
                            updatedAt = op.timestamp
                        )
                    } else layer
                }
                renderEngine.invalidateLayer(op.layerId)
            }
            "layer_add" -> {
                val blobUrl = op.payload["blobUrl"] as? String
                val order = op.payload["order"].asFirebaseInt() ?: 0
                val typeStr = op.payload["type"] as? String ?: "image"
                val layerType = when (typeStr) {
                    "image" -> LayerType.IMAGE
                    "solid_color" -> LayerType.SOLID_COLOR
                    "text" -> LayerType.TEXT
                    "ai_generated" -> LayerType.AI_GENERATED
                    else -> LayerType.IMAGE
                }

                val solidColor = op.payload["color"].asFirebaseInt()

                val textContent = if (layerType == LayerType.TEXT) {
                    TextLayerContent(
                        text = op.payload["text"] as? String ?: "",
                        fontFamily = op.payload["fontFamily"] as? String ?: "sans-serif",
                        fontSize = op.payload["fontSize"].asFirebaseFloat() ?: 12f,
                        color = op.payload["color"].asFirebaseInt() ?: 0xFF000000.toInt(),
                        isBold = op.payload["isBold"] as? Boolean ?: false,
                        isItalic = op.payload["isItalic"] as? Boolean ?: false,
                        isUnderline = op.payload["isUnderline"] as? Boolean ?: false
                    )
                } else null

                val existingIds = _layers.value.map { it.id }.toSet()
                if (op.layerId !in existingIds) {
                    val newLayer = Layer(
                        id = op.layerId,
                        canvasId = "",
                        ownerId = op.userId,
                        name = "Layer ${_layers.value.size + 1}",
                        type = layerType,
                        sourceBitmapPath = blobUrl,
                        zIndex = order,
                        transform = transformFromLayerAddPayload(op.payload),
                        solidColor = if (layerType == LayerType.SOLID_COLOR) solidColor else null,
                        textContent = textContent,
                        updatedAt = op.timestamp
                    )
                    _layers.value = (_layers.value + newLayer).sortedBy { it.zIndex }
                    renderEngine.invalidateLayer(newLayer.id)
                }
            }
            "layer_remove" -> {
                _layers.value = _layers.value.filterNot { it.id == op.layerId }
                renderEngine.invalidateLayer(op.layerId)
            }
        }
    }

    private fun parseEffectFromPayload(payload: Map<String, Any>): Effect? {
        val effectId = payload["effectId"] as? String ?: return null
        val effectType = payload["effectType"] as? String ?: return null
        val isEnabled = payload["isEnabled"] as? Boolean ?: true

        return when (effectType) {
            "brightness_contrast" -> Effect.BrightnessContrast(
                id = effectId, isEnabled = isEnabled,
                brightness = (payload["brightness"] as? Number)?.toFloat() ?: 0f,
                contrast = (payload["contrast"] as? Number)?.toFloat() ?: 0f
            )
            "exposure" -> Effect.Exposure(
                id = effectId, isEnabled = isEnabled,
                stops = (payload["stops"] as? Number)?.toFloat() ?: 0f
            )
            "gaussian_blur" -> Effect.GaussianBlur(
                id = effectId, isEnabled = isEnabled,
                radius = (payload["radius"] as? Number)?.toFloat() ?: 5f
            )
            "sharpen" -> Effect.Sharpen(
                id = effectId, isEnabled = isEnabled,
                amount = (payload["amount"] as? Number)?.toFloat() ?: 0.5f
            )
            "vignette" -> Effect.Vignette(
                id = effectId, isEnabled = isEnabled,
                intensity = (payload["intensity"] as? Number)?.toFloat() ?: 0.5f,
                feather = (payload["feather"] as? Number)?.toFloat() ?: 0.5f
            )
            "saturation" -> Effect.Saturation(
                id = effectId, isEnabled = isEnabled,
                amount = (payload["amount"] as? Number)?.toFloat() ?: 0f
            )
            "color_temperature" -> Effect.ColorTemperature(
                id = effectId, isEnabled = isEnabled,
                temperature = (payload["temperature"] as? Number)?.toFloat() ?: 0f,
                tint = (payload["tint"] as? Number)?.toFloat() ?: 0f
            )
            "grain" -> Effect.Grain(
                id = effectId, isEnabled = isEnabled,
                amount = (payload["amount"] as? Number)?.toFloat() ?: 0.3f,
                size = (payload["size"] as? Number)?.toFloat() ?: 1f
            )
            "pixelate" -> Effect.Pixelate(
                id = effectId, isEnabled = isEnabled,
                blockSize = (payload["blockSize"] as? Number)?.toFloat() ?: 2f
            )
            else -> null
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LAYER CRUD
    // ══════════════════════════════════════════════════════════════════════════

    fun addImageLayer(canvasId: String, imagePath: String, imageWidth: Int, imageHeight: Int, isAi: Boolean = false) {
        val current = _layers.value
        val canvasW = _canvasWidth.value
        val canvasH = _canvasHeight.value
        
        val initialScale = if (canvasW > 0 && canvasH > 0 && imageWidth > 0 && imageHeight > 0) {
            val targetWidth = canvasW * 0.9f
            val targetHeight = canvasH * 0.9f
            val scaleToFitWidth = targetWidth / imageWidth
            val scaleToFitHeight = targetHeight / imageHeight
            minOf(scaleToFitWidth, scaleToFitHeight)
        } else {
            1f
        }
        
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            canvasId = canvasId,
            ownerId = "",
            name = if (isAi) "AI ${current.size + 1}" else "Image ${current.size + 1}",
            type = if (isAi) LayerType.AI_GENERATED else LayerType.IMAGE,
            zIndex = (current.maxOfOrNull { it.zIndex } ?: -1) + 1,
            sourceBitmapPath = imagePath,
            transform = LayerTransform(scaleX = initialScale, scaleY = initialScale),
            updatedAt = System.currentTimeMillis()
        )
        _layers.value = current + newLayer
        _selectedLayerId.value = newLayer.id

        viewModelScope.launch {
            try {
                val blobUrl = uploadLayerImageUseCase(canvasId, newLayer.id, imagePath)
                _layerBlobUrls[newLayer.id] = blobUrl

                // Match collaborator model: same Storage URL everywhere (AI temp file can be GC'd).
                _layers.value = _layers.value.map { layer ->
                    if (layer.id == newLayer.id) {
                        layer.copy(sourceBitmapPath = blobUrl, updatedAt = System.currentTimeMillis())
                    } else layer
                }
                renderEngine.invalidateLayer(newLayer.id)

                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = newLayer.id,
                    type = "layer_add",
                    payload = buildMap {
                        put("blobUrl", blobUrl)
                        put("order", newLayer.zIndex)
                        put("type", if (isAi) "ai_generated" else "image")
                        putAll(transformFieldsForLayerAdd(newLayer.transform))
                    }
                )
                val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(canvasId, newLayer, blobUrl)
                pushLayerOpUseCase(canvasId, op, snapshotUpdate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNewTextLayer(canvasId: String, text: String, fontFamily: String, color: Int, isBold: Boolean, isItalic: Boolean, isUnderline: Boolean) {
        val current = _layers.value
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            canvasId = canvasId,
            ownerId = "",
            name = "Text ${current.size + 1}",
            type = LayerType.TEXT,
            zIndex = (current.maxOfOrNull { it.zIndex } ?: -1) + 1,
            textContent = TextLayerContent(
                text = text,
                fontFamily = fontFamily,
                color = color,
                isBold = isBold,
                isItalic = isItalic,
                isUnderline = isUnderline
            ),
            updatedAt = System.currentTimeMillis()
        )
        _layers.value = current + newLayer
        _selectedLayerId.value = newLayer.id

        viewModelScope.launch {
            val op = LayerOp(
                userId = firebaseAuth.currentUser?.uid ?: "",
                layerId = newLayer.id,
                type = "layer_add",
                payload = mapOf(
                    "type" to "text",
                    "order" to newLayer.zIndex,
                    "text" to text,
                    "fontFamily" to fontFamily,
                    "fontSize" to newLayer.textContent!!.fontSize,
                    "color" to color,
                    "isBold" to isBold,
                    "isItalic" to isItalic,
                    "isUnderline" to isUnderline
                )
            )
            val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(canvasId, newLayer, null)
            pushLayerOpUseCase(canvasId, op, snapshotUpdate)
        }
    }

    fun addSolidColorLayer(canvasId: String, color: Int) {
        val current = _layers.value
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            canvasId = canvasId,
            ownerId = "",
            name = "Color Fill ${current.size + 1}",
            type = LayerType.SOLID_COLOR,
            zIndex = (current.maxOfOrNull { it.zIndex } ?: -1) + 1,
            solidColor = color,
            updatedAt = System.currentTimeMillis()
        )
        _layers.value = current + newLayer
        _selectedLayerId.value = newLayer.id

        viewModelScope.launch {
            val op = LayerOp(
                userId = firebaseAuth.currentUser?.uid ?: "",
                layerId = newLayer.id,
                type = "layer_add",
                payload = mapOf(
                    "type" to "solid_color",
                    "order" to newLayer.zIndex,
                    "color" to color
                )
            )
            val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(canvasId, newLayer, null)
            pushLayerOpUseCase(canvasId, op, snapshotUpdate)
        }
    }

        fun generateAiLayer(canvasId: String, prompt: String) {
            viewModelScope.launch {
                _aiState.value = AiGenerationState.Loading
                try {
                    val fullPrompt = "Generate exactly 1 image, no text in response. " +
                            "Keep it simple and direct. $prompt"
                    val bitmap = GeminiApiClient.generateImage(fullPrompt)

                    val file = File(appContext.filesDir, "ai_${UUID.randomUUID()}.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                    addImageLayer(
                        canvasId,
                        file.absolutePath,
                        bitmap.width,
                        bitmap.height,
                        isAi = true
                    )
                    _aiState.value = AiGenerationState.Success
                } catch (e: Exception) {
                    _aiState.value = AiGenerationState.Error(e.message ?: "Generation failed")
                }
            }
        }

        fun clearAiError() {
            _aiState.value = AiGenerationState.Idle
        }

        fun deleteLayer(canvasId: String, layerId: String) {
            val targetLayer = _layers.value.find { it.id == layerId }
            _layers.value = _layers.value.filterNot { it.id == layerId }
            renderCache.remove(layerId)
            renderEngine.invalidateLayer(layerId)
            if (_selectedLayerId.value == layerId) {
                _selectedLayerId.value = _layers.value.lastOrNull()?.id
            }
            _layerBlobUrls.remove(layerId)

            val path = targetLayer?.sourceBitmapPath
            val deleteStorageFile = targetLayer != null && (
                targetLayer.type == LayerType.IMAGE ||
                    targetLayer.type == LayerType.AI_GENERATED ||
                    (!path.isNullOrBlank() &&
                        (path.startsWith("http://") || path.startsWith("https://")))
                )

            viewModelScope.launch {
                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = layerId,
                    type = "layer_remove",
                    payload = emptyMap()
                )
                val snapshotUpdate = mapOf<String, Any?>(
                    "canvases/$canvasId/layers/$layerId" to null
                )
                try {
                    pushLayerOpUseCase(canvasId, op, snapshotUpdate)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (deleteStorageFile) {
                    try {
                        deleteLayerImageUseCase(canvasId, layerId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        /** After pinch/scale gesture, fold uniform scale into font size (matches rendered size). */
        fun finalizeTextSizeAfterGesture(layerId: String) {
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId && layer.type == LayerType.TEXT && layer.textContent != null) {
                    val avgScale = (layer.transform.scaleX + layer.transform.scaleY) / 2f
                    if (avgScale != 1f) {
                        val newFontSize = (layer.textContent.fontSize * avgScale).coerceIn(4f, 200f)
                        renderEngine.invalidateLayer(layerId)
                        layer.copy(
                            transform = layer.transform.copy(scaleX = 1f, scaleY = 1f),
                            textContent = layer.textContent.copy(fontSize = newFontSize),
                            updatedAt = System.currentTimeMillis()
                        )
                    } else layer
                } else layer
            }
        }

        // ══════════════════════════════════════════════════════════════════════════
        // TRANSFORM
        // ══════════════════════════════════════════════════════════════════════════

        fun updateLayerTransform(canvasId: String,layerId: String, transform: LayerTransform) {
            var updatedLayer: Layer? = null

            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(transform = transform, updatedAt = System.currentTimeMillis()).also {
                        updatedLayer = it
                    }
                } else layer
            }
            val layer = updatedLayer ?: return
            val op = LayerOp(
                userId = firebaseAuth.currentUser?.uid ?: "",
                layerId = layerId,
                type = "transform",
                payload = buildTransformPayload(layer, transform)
            )

            val snapShotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                canvasId, layer, _layerBlobUrls[layerId]
            )
            transformThrottle.onOp(op,snapShotUpdate) {
                throttledOp, throttledSnapshot ->
                viewModelScope.launch {
                    pushLayerOpUseCase(canvasId, throttledOp, throttledSnapshot)
                }
            }

        }

    fun finalizeTransform(canvasId: String, layerId: String) {
        // Bake text pinch-scale into fontSize before building the op/snapshot so Firebase gets
        // the same layer state collaborators must apply (avoids scale=1 without font bump).
        finalizeTextSizeAfterGesture(layerId)
        val layer = _layers.value.find { it.id == layerId } ?: return

        val op = LayerOp(
            userId = firebaseAuth.currentUser?.uid ?: "",
            layerId = layerId,
            type = "transform",
            payload = buildTransformPayload(layer, layer.transform)
        )
        val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
            canvasId, layer, _layerBlobUrls[layerId]
        )
        transformThrottle.onFinalOp(op, snapshotUpdate) { finalOp, finalSnapshot ->
            viewModelScope.launch {
                pushLayerOpUseCase(canvasId, finalOp, finalSnapshot)
            }
        }
    }

        // ══════════════════════════════════════════════════════════════════════════
        // OPACITY & BLEND MODE
        // ══════════════════════════════════════════════════════════════════════════

        fun setLayerOpacity(canvasId: String,layerId: String, opacity: Float) {
            var updatedLayer: Layer? = null
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(
                        opacity = opacity.coerceIn(0f, 1f),
                        updatedAt = System.currentTimeMillis()
                    ).also {
                        updatedLayer = it
                    }
                } else layer
            }

            viewModelScope.launch {
                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = layerId,
                    type = "opacity",
                    payload = mapOf("value" to opacity.toDouble())
                )
                val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                    canvasId, updatedLayer!!, _layerBlobUrls[layerId]
                )
                pushLayerOpUseCase(canvasId, op, snapshotUpdate)
            }
        }


    fun setBlendMode(canvasId: String, layerId: String, blendMode: BlendMode) {
        var updatedLayer: Layer? = null
        _layers.value = _layers.value.map { layer ->
            if (layer.id == layerId) {
                layer.copy(blendMode = blendMode, updatedAt = System.currentTimeMillis()).also {
                    updatedLayer = it
                }
            } else layer
        }
        val layer = updatedLayer ?: return
        viewModelScope.launch {
            val op = LayerOp(
                userId = firebaseAuth.currentUser?.uid ?: "",
                layerId = layerId,
                type = "blend_mode",
                payload = mapOf("mode" to blendMode.name.lowercase())
            )
            val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                canvasId, layer, _layerBlobUrls[layerId]
            )
            pushLayerOpUseCase(canvasId, op, snapshotUpdate)
        }
    }
        // ══════════════════════════════════════════════════════════════════════════
        // EFFECTS
        // ══════════════════════════════════════════════════════════════════════════

        fun addEffect(canvasId: String, layerId: String, effect: Effect) {
            var updatedLayer: Layer? = null
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(
                        effectChain = layer.effectChain + effect,
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedLayer = it }
                } else layer
            }
            renderEngine.invalidateLayer(layerId)

            val layer = updatedLayer ?: return
            viewModelScope.launch {
                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = layerId,
                    type = "effect_add",
                    payload = effect.toPayloadMap()
                )
                val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                    canvasId, layer, _layerBlobUrls[layerId]
                )
                pushLayerOpUseCase(canvasId, op, snapshotUpdate)
            }
        }

        fun updateEffect(canvasId: String, layerId: String, updatedEffect: Effect) {
            var updatedLayer: Layer? = null
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(
                        effectChain = layer.effectChain.map {
                            if (it.id == updatedEffect.id) updatedEffect else it
                        },
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedLayer = it }
                } else layer
            }
            renderEngine.invalidateLayer(layerId)

            val layer = updatedLayer ?: return
            val op = LayerOp(
                userId = firebaseAuth.currentUser?.uid ?: "",
                layerId = layerId,
                type = "effect_update",
                payload = updatedEffect.toPayloadMap()
            )
            val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                canvasId, layer, _layerBlobUrls[layerId]
            )
            effectThrottle.onOp(op, snapshotUpdate) { throttledOp, throttledSnapshot ->
                viewModelScope.launch {
                    pushLayerOpUseCase(canvasId, throttledOp, throttledSnapshot)
                }
            }
        }

        fun removeEffect(canvasId: String, layerId: String, effectId: String) {
            var updatedLayer: Layer? = null
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(
                        effectChain = layer.effectChain.filter { it.id != effectId },
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedLayer = it }
                } else layer
            }
            renderEngine.invalidateLayer(layerId)

            val layer = updatedLayer ?: return
            viewModelScope.launch {
                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = layerId,
                    type = "effect_remove",
                    payload = mapOf("effectId" to effectId)
                )
                val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                    canvasId, layer, _layerBlobUrls[layerId]
                )
                pushLayerOpUseCase(canvasId, op, snapshotUpdate)
            }
        }

        fun toggleEffect(canvasId: String, layerId: String, effectId: String) {
            var updatedLayer: Layer? = null
            _layers.value = _layers.value.map { layer ->
                if (layer.id == layerId) {
                    layer.copy(
                        effectChain = layer.effectChain.map { effect ->
                            if (effect.id == effectId) {
                                toggleEffectEnabled(effect)
                            } else effect
                        },
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedLayer = it }
                } else layer
            }
            renderEngine.invalidateLayer(layerId)

            val layer = updatedLayer ?: return
            viewModelScope.launch {
                val op = LayerOp(
                    userId = firebaseAuth.currentUser?.uid ?: "",
                    layerId = layerId,
                    type = "effect_toggle",
                    payload = mapOf("effectId" to effectId)
                )
                val snapshotUpdate = pushLayerOpUseCase.buildLayerDataMap(
                    canvasId, layer, _layerBlobUrls[layerId]
                )
                pushLayerOpUseCase(canvasId, op, snapshotUpdate)
            }
        }

        private fun toggleEffectEnabled(effect: Effect): Effect {
            return when (effect) {
                is Effect.BrightnessContrast -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.GaussianBlur -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Saturation -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Vignette -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Sharpen -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Exposure -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.ColorTemperature -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Grain -> effect.copy(isEnabled = !effect.isEnabled)
                is Effect.Pixelate -> effect.copy(isEnabled = !effect.isEnabled)
            }
        }

        fun getProcessedBitmap(layerId: String): Bitmap? = renderCache.get(layerId)

    override fun onCleared() {
        super.onCleared()
        thumbnailIdleJob?.cancel()
        opsListenerJob?.cancel()
        renderEngine.invalidateAll()
    }

    companion object {
        private const val HYDRATION_TIMEOUT_MS = 60_000L
        private const val THUMBNAIL_IDLE_DEBOUNCE_MS = 1500L
    }
    fun exportCompositeToGallery(canvasDisplayName: String){
        viewModelScope.launch {
            val bitmap = compositedBitmap.value
            if(bitmap==null){
                _exportMessages.emit("Nothing to export yet")
                return@launch
            }
            val result = withContext(Dispatchers.IO){
                GalleryExporter.savePngLossless(appContext,bitmap,canvasDisplayName)
            }
            result.fold(
                onSuccess = {_exportMessages.emit("Saved to photos")},
                onFailure = {e -> _exportMessages.emit("Export failed: ${e.message}") }
            )
        }
    }
}
