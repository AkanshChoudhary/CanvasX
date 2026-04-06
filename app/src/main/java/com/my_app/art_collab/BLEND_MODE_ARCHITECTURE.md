# Blend Mode Architecture — Complete Technical Reference

How blend modes are defined, stored, applied on screen, and rendered for export in CanvasX.

---

## 1. Design Overview

Blend modes operate at **two independent levels** in the app, each optimized for a different purpose:

```
┌──────────────────────────────────────────────────────────────────────┐
│                    DISPLAY PATH (real-time)                          │
│                                                                      │
│  Compose DrawScope.drawImage() / drawRect()                          │
│  Applies blend modes via native BlendMode parameter                  │
│  GPU-accelerated by Skia under the hood                              │
│  Zero overhead — blend mode is just a flag on draw calls             │
│  Works at 60fps during gestures, slider drags, everything            │
│                                                                      │
│  Files: LayerRenderer.kt, CanvasViewport.kt                         │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                    EXPORT PATH (pixel-perfect)                       │
│                                                                      │
│  AGSL RuntimeShader with per-pixel blend math                        │
│  Runs on GPU via HardwareRenderer + RenderNode                       │
│  Produces a flat composited Bitmap for export/share                  │
│  Processes asynchronously on Dispatchers.Default                     │
│                                                                      │
│  Files: BlendModeProcessor.kt, AgslShaders.kt,                      │
│         HardwareShaderRenderer.kt, RenderEngine.kt                   │
└──────────────────────────────────────────────────────────────────────┘
```

**Why two paths?** Compose's built-in `BlendMode` on draw calls is the fastest possible
way to show blending on screen — it runs inside Skia's existing draw pipeline with no
extra allocations, no shader compilation, no bitmap copies. But when we export the final
image, we need a flat `Bitmap` with all layers composited. That requires explicit
pixel-level blending through AGSL shaders running on a `HardwareRenderer`.

---

## 2. Supported Blend Modes

Defined as a serializable enum at the domain layer:

```
File: domain/model/BlendMode.kt
```

```kotlin
enum class BlendMode {
    NORMAL,       // Standard alpha-over compositing (SrcOver)
    MULTIPLY,     // Darkens — each channel = base × blend
    SCREEN,       // Lightens — inverse of Multiply
    OVERLAY,      // Contrast boost — Multiply for darks, Screen for lights
    SOFT_LIGHT    // Subtle contrast & saturation shift
}
```

Each `Layer` carries its blend mode and opacity:

```
File: domain/model/Layer.kt
```

```kotlin
data class Layer(
    ...
    val blendMode: BlendMode = BlendMode.NORMAL,
    val opacity: Float = 1f,
    ...
)
```

Default is `NORMAL` at full opacity. These values are persisted with the layer via
kotlinx.serialization.

---

## 3. Display Path — How Blend Modes Appear On Screen

This is the **performance-critical path**. Every frame, during gestures, slider drags,
and all user interaction, layers are drawn individually from bottom to top with their
blend mode applied natively on each draw call.

### 3.1 The Mapping Function

```
File: ui/screens/canvas_editor/LayerRenderer.kt
```

The domain `BlendMode` enum is mapped to Compose's `androidx.compose.ui.graphics.BlendMode`:

```kotlin
private fun mapBlendMode(mode: BlendMode): ComposeBlendMode = when (mode) {
    BlendMode.NORMAL    -> ComposeBlendMode.SrcOver
    BlendMode.MULTIPLY  -> ComposeBlendMode.Multiply
    BlendMode.SCREEN    -> ComposeBlendMode.Screen
    BlendMode.OVERLAY   -> ComposeBlendMode.Overlay
    BlendMode.SOFT_LIGHT -> ComposeBlendMode.Softlight
}
```

`ComposeBlendMode` is an import alias for `androidx.compose.ui.graphics.BlendMode` to
avoid naming collisions with the domain enum.

### 3.2 Applying Blend Modes in drawLayer()

```
File: ui/screens/canvas_editor/LayerRenderer.kt
```

The `DrawScope.drawLayer()` extension function draws each layer. Blend mode and opacity
are passed as parameters directly to Compose's draw calls:

**For image layers (IMAGE, AI_GENERATED):**

```kotlin
drawImage(
    image = imageBitmap,
    srcOffset = IntOffset.Zero,
    srcSize = IntSize(bitmap.width, bitmap.height),
    dstOffset = IntOffset(destLeft, destTop),
    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
    alpha = layer.opacity,
    blendMode = mapBlendMode(layer.blendMode)   // <-- GPU blend mode
)
```

**For solid color layers:**

```kotlin
drawRect(
    color = Color(color),
    topLeft = Offset(centerX - layerW / 2, centerY - layerH / 2),
    size = Size(layerW, layerH),
    alpha = layer.opacity,
    blendMode = mapBlendMode(layer.blendMode)   // <-- GPU blend mode
)
```

Key detail: opacity is passed via the `alpha` parameter, **not** baked into the color.
This ensures the GPU applies opacity and blend mode together in a single compositing
step.

### 3.3 Why This Is Essentially Free

Compose's `drawImage` and `drawRect` delegate to Skia's `SkCanvas::drawImageRect` and
`SkCanvas::drawRect` internally. The `blendMode` parameter maps directly to Skia's
`SkBlendMode` enum, which the GPU implements as a hardware blend state — the same
mechanism used by OpenGL/Vulkan blend equations. There are:

- **No extra bitmap allocations** — blending happens in the framebuffer
- **No shader compilation** — these are built-in GPU blend states
- **No extra render passes** — the blend mode is applied during the existing draw call
- **No CPU pixel work** — the GPU handles everything

This is why blend modes work at full framerate during drag gestures without any
performance impact.

### 3.4 Drawing Order in CanvasViewport

```
File: ui/screens/canvas_editor/CanvasViewport.kt
```

The `Canvas` composable draws layers bottom-to-top by `zIndex`. Each subsequent layer
blends with the accumulated result of all layers below it:

```kotlin
Canvas(...) {
    drawCheckerboard()           // Transparency grid background

    @Suppress("UNUSED_EXPRESSION")
    compositedBitmap             // Read state to trigger recomposition
                                 // when RenderEngine finishes processing

    layers
        .filter { it.isVisible }
        .sortedBy { it.zIndex }  // Bottom-to-top order
        .forEach { layer ->
            drawLayer(           // Each layer blends with what's below
                layer = layer,
                ...
                getProcessedBitmap = getProcessedBitmap
            )
        }

    // Selection handles drawn on top (always SrcOver)
    drawSelectionBorder(...)
}
```

The `compositedBitmap` reference (suppressed as unused) exists solely as a Compose state
read — it forces the Canvas to recompose when the `RenderEngine` finishes processing
effects in the background, so `getProcessedBitmap` returns the latest effect-processed
bitmaps from the `RenderCache`.

### 3.5 Bitmap Source Priority

When drawing a layer, `drawLayer()` prefers the effect-processed bitmap from the
render cache over the raw source:

```kotlin
val bitmap = getProcessedBitmap?.invoke(layer.id)  // Effect-processed (from RenderCache)
    ?: imageCache.getBitmap(layer)                  // Raw source (fallback)
```

This means the display path shows: **effects (brightness, blur, etc.) + blend mode +
opacity** — all in real-time.

---

## 4. Export Path — Pixel-Perfect Compositing via AGSL

When the app needs a flat `Bitmap` (for export, sharing, or thumbnails), the
`RenderEngine` runs the full compositing pipeline including AGSL blend shaders.

### 4.1 AGSL Shader Definitions

```
File: engine/AgslShaders.kt
```

Each blend mode has a corresponding AGSL (Android Graphics Shading Language) shader.
These are inline GLSL-like strings compiled at runtime by `RuntimeShader`:

**MULTIPLY:**
```glsl
uniform shader base;
uniform shader blend;
uniform float opacity;
half4 main(float2 coord) {
    half4 baseColor = base.eval(coord);
    half4 blendColor = blend.eval(coord);
    half4 result = baseColor * blendColor;
    return mix(baseColor, result, blendColor.a * opacity);
}
```

**SCREEN:**
```glsl
half4 result = half4(1.0) - (half4(1.0) - b) * (half4(1.0) - s);
result.a = b.a;
return mix(b, result, s.a * opacity);
```

**OVERLAY:**
```glsl
// Per-channel: Multiply for darks, Screen for lights
result.r = b.r < 0.5 ? 2.0*b.r*s.r : 1.0 - 2.0*(1.0-b.r)*(1.0-s.r);
// (same for .g, .b)
return mix(b, half4(result, b.a), s.a * opacity);
```

**SOFT_LIGHT:**
```glsl
// Per-channel: Pegtop soft light formula
result.r = s.r < 0.5
    ? b.r - (1.0-2.0*s.r)*b.r*(1.0-b.r)
    : b.r + (2.0*s.r-1.0)*(sqrt(b.r)-b.r);
// (same for .g, .b)
return mix(b, half4(result, b.a), s.a * opacity);
```

All shaders share the same pattern:
1. Sample `base` (the composite-so-far) and `blend` (the current layer) at the coordinate
2. Compute the blended result per the mode's math
3. `mix()` between the original base and the blended result, controlled by
   `blendColor.a * opacity` — this gives correct alpha-aware blending with opacity

### 4.2 BlendModeProcessor — Shader Execution

```
File: engine/BlendModeProcessor.kt
```

This singleton takes a base bitmap (composite-so-far) and a blend bitmap (current layer)
and produces a new composited bitmap:

```kotlin
fun composite(base: Bitmap, blend: Bitmap, blendMode: BlendMode, opacity: Float): Bitmap
```

**Flow for NORMAL mode:**
```
base + blend → Canvas.drawBitmap(base) → Canvas.drawBitmap(blend, alpha=opacity)
             → Pure CPU alpha compositing, no shader needed
```

**Flow for MULTIPLY / SCREEN / OVERLAY / SOFT_LIGHT:**
```
1. Select AGSL source string from AgslShaders
2. Create RuntimeShader(shaderSource)
3. Attach base bitmap as BitmapShader → runtimeShader.setInputBuffer("base", ...)
4. Attach blend bitmap as BitmapShader → runtimeShader.setInputBuffer("blend", ...)
5. Set opacity uniform → runtimeShader.setFloatUniform("opacity", opacity)
6. Create Paint with the RuntimeShader
7. Render via HardwareShaderRenderer → returns Bitmap
8. On failure → falls back to compositeNormal (graceful degradation)
```

### 4.3 HardwareShaderRenderer — GPU Bitmap Rasterization

```
File: engine/HardwareShaderRenderer.kt
```

AGSL `RuntimeShader` requires hardware rendering — it cannot run on a software
`Canvas`. This utility creates a temporary GPU rendering pipeline:

```
RenderNode (records draw commands)
    ↓
HardwareRenderer (submits to GPU)
    ↓
ImageReader.surface (captures GPU output)
    ↓
ImageReader.acquireLatestImage() → HardwareBuffer → Bitmap
    ↓
Bitmap.copy(ARGB_8888) → mutable software bitmap
```

All resources (ImageReader, HardwareRenderer, RenderNode) are created and destroyed
per call. The result is a standard mutable `ARGB_8888` bitmap that can be further
composed or exported.

### 4.4 RenderEngine — Full Pipeline Orchestration

```
File: engine/RenderEngine.kt
```

The render engine runs in a coroutine loop consuming from a `CONFLATED` channel
(only the latest request is kept, older ones are dropped):

```
ViewModel._layers changes
    ↓
_layers.collect { } → renderEngine.requestRender(layers, w, h)
    ↓
CONFLATED Channel (drops stale requests)
    ↓
renderFrame(request):
    │
    ├── Step 1: Process dirty layers IN PARALLEL
    │   For each dirty layer (via DirtyFlagTracker):
    │     async {
    │       loadSourceBitmap()     → raw pixels
    │       effectProcessor.apply() → brightness, blur, etc. (AGSL)
    │       maskProcessor.apply()   → alpha masking (AGSL)
    │     }
    │   Results → RenderCache (per-layer, keyed by layerId)
    │
    └── Step 2: Composite all layers SEQUENTIALLY (bottom-to-top)
        var composite = empty transparent Bitmap
        for each visible layer (sorted by zIndex):
          transformed = transformProcessor.apply(cached, transform)
          composite = blendModeProcessor.composite(composite, transformed, blendMode, opacity)
        emit composite → _compositedBitmap StateFlow
```

### 4.5 Dirty Flag Tracking

```
File: engine/DirtyFlagTracker.kt
```

The `DirtyFlagTracker` hashes each layer's render-relevant properties to determine
if it needs reprocessing:

```kotlin
private fun computeHash(layer: Layer): Int = Objects.hash(
    layer.sourceBitmapPath,
    layer.effectChain,
    layer.opacity,
    layer.blendMode,
    layer.maskPath,
    layer.solidColor,
    layer.textContent,
    layer.type
)
```

Note: `transform` is NOT in this hash. Transform-only changes (drag, scale, rotate)
skip per-layer reprocessing entirely — they only affect the compositing step.
`blendMode` and `opacity` ARE tracked, so changing them triggers per-layer reprocessing.

---

## 5. User Interaction Flow

### 5.1 User Changes Blend Mode via UI

```
File: ui/screens/canvas_editor/components/BlendModePickerSheet.kt
File: ui/screens/canvas_editor/CanvasEditorViewModel.kt
```

```
User taps a blend mode in BlendModePickerSheet
    ↓
onBlendModeSelected(BlendMode.MULTIPLY)
    ↓
CanvasEditorViewModel.setBlendMode(layerId, BlendMode.MULTIPLY)
    ↓
_layers.value = layers.map { ... copy(blendMode = MULTIPLY) ... }
    ↓
Two things happen simultaneously:
    │
    ├── INSTANT: Compose recomposes Canvas
    │   drawLayer() reads layer.blendMode → mapBlendMode() → ComposeBlendMode.Multiply
    │   drawImage(..., blendMode = Multiply) → GPU applies immediately
    │   User sees the result THIS FRAME
    │
    └── ASYNC: _layers collector fires → requestRender()
        RenderEngine picks up the request
        DirtyFlagTracker sees blendMode changed → marks dirty
        Re-runs effect chain + AGSL blend compositing
        Emits new compositedBitmap (for future export)
        Triggers recomposition → getProcessedBitmap returns updated cache
```

### 5.2 User Drags Opacity Slider

```
User drags opacity slider continuously
    ↓
onOpacityChanged(0.7f) fires per frame
    ↓
CanvasEditorViewModel.setLayerOpacity(layerId, 0.7f)
    ↓
_layers.value updates → Compose recomposes
    ↓
drawImage(..., alpha = 0.7f, blendMode = currentMode) → instant
    ↓
RenderEngine also processes in background (CONFLATED — drops intermediate values)
```

### 5.3 User Drags a Layer

```
User drags an image layer across the canvas
    ↓
onUpdateTransform() fires every gesture frame
    ↓
_layers.value updates with new translateX/translateY
    ↓
Canvas recomposes → drawLayer() draws at new position
    with blend mode STILL APPLIED (same blendMode parameter)
    ↓
RenderEngine also receives requests but:
    - DirtyFlagTracker does NOT hash transforms
    - Step 1 (per-layer processing) is skipped (nothing dirty)
    - Step 2 (compositing) runs but result isn't used for display
```

---

## 6. File Map

```
domain/model/
├── BlendMode.kt              Enum: NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT
└── Layer.kt                  Data class with blendMode + opacity fields

engine/
├── AgslShaders.kt             AGSL shader source strings for each blend mode
├── BlendModeProcessor.kt      Composites two bitmaps using AGSL or alpha-over
├── HardwareShaderRenderer.kt  Runs AGSL on GPU → produces CPU Bitmap
├── RenderEngine.kt            Orchestrates the full async pipeline
├── RenderCache.kt             Stores per-layer processed bitmaps
└── DirtyFlagTracker.kt        Hash-based change detection for layers

ui/screens/canvas_editor/
├── LayerRenderer.kt           drawLayer() with Compose BlendMode on draw calls
├── CanvasViewport.kt          Canvas composable, per-layer drawing loop
├── CanvasEditorViewModel.kt   setBlendMode(), setLayerOpacity(), state management
└── components/
    └── BlendModePickerSheet.kt  Bottom sheet UI: blend mode radios + opacity slider
```

---

## 7. Performance Characteristics

| Operation | Display Path | Export Path |
|-----------|-------------|-------------|
| Blend mode application | GPU blend state flag | AGSL RuntimeShader |
| Extra allocations | None | 1 Bitmap per blend operation |
| Latency | < 1ms (same frame) | 10-100ms (async) |
| Works during gestures | Yes, full framerate | Background only |
| Pixel accuracy | Skia built-in (standard) | Custom AGSL (W3C formulas) |

### Why the display path is fast:

1. **No extra bitmap allocations** — blending happens in the existing framebuffer
2. **No shader compilation** — Compose delegates to Skia's built-in blend modes
   which map to hardware blend states (OpenGL `glBlendFunc` / Vulkan blend equations)
3. **No extra render passes** — the blend mode is a parameter on the draw call
   that was already happening
4. **Constant time** — performance is identical whether blend mode is NORMAL or MULTIPLY

### Why the export path exists alongside it:

1. Compose's `DrawScope` cannot produce a flat `Bitmap` — it draws to screen only
2. Export/share requires a single composited bitmap file
3. AGSL gives pixel-level control over blend math for format-specific accuracy
4. The `RenderNode` → `HardwareRenderer` → `ImageReader` pipeline is the only way
   to run AGSL shaders and capture the result as a `Bitmap`

---

## 8. Adding a New Blend Mode

To add a new blend mode (e.g., `COLOR_DODGE`):

### Step 1 — Domain enum

```
File: domain/model/BlendMode.kt
```
Add `COLOR_DODGE` to the enum.

### Step 2 — Display path mapping

```
File: ui/screens/canvas_editor/LayerRenderer.kt
```
Add to `mapBlendMode()`:
```kotlin
BlendMode.COLOR_DODGE -> ComposeBlendMode.ColorDodge
```

### Step 3 — Export path shader

```
File: engine/AgslShaders.kt
```
Add the AGSL shader string with the W3C compositing formula:
```kotlin
val COLOR_DODGE_BLEND = """
    uniform shader base;
    uniform shader blend;
    uniform float opacity;
    half4 main(float2 coord) {
        half4 b = base.eval(coord);
        half4 s = blend.eval(coord);
        half3 result;
        result.r = s.r >= 1.0 ? 1.0 : min(1.0, b.r / (1.0 - s.r));
        result.g = s.g >= 1.0 ? 1.0 : min(1.0, b.g / (1.0 - s.g));
        result.b = s.b >= 1.0 ? 1.0 : min(1.0, b.b / (1.0 - s.b));
        return mix(b, half4(result, b.a), s.a * opacity);
    }
""".trimIndent()
```

### Step 4 — Wire into BlendModeProcessor

```
File: engine/BlendModeProcessor.kt
```
Add to the `when` block:
```kotlin
BlendMode.COLOR_DODGE -> AgslShaders.COLOR_DODGE_BLEND
```

### Step 5 — UI labels

```
File: ui/screens/canvas_editor/components/BlendModePickerSheet.kt
```
Add display name and description in the extension functions.

That's it. Five files, one line each (plus the shader). The architecture
ensures every new blend mode automatically works at full framerate on screen
and produces correct pixel output for export.
