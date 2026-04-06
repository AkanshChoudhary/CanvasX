# CanvasX Architecture — How Image Editing Actually Works

A detailed walkthrough of how images are loaded, displayed, moved, resized, and have effects applied. Every file mentioned, every function called, in exact order.

---

## 1. The Big Picture

There are two parallel systems working together:

```
                    ┌──────────────────────────────────────────────┐
                    │           DISPLAY PATH (instant)             │
                    │  Compose Canvas draws per-layer bitmaps      │
                    │  with transforms applied via drawImage()     │
                    │  Uses effect-processed bitmaps from cache    │
                    └──────────────────┬───────────────────────────┘
                                       │ reads from
                    ┌──────────────────▼───────────────────────────┐
                    │          PROCESSING PATH (async)             │
                    │  RenderEngine runs on background thread      │
                    │  Applies AGSL shaders via GPU → RenderCache  │
                    │  Emits compositedBitmap to trigger redraw    │
                    └──────────────────────────────────────────────┘
```

**Why two paths?** If we only had the async path, dragging an image would feel laggy (GPU processing takes time, image trails behind your finger). If we only had the sync path, we couldn't use AGSL shaders (they need hardware rendering which can't run synchronously in Compose's `DrawScope`). The hybrid gives us instant gesture feedback AND GPU-powered effects.

---

## 2. File Map — What Lives Where

```
domain/model/
    Layer.kt              ← Data class: id, transform, effectChain, opacity, etc.
    LayerTransform.kt     ← Data class: translateX/Y, scaleX/Y, rotation, flip
    Effect.kt             ← Sealed class: BrightnessContrast, GaussianBlur, etc.

engine/
    RenderEngine.kt       ← Orchestrator: render loop, loads bitmaps, coordinates processors
    EffectProcessor.kt    ← Applies effect chain (AGSL shaders) to a single layer bitmap
    HardwareShaderRenderer.kt ← GPU bridge: RenderNode + HardwareRenderer + ImageReader
    AgslShaders.kt        ← Raw AGSL shader source strings (the actual GPU programs)
    BlendModeProcessor.kt ← Composites two bitmaps with a blend mode
    TransformProcessor.kt ← Applies translate/scale/rotate/flip via android.graphics.Matrix
    MaskProcessor.kt      ← Multiplies layer alpha by a mask bitmap
    DirtyFlagTracker.kt   ← Tracks which layers need re-processing (hash comparison)
    RenderCache.kt        ← LruCache<String, Bitmap> of processed layer bitmaps

di/
    EngineModule.kt       ← Hilt: wires all engine classes as @Singleton

ui/screens/canvas_editor/
    CanvasEditorScreen.kt ← Top-level Composable: Scaffold, toolbar, sheets
    CanvasEditorViewModel.kt ← Holds _layers StateFlow, connects UI to RenderEngine
    CanvasViewport.kt     ← The actual canvas: gesture handling + drawing
    LayerRenderer.kt      ← DrawScope extensions: drawLayer(), drawSelectionBorder()
    GestureUtils.kt       ← Hit testing + drag math (DragMode, applyDrag)
    ImageCache.kt         ← Loads raw bitmaps from content:// URIs (fallback cache)

    components/
        EffectChainSheet.kt   ← Bottom sheet listing all effects on a layer
        EffectItem.kt         ← Single effect row with sliders
        EffectPickerDialog.kt ← Dialog to pick which effect to add
```

---

## 3. Dependency Injection — How It's All Wired

```
EngineModule.kt (@Module, @InstallIn(SingletonComponent))
    │
    ├── provides EffectProcessor          (singleton)
    ├── provides BlendModeProcessor       (singleton)
    ├── provides MaskProcessor            (singleton)
    ├── provides TransformProcessor       (singleton)
    ├── provides DirtyFlagTracker         (singleton)
    ├── provides RenderCache              (singleton)
    │
    └── provides RenderEngine             (singleton)
            takes: Context, EffectProcessor, BlendModeProcessor,
                   MaskProcessor, TransformProcessor,
                   DirtyFlagTracker, RenderCache

CanvasEditorViewModel (@HiltViewModel)
    @Inject constructor(renderEngine: RenderEngine, renderCache: RenderCache)
```

Everything in the engine is a singleton because there's one render pipeline for the app. The ViewModel gets both `RenderEngine` (to trigger renders) and `RenderCache` (to read processed bitmaps for display).

---

## 4. Flow A — Image Loading, Display, Moving, and Resizing

### Step 1: User picks an image

The user taps "Add" in the toolbar, selects an image from the gallery. The flow:

```
AddLayerSheet.kt
    onImageSelected(uri)
        ↓
CanvasEditorScreen.kt
    BitmapFactory.Options (inJustDecodeBounds = true)  ← reads dimensions without loading pixels
    viewModel.addImageLayer(canvasId, uri.toString(), imageWidth, imageHeight)
        ↓
CanvasEditorViewModel.kt :: addImageLayer()
    1. Calculates initialScale to fit image at 90% of canvas
    2. Creates Layer(
           id = UUID,
           type = IMAGE,
           sourceBitmapPath = "content://...",
           transform = LayerTransform(scaleX = initialScale, scaleY = initialScale),
           effectChain = emptyList()
       )
    3. _layers.value = current + newLayer       ← triggers StateFlow emission
    4. _selectedLayerId.value = newLayer.id
```

### Step 2: The StateFlow emission triggers two things simultaneously

```
_layers.collect { layers ->                          ← ViewModel init block
    renderEngine.requestRender(layers, width, height) ← sends to background render loop
}

CanvasEditorScreen collects layers via:
    val layers by viewModel.layers.collectAsState()   ← triggers Compose recomposition
```

### Step 3: Compose draws the image (DISPLAY PATH — instant)

The recomposition flows through:

```
CanvasEditorScreen
    └── CanvasViewport(layers, getProcessedBitmap = viewModel::getProcessedBitmap, ...)
            └── Canvas(modifier) {
                    layers.filter { it.isVisible }.sortedBy { it.zIndex }.forEach { layer ->
                        drawLayer(layer, ..., imageCache, getProcessedBitmap)
                    }
                }
```

Inside `drawLayer()` (LayerRenderer.kt):

```kotlin
val bitmap = getProcessedBitmap?.invoke(layer.id)   // try effect-processed bitmap first
    ?: imageCache.getBitmap(layer)                   // fall back to raw bitmap

val scaleToDisplay = displayWidth / canvasWidth      // e.g., 360dp / 1080px = 0.33

val baseWidth  = bitmap.width  * scaleToDisplay      // raw image size → display pixels
val baseHeight = bitmap.height * scaleToDisplay

val scaledWidth  = baseWidth  * layer.transform.scaleX   // apply user scale
val scaledHeight = baseHeight * layer.transform.scaleY

val centerX = displayWidth / 2f + layer.transform.translateX * scaleToDisplay
val centerY = displayHeight / 2f + layer.transform.translateY * scaleToDisplay

drawImage(
    image = bitmap.asImageBitmap(),
    dstOffset = IntOffset(centerX - scaledWidth/2, centerY - scaledHeight/2),
    dstSize = IntSize(scaledWidth, scaledHeight),
    alpha = layer.opacity
)
```

**Key insight**: The Compose `Canvas` operates in display-pixel coordinates. All layer transforms are stored in canvas-pixel coordinates. The `scaleToDisplay` ratio converts between the two.

### Step 4: User drags the image (MOVING)

Touch events are handled in `CanvasViewport.kt` via `pointerInput` + `detectDragGestures`:

```
onDragStart:
    1. Find the selected layer
    2. Calculate its bounds in display coordinates (calculateLayerBounds)
    3. Hit-test: is the touch on a corner handle or inside the image?
       → determineDragMode() returns MOVE, RESIZE_*, or NONE

onDrag (each frame):
    1. applyDrag() converts display-pixel drag deltas to canvas-pixel deltas:
       dx_canvas = dragAmountX * displayToCanvasScale

    2. For MOVE mode:
       DragResult(dx = dx_canvas, dy = dy_canvas, dScaleX = 0, dScaleY = 0)

    3. Accumulates into activeTransform:
       newTransform = activeTransform.copy(
           translateX = activeTransform.translateX + result.dx,
           translateY = activeTransform.translateY + result.dy
       )

    4. Calls onUpdateTransform(layerId, newTransform)
           ↓
       CanvasEditorViewModel.updateLayerTransform()
           ↓
       _layers.value = _layers.value.map { if (it.id == layerId) it.copy(transform = ...) }
           ↓
       Compose recomposes → Canvas redraws with new position → INSTANT
```

### Step 5: User resizes via corner handles (RESIZING)

Same drag system, but `determineDragMode()` returns `RESIZE_BOTTOM_RIGHT` (etc.) when the touch is near a corner handle (within 24dp radius):

```
For RESIZE_BOTTOM_RIGHT:
    scaleDeltaX = (dragAmountX * displayToCanvasScale) / layerBaseWidth
    scaleDeltaY = (dragAmountY * displayToCanvasScale) / layerBaseHeight

    newTransform = activeTransform.copy(
        scaleX = (activeTransform.scaleX + scaleDeltaX).coerceAtLeast(0.1f),
        scaleY = (activeTransform.scaleY + scaleDeltaY).coerceAtLeast(0.1f)
    )
```

The `scaleX`/`scaleY` values multiply the bitmap's display size in `drawLayer()`, so changing them immediately changes the rendered size.

### Coordinate System Summary

```
Canvas-pixel space (e.g., 1080 x 1920):
    - Where layer.transform values live
    - translateX = 0 means centered
    - scaleX = 1.0 means original bitmap size

Display-pixel space (e.g., 360 x 640):
    - What the Compose Canvas actually draws
    - Converted via: displayValue = canvasValue * (displayWidth / canvasWidth)

displayToCanvasScale = canvasWidth / displayWidth
    - Converts gesture deltas (display px) back to canvas px for storage
```

---

## 5. Flow B — Brightness Effect (Full Lifecycle)

This traces exactly what happens when the user adds a Brightness & Contrast effect and drags the brightness slider.

### Step 1: User taps "Effects" in toolbar → "Add Effect" → "Brightness & Contrast"

```
CanvasEditorScreen.kt
    showEffectsSheet = true
        ↓
    EffectChainSheet(onAddEffect = { effect -> viewModel.addEffect(selectedLayerId, effect) })
        ↓
    EffectPickerDialog.kt
        availableEffects list contains:
            EffectOption(name = "Brightness & Contrast", createEffect = { Effect.BrightnessContrast() })
        User taps it → onEffectSelected(Effect.BrightnessContrast())
            ↓
    EffectChainSheet calls onAddEffect(effect)
```

### Step 2: ViewModel adds the effect to the layer

```kotlin
// CanvasEditorViewModel.kt :: addEffect()
fun addEffect(layerId: String, effect: Effect) {
    _layers.value = _layers.value.map { layer ->
        if (layer.id == layerId) {
            layer.copy(
                effectChain = layer.effectChain + effect,    // append to chain
                updatedAt = System.currentTimeMillis()
            )
        } else layer
    }
    renderEngine.invalidateLayer(layerId)    // mark dirty in DirtyFlagTracker
}
```

`invalidateLayer` clears the hash in `DirtyFlagTracker` so the layer is re-processed, but keeps the old bitmap in `RenderCache` (the user sees the previous state while the new one renders).

### Step 3: `_layers` emission triggers the render pipeline

```
_layers.collect → renderEngine.requestRender(layers, width, height)
    ↓
renderChannel.trySend(RenderRequest(...))    // CONFLATED channel — keeps only latest
    ↓
startRenderLoop() picks it up (running in viewModelScope coroutine)
    ↓
renderFrame(request) on Dispatchers.Default
```

### Step 4: RenderEngine processes the dirty layer

```kotlin
// RenderEngine.kt :: renderFrame()

// Step 4a: Find dirty layers
val visibleLayers = request.layers.filter { it.isVisible }.sortedBy { it.zIndex }
val dirtyLayers = visibleLayers.filter { dirtyFlagTracker.isDirty(it) }

// Step 4b: Process each dirty layer in parallel (coroutine async)
dirtyLayers.map { layer -> async {

    // Load the raw bitmap from content:// URI
    val sourceBitmap = loadSourceBitmap(layer, canvasWidth, canvasHeight)
    //   → ImageDecoder.decodeBitmap(source) for content:// URIs
    //   → Returns e.g. a 4032x3024 JPEG as a Bitmap

    // Apply the effect chain
    val effectApplied = effectProcessor.apply(source = sourceBitmap, chain = layer.effectChain)

    // Cache the result
    dirtyFlagTracker.markClean(layer.id, layer)
    layer.id to effectApplied
}}

// Results go into RenderCache
freshBitmaps.forEach { (id, bitmap) -> renderCache.put(id, bitmap) }
```

### Step 5: EffectProcessor applies the Brightness shader

```kotlin
// EffectProcessor.kt :: apply()
chain.fold(source) { current, effect ->
    if (effect.isEnabled) applyEffect(current, effect) else current
}

// For BrightnessContrast:
applyShader(source, AgslShaders.BRIGHTNESS_CONTRAST) { shader ->
    shader.setFloatUniform("brightness", effect.brightness)   // e.g., 0.3
    shader.setFloatUniform("contrast", effect.contrast)       // e.g., 0.0
}
```

### Step 6: applyShader uses HardwareShaderRenderer for GPU execution

```kotlin
// EffectProcessor.kt :: applyShader()
val runtimeShader = RuntimeShader(shaderSource)
runtimeShader.setInputBuffer("source", BitmapShader(source, CLAMP, CLAMP))
uniforms(runtimeShader)     // sets brightness = 0.3, contrast = 0.0
val paint = Paint().apply { shader = runtimeShader }

return HardwareShaderRenderer.render(w, h) { canvas ->
    canvas.drawRect(0f, 0f, w, h, paint)    // the GPU runs the AGSL shader here
} ?: source                                 // fallback to unprocessed if GPU fails
```

### Step 7: HardwareShaderRenderer bridges to the GPU

```kotlin
// HardwareShaderRenderer.kt :: render()

// 1. Record draw commands into a hardware-accelerated RenderNode
val renderNode = RenderNode("shader_render")
renderNode.setPosition(0, 0, width, height)
val canvas = renderNode.beginRecording()     // ← this canvas IS hardware-accelerated
drawBlock(canvas)                             // draws the rect with RuntimeShader paint
renderNode.endRecording()

// 2. Create a surface to render into
val imageReader = ImageReader.newInstance(width, height, RGBA_8888, 1)
val hardwareRenderer = HardwareRenderer()
hardwareRenderer.setContentRoot(renderNode)
hardwareRenderer.setSurface(imageReader.surface)

// 3. Execute the GPU render and wait for completion
hardwareRenderer.createRenderRequest()
    .setWaitForPresent(true)
    .syncAndDraw()                            // ← GPU executes the AGSL shader

// 4. Read the result back as a Bitmap
val image = imageReader.acquireLatestImage()
val hwBitmap = Bitmap.wrapHardwareBuffer(image.hardwareBuffer, null)
val result = hwBitmap.copy(Bitmap.Config.ARGB_8888, true)  // → mutable software bitmap

// 5. Clean up all GPU resources
```

**Why this complexity?** `RuntimeShader` (AGSL) is a GPU API. You cannot use it with a software `Canvas(Bitmap(...))` — Android throws `IllegalArgumentException: Software rendering doesn't support RuntimeShader`. The only way to execute a RuntimeShader and get pixels back is through this `RenderNode → HardwareRenderer → ImageReader` pipeline.

### Step 8: The actual AGSL shader that runs on the GPU

```glsl
// AgslShaders.kt :: BRIGHTNESS_CONTRAST
uniform shader source;          // the input image as a shader
uniform float brightness;       // -1.0 to 1.0
uniform float contrast;         // -1.0 to 1.0

half4 main(float2 coord) {
    half4 color = source.eval(coord);                    // sample input pixel
    half3 rgb = color.rgb + half3(brightness);           // shift brightness
    float contrastFactor = (1.0 + contrast);
    rgb = (rgb - 0.5) * contrastFactor + 0.5;            // pivot contrast around mid-gray
    rgb = clamp(rgb, 0.0, 1.0);                          // keep in valid range
    return half4(rgb, color.a);                          // preserve alpha
}
```

This runs **per pixel** on the GPU. For a 4032x3024 image, that's ~12 million pixel shader invocations — but they run in parallel on the GPU, so it takes milliseconds.

### Step 9: RenderEngine emits compositedBitmap → triggers redraw

```kotlin
// Back in renderFrame(), after all layers processed:
_compositedBitmap.value = composite     // StateFlow emission
```

```
CanvasEditorScreen.kt:
    val compositedBitmap by viewModel.compositedBitmap.collectAsState()
        ↓ parameter changed
    CanvasViewport recomposes
        ↓
    Canvas draw lambda executes
        ↓
    drawLayer() calls getProcessedBitmap(layer.id)
        ↓
    viewModel.getProcessedBitmap() → renderCache.get(layerId)
        ↓
    Returns the GPU-processed bitmap (with brightness applied!)
        ↓
    drawImage() renders it to screen with current transforms
```

### Step 10: User drags the brightness slider

```
EffectItem.kt :: BrightnessContrastSliders
    LabeledSlider(
        value = brightness,                    // local mutable state for smooth dragging
        onValueChange = { brightness = it },   // updates slider position instantly
        onValueChangeFinished = {              // fires when finger lifts
            onUpdate(effect.copy(brightness = brightness))
        }
    )
```

The `onUpdate` callback flows back up:

```
EffectItem → EffectChainSheet.onUpdateEffect → CanvasEditorScreen → viewModel.updateEffect()
```

```kotlin
// CanvasEditorViewModel.kt :: updateEffect()
fun updateEffect(layerId: String, updatedEffect: Effect) {
    _layers.value = _layers.value.map { layer ->
        if (layer.id == layerId) {
            layer.copy(effectChain = layer.effectChain.map {
                if (it.id == updatedEffect.id) updatedEffect else it   // swap the effect
            })
        } else layer
    }
    renderEngine.invalidateLayer(layerId)   // mark dirty → re-process on next render
}
```

Then the entire render pipeline (Steps 3-9) runs again with the new brightness value.

---

## 6. The DirtyFlagTracker — Why Dragging Doesn't Re-Run Shaders

```kotlin
// DirtyFlagTracker.kt :: computeHash()
private fun computeHash(layer: Layer): Int = Objects.hash(
    layer.sourceBitmapPath,
    layer.effectChain,      // ← included: effect changes trigger re-processing
    layer.opacity,
    layer.blendMode,
    layer.maskPath,
    layer.solidColor,
    layer.textContent,
    layer.type
    // NOTE: transform is NOT hashed
)
```

**`transform` is deliberately excluded from the hash.** This means:

- Dragging/resizing changes `translateX`, `scaleX`, etc. → hash unchanged → layer NOT dirty
- The cached effect-processed bitmap in `RenderCache` is reused as-is
- Only the composite step re-runs (cheap Matrix operations)
- Result: dragging is fast even with heavy effects applied

When the effect chain changes (slider adjustment), the hash changes → layer IS dirty → full GPU re-processing.

---

## 7. Two-Bitmap Strategy — RenderCache vs ImageCache

| | RenderCache | ImageCache |
|---|---|---|
| **Lives in** | `engine/RenderCache.kt` | `ui/.../ImageCache.kt` |
| **Scope** | Singleton (Hilt) | Per-composition (remember) |
| **Contains** | Effect-processed bitmaps (post-shader) | Raw bitmaps (unprocessed) |
| **Keyed by** | `layerId` (String) | `sourceBitmapPath` (String) |
| **Written by** | RenderEngine background thread | ImageCache on first access |
| **Read by** | LayerRenderer via `getProcessedBitmap()` | LayerRenderer as fallback |

In `drawLayer()`:

```kotlin
val bitmap = getProcessedBitmap?.invoke(layer.id)    // RenderCache (has effects)
    ?: imageCache.getBitmap(layer)                    // ImageCache (raw fallback)
```

The fallback ensures the image is visible immediately after adding a layer, before the first render pass completes.

---

## 8. Threading Model

```
Main Thread (UI):
    ├── Compose recomposition
    ├── Canvas.drawLayer() — reads from RenderCache (LruCache is synchronized)
    └── Gesture callbacks (pointerInput)

Dispatchers.Default (Background):
    ├── RenderEngine.startRenderLoop() — processes render channel
    ├── EffectProcessor.apply() — calls HardwareShaderRenderer
    └── HardwareShaderRenderer.render() — GPU render + readback

Dispatchers.IO:
    └── RenderEngine.loadBitmapFromPath() — disk/ContentResolver reads
```

The render channel is `Channel.CONFLATED` — if multiple render requests arrive while one is processing, only the latest is kept. This prevents queue buildup during rapid gesture updates.

---

## 9. Complete Data Flow Diagram

```
 User Gesture / Slider Change
         │
         ▼
 CanvasEditorViewModel
 ├── _layers.value = updated list        ──────── Compose recomposes (instant)
 ├── renderEngine.invalidateLayer()                    │
 │       └── dirtyFlagTracker.invalidate()             │
 │                                                     ▼
 └── _layers.collect triggers:              CanvasViewport Canvas { }
     renderEngine.requestRender()               │
         │                                      ├── drawLayer() per layer
         ▼                                      │   ├── getProcessedBitmap(id)
     renderChannel (CONFLATED)                  │   │       └── renderCache.get()
         │                                      │   │           returns processed Bitmap
         ▼                                      │   └── imageCache fallback if null
     renderFrame() on Dispatchers.Default       │
         │                                      └── drawSelectionBorder()
         ├── isDirty? → yes for effect changes
         │   ├── loadSourceBitmap (IO)
         │   ├── effectProcessor.apply()
         │   │       └── HardwareShaderRenderer.render()
         │   │               └── GPU executes AGSL shader
         │   └── renderCache.put(id, result)
         │
         ├── isDirty? → no for transform-only changes
         │   └── renderCache.get() reuses existing bitmap (FAST)
         │
         └── _compositedBitmap.value = composite
                 │
                 └── Compose sees StateFlow change → recomposition
                         → Canvas redraws with updated RenderCache → effects visible
```
