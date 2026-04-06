# CanvasX — Master Development Plan
> Version 2.0 | Solo Developer | 8-Week MVP Build | Phone Portrait Only
> Cuts applied: Cloudinary removed, Onboarding removed, Blend modes reduced to 5

---

## Table of Contents

1. App Summary
2. Tech Stack — Every Decision Explained
3. Project Structure
4. Data Models
5. Architecture — MVI Pattern
6. Rendering Engine Design
7. Every Screen — Full Specification
8. Navigation Graph
9. API Integrations
10. Multithreading Model
11. Real-Time Collaboration Design
12. Adaptive UI System
13. Background Work & Notifications
14. 8-Week Sprint Plan
15. MVP Scope vs Deferred Features

---

## 1. App Summary

CanvasX is a real-time collaborative photo compositing app for Android. Users import photos, generate AI imagery, and stack them as independent layers — each with its own blend mode, opacity, effect chain, and mask. Every edit is non-destructive and synced live across collaborators, so multiple people can work on the same canvas simultaneously and see each other's changes instantly. The final composite is exported at full resolution to the device gallery. It sits in the space between a lightweight Photoshop and a collaborative Figma, built entirely for mobile.

**Target Android version:** API 33+ (Android 13) minimum. Required for AGSL (Android Graphics Shading Language) shaders.
**Target form factor:** Phone portrait only. No tablet, no foldable, no landscape layouts.
**Language:** Kotlin only. Zero Java.
**UI:** 100% Jetpack Compose. Zero XML layouts.
**Architecture:** MVI (Model-View-Intent) with strict unidirectional data flow.

---

## 2. Tech Stack — Every Decision Explained

### Full Dependency List

```kotlin
// build.gradle.kts (app level)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)   // Type-safe nav routes
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)        // Firebase
}

android {
    compileSdk = 35
    minSdk = 33                                // Hard requirement for AGSL RuntimeShader
    targetSdk = 35

    buildFeatures {
        compose = true
        buildConfig = true                     // For API keys via BuildConfig
    }
}

dependencies {

    // ── Compose BOM ─────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")        // Shared element transitions, AnimatedVisibility
    implementation("androidx.compose.foundation:foundation")      // pointerInput, Canvas, LazyColumn drag

    // ── Navigation ──────────────────────────────────────────────────────────
    // Navigation 2.8+ required for type-safe routes via Kotlin Serialization
    implementation("androidx.navigation:navigation-compose:2.8.x")

    // ── Lifecycle & ViewModel ────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x")
    // collectAsStateWithLifecycle — Google-recommended over collectAsState()
    // Automatically pauses collection when app goes to background
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.x")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.x")
    // Required to bridge Firebase Task API with coroutines (.await() extension)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.x")

    // ── Room (Local Database) ─────────────────────────────────────────────────
    // Source of truth for offline support. Every canvas + layer lives here first.
    implementation("androidx.room:room-runtime:2.6.x")
    implementation("androidx.room:room-ktx:2.6.x")                // Flow + suspend support
    ksp("androidx.room:room-compiler:2.6.x")

    // ── DataStore ────────────────────────────────────────────────────────────
    // Replaces SharedPreferences. Stores user preferences (theme, default canvas size)
    implementation("androidx.datastore:datastore-preferences:1.1.x")

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.x.x"))
    implementation("com.google.firebase:firebase-auth-ktx")        // Google Sign-In
    implementation("com.google.firebase:firebase-firestore-ktx")   // Canvas + layer sync
    implementation("com.google.firebase:firebase-messaging-ktx")   // Push notifications

    // ── WorkManager ──────────────────────────────────────────────────────────
    // The ONLY API that guarantees background execution after app kill.
    // Used for export pipeline. Hilt integration required.
    implementation("androidx.work:work-runtime-ktx:2.9.x")

    // ── Hilt (Dependency Injection) ───────────────────────────────────────────
    // Industry standard for Android DI. Required to inject into WorkManager workers.
    implementation("com.google.dagger:hilt-android:2.51.x")
    ksp("com.google.dagger:hilt-compiler:2.51.x")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.x")  // hiltViewModel() in Compose
    implementation("androidx.hilt:hilt-work:1.2.x")                // @HiltWorker support

    // ── CameraX ──────────────────────────────────────────────────────────────
    // Used to capture photos directly into new image layers
    implementation("androidx.camera:camera-camera2:1.3.x")
    implementation("androidx.camera:camera-lifecycle:1.3.x")
    implementation("androidx.camera:camera-view:1.3.x")            // PreviewView for Compose

    // ── Coil ─────────────────────────────────────────────────────────────────
    // Image loading library. Compose-native, coroutine-native.
    // Used for loading thumbnails in HomeScreen canvas cards.
    implementation("io.coil-kt:coil-compose:2.6.x")

    // ── Networking ───────────────────────────────────────────────────────────
    // Retrofit for DALL-E and Remove.bg REST APIs
    implementation("com.squareup.retrofit2:retrofit:2.11.x")
    implementation("com.squareup.retrofit2:converter-gson:2.11.x")
    implementation("com.squareup.okhttp3:okhttp:4.12.x")
    // Logs all HTTP requests/responses in debug builds. Essential for API debugging.
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.x")

    // ── Google Sign-In ────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-auth:21.x.x")

    // ── Kotlin Serialization ──────────────────────────────────────────────────
    // Powers type-safe navigation routes. Also used to serialize
    // Effect and LayerTransform objects to/from JSON for Firestore and Room.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.x")

    // ── Accompanist Permissions ───────────────────────────────────────────────
    // Simplifies runtime permission requests in Compose.
    // Used on first editor launch to request camera + storage.
    implementation("com.google.accompanist:accompanist-permissions:0.34.x")

    // ── Splash Screen ─────────────────────────────────────────────────────────
    // Official Android Splash Screen API. Handles the animated icon on cold start.
    implementation("androidx.core:core-splashscreen:1.0.x")
}
```

### Architecture Decision Log

Every key decision is recorded here so it can be defended in an interview.

| Decision | Choice | Rejected Alternative | Reason |
|---|---|---|---|
| Rendering API | AGSL (RuntimeShader) | RenderScript | RenderScript is fully deprecated since API 31. AGSL is the modern GPU shader API for Android 13+. Direct Google recommendation. |
| UI Toolkit | Jetpack Compose | XML + View system | Compose is the current standard. Required for M3 Expressive animations, type-safe nav, and modern state management. |
| Architecture | MVI | MVVM | MVI's unidirectional data flow is essential for collaborative state correctness — two sources of change (local user + remote Firestore) must both flow through one pipeline. |
| DI Framework | Hilt | Koin, manual DI | Hilt is required for `@HiltWorker` injection into WorkManager. It is the Google-recommended standard. |
| Local Database | Room | SQLite directly | Room provides type-safe DAOs, coroutine support, and Flow emissions — all necessary for reactive offline architecture. |
| Nav Route System | Navigation 2.8 type-safe | String routes | String routes are deprecated. Type-safe routes via `@Serializable` data classes are the current Google guideline. |
| Collaboration | Firebase Firestore | Supabase, custom WebSocket | Firestore's `callbackFlow` snapshot listeners give real-time sync with automatic offline persistence out of the box. |
| Background Work | WorkManager | Foreground Service, AlarmManager | WorkManager is the only API that guarantees execution under all battery optimization regimes on all Android OEMs. |
| Image Loading | Coil | Glide, Picasso | Coil is written in Kotlin, uses coroutines natively, and has first-class Compose support. Smaller binary than Glide. |
| Shader Blending | AGSL per-pixel shaders | ColorMatrix, PorterDuff | ColorMatrix cannot implement Multiply/Screen/Overlay correctly. PorterDuff only handles alpha compositing, not photographic blend modes. AGSL is the only correct solution. |

---

## 3. Project Structure

The full file tree. Every file listed here must be created.

```
app/
└── src/
    ├── main/
    │   ├── java/com/yourname/canvasx/
    │   │   │
    │   │   ├── CanvasXApplication.kt          // @HiltAndroidApp entry point
    │   │   ├── MainActivity.kt                // Single activity. Edge-to-edge setup.
    │   │   │                                  // Hosts NavHost. Passes WindowSizeClass.
    │   │   │
    │   │   ├── core/
    │   │   │   ├── di/
    │   │   │   │   ├── NetworkModule.kt       // Provides OkHttpClient, Retrofit instances
    │   │   │   │   ├── DatabaseModule.kt      // Provides Room DB singleton
    │   │   │   │   ├── FirebaseModule.kt      // Provides FirebaseAuth, Firestore instances
    │   │   │   │   ├── RepositoryModule.kt    // Binds interfaces to implementations
    │   │   │   │   └── WorkerModule.kt        // Hilt WorkerFactory binding
    │   │   │   │
    │   │   │   ├── navigation/
    │   │   │   │   ├── AppNavGraph.kt         // Root NavHost with all destinations
    │   │   │   │   ├── Routes.kt              // All @Serializable route data classes
    │   │   │   │   └── NavExtensions.kt       // Helper: navigateAndClearStack()
    │   │   │   │
    │   │   │   ├── theme/
    │   │   │   │   ├── Theme.kt               // MaterialTheme setup, dynamic color
    │   │   │   │   ├── Typography.kt          // M3 type scale
    │   │   │   │   ├── Color.kt               // Brand color tokens
    │   │   │   │   └── Shape.kt               // M3 shape scheme
    │   │   │   │
    │   │   │   └── utils/
    │   │   │       ├── BitmapUtils.kt         // loadBitmapFromPath(), scaleBitmap()
    │   │   │       ├── ColorUtils.kt          // ARGB int helpers
    │   │   │       ├── FileUtils.kt           // saveToGallery(), getTempFile()
    │   │   │       └── JsonUtils.kt           // Effect/Transform serialization helpers
    │   │   │
    │   │   ├── data/
    │   │   │   ├── local/
    │   │   │   │   ├── db/
    │   │   │   │   │   ├── CanvasXDatabase.kt // @Database declaration, migrations
    │   │   │   │   │   ├── dao/
    │   │   │   │   │   │   ├── CanvasDao.kt
    │   │   │   │   │   │   ├── LayerDao.kt
    │   │   │   │   │   │   └── HistoryDao.kt
    │   │   │   │   │   └── entity/
    │   │   │   │   │       ├── CanvasEntity.kt
    │   │   │   │   │       ├── LayerEntity.kt
    │   │   │   │   │       └── HistoryEntity.kt
    │   │   │   │   └── datastore/
    │   │   │   │       └── UserPreferencesDataStore.kt
    │   │   │   │
    │   │   │   ├── remote/
    │   │   │   │   ├── firebase/
    │   │   │   │   │   ├── FirestoreCanvasDataSource.kt
    │   │   │   │   │   └── FirestoreLayerDataSource.kt
    │   │   │   │   └── api/
    │   │   │   │       ├── DalleApiService.kt
    │   │   │   │       ├── RemoveBgApiService.kt
    │   │   │   │       └── dto/
    │   │   │   │           ├── DalleRequest.kt
    │   │   │   │           ├── DalleResponse.kt
    │   │   │   │           └── RemoveBgResponse.kt
    │   │   │   │
    │   │   │   └── repository/
    │   │   │       ├── CanvasRepository.kt
    │   │   │       ├── LayerRepository.kt
    │   │   │       ├── AuthRepository.kt
    │   │   │       └── ExportRepository.kt
    │   │   │
    │   │   ├── domain/
    │   │   │   ├── model/
    │   │   │   │   ├── Canvas.kt
    │   │   │   │   ├── Layer.kt
    │   │   │   │   ├── Effect.kt              // Sealed class hierarchy — 11 types
    │   │   │   │   ├── BlendMode.kt           // Enum — 5 values
    │   │   │   │   ├── LayerTransform.kt
    │   │   │   │   ├── Mask.kt
    │   │   │   │   ├── TextLayerContent.kt
    │   │   │   │   ├── Collaborator.kt
    │   │   │   │   └── HistoryEntry.kt
    │   │   │   │
    │   │   │   └── usecase/
    │   │   │       ├── canvas/
    │   │   │       │   ├── CreateCanvasUseCase.kt
    │   │   │       │   ├── GetCanvasListUseCase.kt
    │   │   │       │   ├── GetCanvasUseCase.kt
    │   │   │       │   ├── UpdateCanvasUseCase.kt
    │   │   │       │   └── DeleteCanvasUseCase.kt
    │   │   │       ├── layer/
    │   │   │       │   ├── AddImageLayerUseCase.kt
    │   │   │       │   ├── AddSolidColorLayerUseCase.kt
    │   │   │       │   ├── UpdateLayerTransformUseCase.kt
    │   │   │       │   ├── ReorderLayersUseCase.kt
    │   │   │       │   ├── AddEffectToLayerUseCase.kt
    │   │   │       │   ├── UpdateEffectUseCase.kt
    │   │   │       │   ├── RemoveEffectUseCase.kt
    │   │   │       │   ├── SetBlendModeUseCase.kt
    │   │   │       │   ├── SetLayerOpacityUseCase.kt
    │   │   │       │   ├── DeleteLayerUseCase.kt
    │   │   │       │   └── MergeLayersUseCase.kt
    │   │   │       ├── ai/
    │   │   │       │   ├── GenerateAiLayerUseCase.kt
    │   │   │       │   └── RemoveBackgroundUseCase.kt
    │   │   │       ├── history/
    │   │   │       │   ├── PushHistoryUseCase.kt
    │   │   │       │   ├── UndoUseCase.kt
    │   │   │       │   └── RedoUseCase.kt
    │   │   │       └── export/
    │   │   │           └── ExportCanvasUseCase.kt
    │   │   │
    │   │   ├── engine/
    │   │   │   ├── RenderEngine.kt            // Core compositing loop
    │   │   │   ├── AgslShaders.kt             // All AGSL shader source strings as constants
    │   │   │   ├── EffectProcessor.kt         // Applies effect chain per layer
    │   │   │   ├── BlendModeProcessor.kt      // Composites two bitmaps with blend mode
    │   │   │   ├── MaskProcessor.kt           // Applies mask alpha to layer bitmap
    │   │   │   ├── TransformProcessor.kt      // Applies LayerTransform to bitmap
    │   │   │   ├── DirtyFlagTracker.kt        // Tracks which layers need re-render
    │   │   │   └── RenderCache.kt             // In-memory cache of processed layer bitmaps
    │   │   │
    │   │   └── ui/
    │   │       ├── screens/
    │   │       │   ├── splash/
    │   │       │   │   └── SplashScreen.kt
    │   │       │   ├── auth/
    │   │       │   │   ├── AuthScreen.kt
    │   │       │   │   └── AuthViewModel.kt
    │   │       │   ├── home/
    │   │       │   │   ├── HomeScreen.kt
    │   │       │   │   ├── HomeViewModel.kt
    │   │       │   │   ├── HomeUiState.kt
    │   │       │   │   ├── HomeIntent.kt
    │   │       │   │   └── components/
    │   │       │   │       ├── CanvasCard.kt
    │   │       │   │       └── NewCanvasBottomSheet.kt
    │   │       │   ├── editor/
    │   │       │   │   ├── EditorScreen.kt
    │   │       │   │   ├── EditorViewModel.kt
    │   │       │   │   ├── EditorUiState.kt
    │   │       │   │   ├── EditorIntent.kt
    │   │       │   │   └── components/
    │   │       │   │       ├── CanvasRenderer.kt
    │   │       │   │       ├── TopEditorBar.kt
    │   │       │   │       ├── ToolBar.kt
    │   │       │   │       ├── TransformOverlay.kt
    │   │       │   │       ├── LayerPanel.kt
    │   │       │   │       ├── LayerItem.kt
    │   │       │   │       ├── AddLayerSheet.kt
    │   │       │   │       ├── EffectChainSheet.kt
    │   │       │   │       ├── EffectItem.kt
    │   │       │   │       ├── BlendModePickerSheet.kt
    │   │       │   │       ├── CollaboratorsSheet.kt
    │   │       │   │       └── AiGenerationDialog.kt
    │   │       │   ├── mask/
    │   │       │   │   ├── MaskEditorScreen.kt
    │   │       │   │   └── MaskEditorViewModel.kt
    │   │       │   ├── export/
    │   │       │   │   ├── ExportScreen.kt
    │   │       │   │   └── ExportViewModel.kt
    │   │       │   └── settings/
    │   │       │       ├── SettingsScreen.kt
    │   │       │       └── SettingsViewModel.kt
    │   │       │
    │   │       └── worker/
    │   │           └── ExportWorker.kt
    │   │
    │   └── res/
    │       ├── raw/                           // AGSL shader source files
    │       │   ├── shader_multiply.agsl
    │       │   ├── shader_screen.agsl
    │       │   ├── shader_overlay.agsl
    │       │   ├── shader_soft_light.agsl
    │       │   ├── shader_brightness_contrast.agsl
    │       │   ├── shader_gaussian_blur.agsl
    │       │   ├── shader_vignette.agsl
    │       │   ├── shader_color_temperature.agsl
    │       │   ├── shader_hsl.agsl
    │       │   ├── shader_exposure.agsl
    │       │   ├── shader_sharpen.agsl
    │       │   ├── shader_vignette.agsl
    │       │   ├── shader_grain.agsl
    │       │   ├── shader_pixelate.agsl
    │       │   └── shader_edge_detection.agsl
    │       ├── drawable/
    │       │   ├── ic_launcher_foreground.xml
    │       │   └── ic_export.xml
    │       └── values/
    │           ├── strings.xml
    │           └── themes.xml
    │
    └── test/ + androidTest/                  // Unit and instrumented tests per component
```

---

## 4. Data Models

### 4.1 Domain Models

These are pure Kotlin data classes with no Android framework imports. They live in the `domain/model/` package. The Repository layer is responsible for mapping between these and Room entities or Firestore DTOs.

```kotlin
// domain/model/Canvas.kt
@Serializable
data class Canvas(
    val id: String,                          // UUID, generated locally on creation
    val ownerId: String,                     // Firebase Auth UID of creator
    val name: String,
    val widthPx: Int,                        // Canvas pixel dimensions
    val heightPx: Int,
    val layers: List<Layer>,                 // Ordered bottom (index 0) to top
    val collaboratorIds: List<String>,       // Firebase UIDs of invited users
    val isViewOnly: Boolean,                 // If true, non-owners cannot edit
    val createdAt: Long,                     // Unix timestamp millis
    val updatedAt: Long,
    val thumbnailLocalPath: String?,         // Path to locally cached thumbnail PNG
    val isPinned: Boolean                    // Pinned to top of Home grid
)
```

```kotlin
// domain/model/Layer.kt
@Serializable
data class Layer(
    val id: String,                          // UUID
    val canvasId: String,
    val ownerId: String,                     // Firebase UID of user who created it
    val name: String,                        // e.g. "Background", "Layer 2"
    val type: LayerType,
    val sourceBitmapPath: String?,           // Absolute local file path to source image.
                                             // Null for SOLID_COLOR layers.
    val transform: LayerTransform,
    val effectChain: List<Effect>,           // Ordered. Output of effect[N] → input of effect[N+1]
    val blendMode: BlendMode,
    val opacity: Float,                      // 0.0 (transparent) to 1.0 (fully opaque)
    val isVisible: Boolean,
    val isLocked: Boolean,                   // If true, gestures on this layer are ignored
    val maskPath: String?,                   // Absolute local path to alpha mask bitmap.
                                             // Null means no mask applied.
    val zIndex: Int,                         // Position in composite stack. 0 = bottommost.
    val updatedAt: Long,
    val textContent: TextLayerContent?,      // Non-null only when type == TEXT
    val solidColor: Int?                     // ARGB packed int. Non-null when type == SOLID_COLOR
)

@Serializable
enum class LayerType {
    IMAGE,          // Loaded from device gallery
    SOLID_COLOR,    // Flat fill layer — no source bitmap
    TEXT,           // Text rendered to bitmap
    AI_GENERATED    // Downloaded from DALL-E
}
```

```kotlin
// domain/model/LayerTransform.kt
@Serializable
data class LayerTransform(
    val translateX: Float = 0f,             // Pixels offset from canvas center
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,              // Degrees clockwise
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false
)
```

```kotlin
// domain/model/Effect.kt
// Sealed class hierarchy. Every effect is a distinct subtype.
// The `id` field uniquely identifies each effect instance within a chain.
// The `isEnabled` flag allows toggling without removing from the chain.
// All parameters are @Serializable so the chain serializes cleanly to JSON for Firestore.

@Serializable
sealed class Effect {
    abstract val id: String
    abstract val isEnabled: Boolean

    @Serializable
    data class BrightnessContrast(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val brightness: Float = 0f,        // Range: -1.0 to 1.0
        val contrast: Float = 0f           // Range: -1.0 to 1.0
    ) : Effect()

    @Serializable
    data class Exposure(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val stops: Float = 0f              // Range: -3.0 to +3.0 EV stops
    ) : Effect()

    @Serializable
    data class GaussianBlur(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val radius: Float = 5f             // Range: 0 to 50px. Higher = more blur.
    ) : Effect()

    @Serializable
    data class Sharpen(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val amount: Float = 0.5f           // Range: 0 to 1.0
    ) : Effect()

    @Serializable
    data class Vignette(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val intensity: Float = 0.5f,       // Range: 0 to 1.0. How dark the vignette is.
        val feather: Float = 0.5f          // Range: 0 to 1.0. How soft the vignette edge is.
    ) : Effect()

    @Serializable
    data class ColorGrade(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val shadowsR: Float = 0f, val shadowsG: Float = 0f, val shadowsB: Float = 0f,
        val midtonesR: Float = 0f, val midtonesG: Float = 0f, val midtonesB: Float = 0f,
        val highlightsR: Float = 0f, val highlightsG: Float = 0f, val highlightsB: Float = 0f
        // All channels range: -0.5 to +0.5
    ) : Effect()

    @Serializable
    data class HueSaturationLightness(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val hue: Float = 0f,               // Range: -180 to +180 degrees
        val saturation: Float = 0f,        // Range: -1.0 to +1.0
        val lightness: Float = 0f          // Range: -1.0 to +1.0
    ) : Effect()

    @Serializable
    data class ColorTemperature(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val temperature: Float = 0f,       // Range: -1.0 (cool/blue) to +1.0 (warm/orange)
        val tint: Float = 0f               // Range: -1.0 (green) to +1.0 (magenta)
    ) : Effect()

    @Serializable
    data class Grain(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val amount: Float = 0.3f,          // Range: 0 to 1.0
        val size: Float = 1f               // Range: 0.5 to 3.0
    ) : Effect()

    @Serializable
    data class Pixelate(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val blockSize: Float = 10f         // Range: 2 to 100px
    ) : Effect()

    @Serializable
    data class EdgeDetection(
        override val id: String = UUID.randomUUID().toString(),
        override val isEnabled: Boolean = true,
        val threshold: Float = 0.5f        // Range: 0 to 1.0
    ) : Effect()
}
```

```kotlin
// domain/model/BlendMode.kt
// 5 blend modes for MVP. Covers the full mathematical range needed.
// Normal = passthrough. Multiply = darken. Screen = lighten.
// Overlay = contrast. Soft Light = gentle contrast.
enum class BlendMode {
    NORMAL,       // Result = blend layer as-is at given opacity
    MULTIPLY,     // Result = base * blend. Always darker. Good for shadows.
    SCREEN,       // Result = 1 - (1-base)*(1-blend). Always lighter. Good for glows.
    OVERLAY,      // Result = Multiply if base < 0.5, Screen if base > 0.5. Boosts contrast.
    SOFT_LIGHT    // Gentler version of Overlay. Subtle contrast + saturation boost.
}
```

```kotlin
// domain/model/TextLayerContent.kt
@Serializable
data class TextLayerContent(
    val text: String,
    val fontFamily: String = "sans-serif",  // Maps to Typeface.create() family name
    val fontSizeSp: Float = 24f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val colorArgb: Int = 0xFF000000.toInt(),
    val alignment: String = "LEFT"          // "LEFT", "CENTER", "RIGHT"
)
```

```kotlin
// domain/model/Collaborator.kt
data class Collaborator(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val isOnline: Boolean,                  // true if lastSeen < 30 seconds ago
    val activeLayerId: String?,             // Which layer they're currently editing
    val presenceColor: Int                  // ARGB int — assigned deterministically from userId hash
)
```

```kotlin
// domain/model/HistoryEntry.kt
@Serializable
data class HistoryEntry(
    val id: String,
    val canvasId: String,
    val userId: String,
    val userName: String,
    val description: String,               // Human-readable. e.g. "Added Blur to Layer 2"
    val layerStackJson: String,            // Full JSON snapshot of List<Layer> at this point
    val timestamp: Long
)
```

### 4.2 Room Entity Schema

Room entities mirror domain models but with Android-specific annotations. The Repository maps between them.

```kotlin
// data/local/db/entity/CanvasEntity.kt
@Entity(tableName = "canvases")
data class CanvasEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    @ColumnInfo(name = "collaborator_ids_json")
    val collaboratorIdsJson: String,       // JSON array of UIDs
    val isViewOnly: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailLocalPath: String?        // Path to locally saved thumbnail PNG
)

// data/local/db/entity/LayerEntity.kt
@Entity(
    tableName = "layers",
    foreignKeys = [ForeignKey(
        entity = CanvasEntity::class,
        parentColumns = ["id"],
        childColumns = ["canvas_id"],
        onDelete = ForeignKey.CASCADE      // Deleting a canvas deletes all its layers
    )],
    indices = [Index(value = ["canvas_id"])]
)
data class LayerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "canvas_id") val canvasId: String,
    val ownerId: String,
    val name: String,
    val type: String,                      // LayerType.name()
    val sourceBitmapPath: String?,
    val transformJson: String,             // Serialized LayerTransform
    val effectChainJson: String,           // Serialized List<Effect>
    val blendMode: String,                 // BlendMode.name()
    val opacity: Float,
    val isVisible: Boolean,
    val isLocked: Boolean,
    val maskPath: String?,
    val zIndex: Int,
    val updatedAt: Long,
    val textContentJson: String?,          // Serialized TextLayerContent, nullable
    val solidColor: Int?
)

// data/local/db/entity/HistoryEntity.kt
@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(
        entity = CanvasEntity::class,
        parentColumns = ["id"],
        childColumns = ["canvas_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class HistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "canvas_id") val canvasId: String,
    val userId: String,
    val userName: String,
    val description: String,
    val layerStackJson: String,
    val timestamp: Long
)
```

### 4.3 DAO Interfaces

```kotlin
// data/local/db/dao/CanvasDao.kt
@Dao
interface CanvasDao {
    @Query("SELECT * FROM canvases ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllCanvases(): Flow<List<CanvasEntity>>   // Live updates via Flow

    @Query("SELECT * FROM canvases WHERE id = :canvasId")
    suspend fun getCanvas(canvasId: String): CanvasEntity?

    @Upsert
    suspend fun upsertCanvas(canvas: CanvasEntity)

    @Delete
    suspend fun deleteCanvas(canvas: CanvasEntity)

    @Query("UPDATE canvases SET isPinned = :isPinned WHERE id = :canvasId")
    suspend fun updatePinned(canvasId: String, isPinned: Boolean)

    @Query("UPDATE canvases SET thumbnailLocalPath = :path WHERE id = :canvasId")
    suspend fun updateThumbnail(canvasId: String, path: String)
}

// data/local/db/dao/LayerDao.kt
@Dao
interface LayerDao {
    @Query("SELECT * FROM layers WHERE canvas_id = :canvasId ORDER BY zIndex ASC")
    fun observeLayersForCanvas(canvasId: String): Flow<List<LayerEntity>>

    @Query("SELECT * FROM layers WHERE canvas_id = :canvasId ORDER BY zIndex ASC")
    suspend fun getLayersForCanvas(canvasId: String): List<LayerEntity>

    @Upsert
    suspend fun upsertLayer(layer: LayerEntity)

    @Upsert
    suspend fun upsertLayers(layers: List<LayerEntity>)

    @Query("DELETE FROM layers WHERE id = :layerId")
    suspend fun deleteLayer(layerId: String)

    @Query("DELETE FROM layers WHERE canvas_id = :canvasId")
    suspend fun deleteAllLayersForCanvas(canvasId: String)
}

// data/local/db/dao/HistoryDao.kt
@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE canvas_id = :canvasId ORDER BY timestamp DESC LIMIT 50")
    suspend fun getHistory(canvasId: String): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE canvas_id = :canvasId AND id NOT IN (SELECT id FROM history WHERE canvas_id = :canvasId ORDER BY timestamp DESC LIMIT 50)")
    suspend fun pruneHistory(canvasId: String)    // Keep only 50 most recent
}
```

### 4.4 Firestore Document Schema

Firestore stores canvas and layer **metadata only**. Bitmap pixel data is never sent to Firestore.

```
// Firestore collection hierarchy:

canvases/{canvasId}
├── Fields:
│   id: String
│   ownerId: String
│   name: String
│   widthPx: Int
│   heightPx: Int
│   collaboratorIds: Array<String>
│   isViewOnly: Boolean
│   updatedAt: Timestamp
│
├── layers/{layerId}                  // Subcollection
│   Fields:
│   id: String
│   ownerId: String
│   name: String
│   type: String
│   sourceBitmapFirebasePath: String? // Firebase Storage path (for collab image fetch)
│   transformJson: String             // Serialized LayerTransform
│   effectChainJson: String           // Serialized List<Effect>
│   blendMode: String
│   opacity: Float
│   isVisible: Boolean
│   isLocked: Boolean
│   maskFirebasePath: String?
│   zIndex: Int
│   updatedAt: Timestamp
│   textContentJson: String?
│   solidColor: Int?                  // (long in Firestore)
│
├── presence/{userId}                 // Subcollection — ephemeral
│   Fields:
│   userId: String
│   displayName: String
│   avatarUrl: String?
│   activeLayerId: String?
│   lastSeen: Timestamp               // Written every 20s and on layer select
│
└── history/{entryId}                 // Subcollection — local only for MVP
    // Not synced in MVP. Kept local in Room.
```

### 4.5 UserPreferences (DataStore)

```kotlin
// data/local/datastore/UserPreferencesDataStore.kt
data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val isDynamicColorEnabled: Boolean = true,
    val defaultCanvasWidth: Int = 1080,
    val defaultCanvasHeight: Int = 1080,
    val notifyCollaboratorJoined: Boolean = true,
    val notifyExportComplete: Boolean = true,
    val notifyAiComplete: Boolean = true
)

enum class AppTheme { LIGHT, DARK, SYSTEM }

// Keys
object PreferenceKeys {
    val THEME = stringPreferencesKey("theme")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val DEFAULT_CANVAS_WIDTH = intPreferencesKey("canvas_width")
    val DEFAULT_CANVAS_HEIGHT = intPreferencesKey("canvas_height")
    val NOTIFY_COLLAB = booleanPreferencesKey("notify_collab")
    val NOTIFY_EXPORT = booleanPreferencesKey("notify_export")
    val NOTIFY_AI = booleanPreferencesKey("notify_ai")
}
```

---

## 5. Architecture — MVI Pattern

### Why MVI Over MVVM

MVVM is the default Android pattern and works well for simple apps. CanvasX has two simultaneous sources of state change: the local user's gestures and intents, and the remote Firestore snapshot listener pushing other collaborators' changes. MVVM's two-way binding can create race conditions when both sources update at the same time. MVI forces all changes — both local and remote — through a single `handleIntent()` function, making the state machine deterministic and auditable. This is the same reason large production apps like Slack and Google Photos use unidirectional data flow architectures.

### MVI Contract Per Screen

```
[User action in Compose] → Intent → ViewModel.handleIntent() → UseCase → Repository
                                                                              ↓
                                                                    Room DB + Firestore
                                                                              ↓
                                                             ← Flow<UiState> ← StateFlow
                                                                              ↓
                                                     Compose collectAsStateWithLifecycle()
                                                                              ↓
                                                                   [Screen recomposes]
```

### Editor MVI — Full Contract

```kotlin
// ui/screens/editor/EditorIntent.kt
// Every possible user action is an explicit sealed class variant.
// No "update field X" with raw values — always domain-meaningful operations.

sealed class EditorIntent {

    // ── Layer Selection ───────────────────────────────────────────────────────
    data class SelectLayer(val layerId: String) : EditorIntent()
    data object DeselectLayer : EditorIntent()

    // ── Layer Transform (from gesture input on CanvasRenderer) ────────────────
    data class MoveLayer(val layerId: String, val deltaX: Float, val deltaY: Float) : EditorIntent()
    data class ScaleLayer(val layerId: String, val scaleFactor: Float, val anchor: Offset) : EditorIntent()
    data class RotateLayer(val layerId: String, val deltaDegrees: Float) : EditorIntent()
    data class FlipLayerHorizontal(val layerId: String) : EditorIntent()
    data class FlipLayerVertical(val layerId: String) : EditorIntent()
    data class ResetTransform(val layerId: String) : EditorIntent()

    // ── Layer Stack Management ────────────────────────────────────────────────
    data class ReorderLayer(val fromIndex: Int, val toIndex: Int) : EditorIntent()
    data class SetLayerVisibility(val layerId: String, val isVisible: Boolean) : EditorIntent()
    data class SetLayerLock(val layerId: String, val isLocked: Boolean) : EditorIntent()
    data class RenameLayer(val layerId: String, val newName: String) : EditorIntent()
    data class DeleteLayer(val layerId: String) : EditorIntent()
    data class DuplicateLayer(val layerId: String) : EditorIntent()

    // ── Layer Compositing Properties ──────────────────────────────────────────
    data class SetLayerOpacity(val layerId: String, val opacity: Float) : EditorIntent()
    data class SetBlendMode(val layerId: String, val blendMode: BlendMode) : EditorIntent()

    // ── Effect Chain ──────────────────────────────────────────────────────────
    data class AddEffect(val layerId: String, val effect: Effect) : EditorIntent()
    data class UpdateEffect(val layerId: String, val effect: Effect) : EditorIntent()
    data class RemoveEffect(val layerId: String, val effectId: String) : EditorIntent()
    data class ToggleEffectEnabled(val layerId: String, val effectId: String) : EditorIntent()
    data class ReorderEffect(val layerId: String, val fromIndex: Int, val toIndex: Int) : EditorIntent()

    // ── Layer Creation ────────────────────────────────────────────────────────
    data class AddImageLayer(val uri: Uri) : EditorIntent()
    data class AddSolidColorLayer(val colorArgb: Int) : EditorIntent()
    data class AddAiLayer(val prompt: String) : EditorIntent()

    // ── History ───────────────────────────────────────────────────────────────
    data object Undo : EditorIntent()
    data object Redo : EditorIntent()

    // ── AI Features ───────────────────────────────────────────────────────────
    data class ApplyRemoveBackground(val layerId: String) : EditorIntent()

    // ── UI Sheet State ────────────────────────────────────────────────────────
    data class ShowSheet(val sheet: EditorSheet) : EditorIntent()
    data object DismissSheet : EditorIntent()

    // ── Canvas-level ──────────────────────────────────────────────────────────
    data class RenameCanvas(val newName: String) : EditorIntent()

    // ── Remote Changes (dispatched internally when Firestore snapshot arrives) ─
    data class ApplyRemoteLayerUpdate(val layer: Layer) : EditorIntent()
    data class ApplyRemoteLayerDelete(val layerId: String) : EditorIntent()
    data class ApplyRemoteLayerAdd(val layer: Layer) : EditorIntent()
    data class ApplyRemotePresenceUpdate(val collaborator: Collaborator) : EditorIntent()
}
```

```kotlin
// ui/screens/editor/EditorUiState.kt
data class EditorUiState(
    val canvasId: String = "",
    val canvasName: String = "",
    val canvasWidthPx: Int = 1080,
    val canvasHeightPx: Int = 1080,
    val layers: List<Layer> = emptyList(),          // Ordered bottom (0) to top
    val selectedLayerId: String? = null,
    val compositedBitmap: Bitmap? = null,           // Emitted by RenderEngine
    val isRendering: Boolean = false,               // True while RenderEngine is processing
    val collaborators: List<Collaborator> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeSheet: EditorSheet = EditorSheet.None,
    val isLoadingCanvas: Boolean = true,            // True while initial canvas loads from Room
    val error: String? = null,                      // Non-null triggers error Snackbar
    val aiGenerationState: AiGenerationState = AiGenerationState.Idle,
    val removeBgState: RemoveBgState = RemoveBgState.Idle
) {
    // Derived properties — computed from state, not stored
    val selectedLayer: Layer? get() = layers.find { it.id == selectedLayerId }
    val layerCount: Int get() = layers.size
    val hasLayers: Boolean get() = layers.isNotEmpty()
}

// All bottom sheet states modeled explicitly.
// Only one sheet can be open at a time.
sealed class EditorSheet {
    data object None : EditorSheet()
    data object LayerPanel : EditorSheet()
    data object AddLayer : EditorSheet()
    data class EffectChain(val layerId: String) : EditorSheet()
    data class BlendModePicker(val layerId: String) : EditorSheet()
    data object Collaborators : EditorSheet()
    data object Export : EditorSheet()
}

sealed class AiGenerationState {
    data object Idle : AiGenerationState()
    data class Loading(val prompt: String) : AiGenerationState()
    data class Success(val newLayerId: String) : AiGenerationState()
    data class Error(val message: String) : AiGenerationState()
}

sealed class RemoveBgState {
    data object Idle : RemoveBgState()
    data class Loading(val layerId: String) : RemoveBgState()
    data class Success(val layerId: String) : RemoveBgState()
    data class Error(val message: String) : RemoveBgState()
}
```

```kotlin
// ui/screens/editor/EditorViewModel.kt
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCanvasUseCase: GetCanvasUseCase,
    private val addImageLayerUseCase: AddImageLayerUseCase,
    private val addSolidColorLayerUseCase: AddSolidColorLayerUseCase,
    private val updateLayerTransformUseCase: UpdateLayerTransformUseCase,
    private val reorderLayersUseCase: ReorderLayersUseCase,
    private val addEffectUseCase: AddEffectToLayerUseCase,
    private val updateEffectUseCase: UpdateEffectUseCase,
    private val removeEffectUseCase: RemoveEffectUseCase,
    private val setBlendModeUseCase: SetBlendModeUseCase,
    private val setOpacityUseCase: SetLayerOpacityUseCase,
    private val deleteLayerUseCase: DeleteLayerUseCase,
    private val generateAiLayerUseCase: GenerateAiLayerUseCase,
    private val removeBackgroundUseCase: RemoveBackgroundUseCase,
    private val undoUseCase: UndoUseCase,
    private val redoUseCase: RedoUseCase,
    private val pushHistoryUseCase: PushHistoryUseCase,
    private val firestoreLayerDataSource: FirestoreLayerDataSource,
    private val renderEngine: RenderEngine
) : ViewModel() {

    private val canvasId: String = savedStateHandle["canvasId"]!!

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        loadCanvas()
        startRenderLoop()
        observeRemoteChanges()
        startPresenceHeartbeat()
    }

    fun handleIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.SelectLayer -> handleSelectLayer(intent)
            is EditorIntent.MoveLayer -> handleMoveLayer(intent)
            is EditorIntent.ScaleLayer -> handleScaleLayer(intent)
            is EditorIntent.RotateLayer -> handleRotateLayer(intent)
            is EditorIntent.ReorderLayer -> handleReorderLayer(intent)
            is EditorIntent.SetLayerOpacity -> handleSetOpacity(intent)
            is EditorIntent.SetBlendMode -> handleSetBlendMode(intent)
            is EditorIntent.AddEffect -> handleAddEffect(intent)
            is EditorIntent.UpdateEffect -> handleUpdateEffect(intent)
            is EditorIntent.RemoveEffect -> handleRemoveEffect(intent)
            is EditorIntent.ToggleEffectEnabled -> handleToggleEffect(intent)
            is EditorIntent.AddImageLayer -> handleAddImageLayer(intent)
            is EditorIntent.AddSolidColorLayer -> handleAddSolidColorLayer(intent)
            is EditorIntent.AddAiLayer -> handleAddAiLayer(intent)
            is EditorIntent.DeleteLayer -> handleDeleteLayer(intent)
            is EditorIntent.DuplicateLayer -> handleDuplicateLayer(intent)
            is EditorIntent.ApplyRemoveBackground -> handleRemoveBackground(intent)
            is EditorIntent.Undo -> handleUndo()
            is EditorIntent.Redo -> handleRedo()
            is EditorIntent.ShowSheet -> _uiState.update { it.copy(activeSheet = intent.sheet) }
            is EditorIntent.DismissSheet -> _uiState.update { it.copy(activeSheet = EditorSheet.None) }
            is EditorIntent.ApplyRemoteLayerUpdate -> handleRemoteUpdate(intent)
            is EditorIntent.ApplyRemoteLayerDelete -> handleRemoteDelete(intent)
            is EditorIntent.ApplyRemoteLayerAdd -> handleRemoteAdd(intent)
            // ... all other intents
        }
    }

    private fun handleAddImageLayer(intent: EditorIntent.AddImageLayer) {
        viewModelScope.launch {
            // Save history snapshot before change
            pushHistoryUseCase(canvasId, _uiState.value.layers, "Added image layer")

            val result = addImageLayerUseCase(canvasId, intent.uri)
            result.onSuccess { newLayer ->
                _uiState.update { state ->
                    state.copy(layers = state.layers + newLayer)
                }
                triggerRender()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    private fun handleUpdateEffect(intent: EditorIntent.UpdateEffect) {
        viewModelScope.launch {
            // Update effect in state immediately for responsive UI
            _uiState.update { state ->
                state.copy(
                    layers = state.layers.map { layer ->
                        if (layer.id == intent.layerId) {
                            layer.copy(
                                effectChain = layer.effectChain.map { effect ->
                                    if (effect.id == intent.effect.id) intent.effect else effect
                                }
                            )
                        } else layer
                    }
                )
            }
            triggerRender()
            // Persist to Room + Firestore asynchronously
            updateEffectUseCase(intent.layerId, intent.effect)
        }
    }

    private fun triggerRender() {
        renderEngine.requestRender(_uiState.value.layers)
    }

    private fun startRenderLoop() {
        viewModelScope.launch {
            renderEngine.compositedBitmap.collect { bitmap ->
                _uiState.update { it.copy(compositedBitmap = bitmap, isRendering = false) }
            }
        }
        viewModelScope.launch {
            renderEngine.startRenderLoop(
                _uiState.value.canvasWidthPx,
                _uiState.value.canvasHeightPx
            )
        }
    }

    private fun observeRemoteChanges() {
        viewModelScope.launch {
            firestoreLayerDataSource.observeLayers(canvasId).collect { remoteLayers ->
                // Filter out changes that originated from this device
                // to avoid applying your own writes as remote updates
                remoteLayers.forEach { remoteLayer ->
                    val localLayer = _uiState.value.layers.find { it.id == remoteLayer.id }
                    if (localLayer == null) {
                        handleIntent(EditorIntent.ApplyRemoteLayerAdd(remoteLayer))
                    } else if (remoteLayer.updatedAt > localLayer.updatedAt) {
                        handleIntent(EditorIntent.ApplyRemoteLayerUpdate(remoteLayer))
                    }
                }
            }
        }
    }

    private fun startPresenceHeartbeat() {
        viewModelScope.launch {
            while (true) {
                delay(20_000L) // Every 20 seconds
                firestoreLayerDataSource.updatePresence(
                    canvasId,
                    FirebaseAuth.getInstance().currentUser?.uid ?: return@launch,
                    _uiState.value.selectedLayerId
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Remove presence document when user leaves canvas
        viewModelScope.launch {
            firestoreLayerDataSource.removePresence(canvasId,
                FirebaseAuth.getInstance().currentUser?.uid ?: return@launch)
        }
    }
}
```

---

## 6. Rendering Engine

This is the core of CanvasX. Every other feature exists to serve this engine. Read this section multiple times before writing any rendering code.

### Design Principles

1. **The render loop runs on `Dispatchers.Default`** — never on the main thread
2. **`Channel(CONFLATED)` queues render requests** — only the latest state ever renders, preventing queue buildup during fast slider dragging
3. **Dirty flagging** — only layers whose visual parameters changed since last render are re-processed. A layer that was just moved does NOT re-apply its effect chain.
4. **Per-layer parallelism** — dirty layers are processed in parallel using `async {}`. Compositing is sequential (bottom-to-top order is mandatory).
5. **AGSL shaders execute on the GPU** — all pixel math runs on the GPU, not CPU. This is what makes real-time effect preview possible on mobile.

### 6.1 RenderEngine.kt

```kotlin
// engine/RenderEngine.kt
class RenderEngine @Inject constructor(
    private val effectProcessor: EffectProcessor,
    private val blendModeProcessor: BlendModeProcessor,
    private val maskProcessor: MaskProcessor,
    private val transformProcessor: TransformProcessor,
    private val dirtyFlagTracker: DirtyFlagTracker,
    private val renderCache: RenderCache
) {
    // CONFLATED: if a new List<Layer> arrives before the current render finishes,
    // the in-progress render is discarded and only the newest state renders.
    // This is identical to how video preview renderers handle fast timeline scrubbing.
    private val renderChannel = Channel<RenderRequest>(Channel.CONFLATED)

    // Emit the latest composited bitmap. Collected by EditorViewModel.
    val compositedBitmap = MutableStateFlow<Bitmap?>(null)
    val isRendering = MutableStateFlow(false)

    data class RenderRequest(
        val layers: List<Layer>,
        val canvasWidthPx: Int,
        val canvasHeightPx: Int
    )

    // Called once from EditorViewModel.init in viewModelScope
    suspend fun startRenderLoop(canvasWidthPx: Int, canvasHeightPx: Int) {
        // This suspend function runs for the lifetime of the ViewModel
        for (request in renderChannel) {
            isRendering.value = true
            try {
                val result = renderFrame(request)
                compositedBitmap.value = result
            } catch (e: CancellationException) {
                throw e  // Always rethrow CancellationException
            } catch (e: Exception) {
                // Log error but don't crash — show last valid frame
                Log.e("RenderEngine", "Render failed", e)
            } finally {
                isRendering.value = false
            }
        }
    }

    // Called from ViewModel on every state change that affects visuals
    fun requestRender(layers: List<Layer>, canvasWidthPx: Int, canvasHeightPx: Int) {
        renderChannel.trySend(RenderRequest(layers, canvasWidthPx, canvasHeightPx))
    }

    private suspend fun renderFrame(request: RenderRequest): Bitmap =
        withContext(Dispatchers.Default) {

            val visibleLayers = request.layers.filter { it.isVisible }

            // ── Step 1: Process dirty layers in parallel ──────────────────────
            // Each layer's effect chain is independent — safe to parallelize.
            // Only layers whose visual params changed since last render are processed.
            val parallelJobs = visibleLayers
                .filter { layer -> dirtyFlagTracker.isDirty(layer) }
                .map { layer ->
                    async {
                        val sourceBitmap = when (layer.type) {
                            LayerType.SOLID_COLOR -> {
                                // Solid color layers don't have a source bitmap —
                                // create a flat fill bitmap of canvas size
                                createSolidBitmap(
                                    layer.solidColor ?: Color.WHITE,
                                    request.canvasWidthPx,
                                    request.canvasHeightPx
                                )
                            }
                            else -> {
                                // Load from local file path on Dispatchers.IO
                                withContext(Dispatchers.IO) {
                                    BitmapUtils.loadBitmapFromPath(layer.sourceBitmapPath!!)
                                }
                            }
                        }

                        // Apply effect chain (all enabled effects in order)
                        val effectApplied = effectProcessor.apply(
                            source = sourceBitmap,
                            chain = layer.effectChain
                        )

                        // Apply mask if present
                        val masked = if (layer.maskPath != null) {
                            val maskBitmap = withContext(Dispatchers.IO) {
                                BitmapUtils.loadBitmapFromPath(layer.maskPath)
                            }
                            maskProcessor.apply(effectApplied, maskBitmap)
                        } else {
                            effectApplied
                        }

                        // Mark as clean
                        dirtyFlagTracker.markClean(layer.id, layer)

                        layer.id to masked
                    }
                }

            // Await all parallel processing jobs
            val freshBitmaps: Map<String, Bitmap> = parallelJobs.awaitAll().toMap()

            // Update cache with newly processed bitmaps
            freshBitmaps.forEach { (id, bitmap) -> renderCache.put(id, bitmap) }

            // ── Step 2: Composite all layers bottom-to-top (sequential) ───────
            // This step CANNOT be parallelized. Order is mandatory.
            // Layer at index 0 is the bottom. Layer at last index is on top.
            var composite = Bitmap.createBitmap(
                request.canvasWidthPx,
                request.canvasHeightPx,
                Bitmap.Config.ARGB_8888
            )

            visibleLayers.forEach { layer ->
                val layerBitmap = renderCache.get(layer.id) ?: return@forEach

                // Apply transform (translate, scale, rotate) to place layer on canvas
                val transformed = transformProcessor.apply(
                    layerBitmap = layerBitmap,
                    transform = layer.transform,
                    canvasWidth = request.canvasWidthPx,
                    canvasHeight = request.canvasHeightPx
                )

                // Composite this layer onto the running composite
                composite = blendModeProcessor.composite(
                    base = composite,
                    blend = transformed,
                    blendMode = layer.blendMode,
                    opacity = layer.opacity
                )
            }

            composite
        }

    private fun createSolidBitmap(colorArgb: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(colorArgb)
        return bitmap
    }
}
```

### 6.2 AgslShaders.kt — All Shader Source Strings

```kotlin
// engine/AgslShaders.kt
// All AGSL shader programs as Kotlin string constants.
// AGSL (Android Graphics Shading Language) is a subset of GLSL ES 1.0
// with Android-specific extensions. Requires Android 13+ (API 33).
// Shaders execute on the GPU — not the CPU.

object AgslShaders {

    // ── Blend Mode Shaders ────────────────────────────────────────────────────
    // Each blend shader takes two input textures (base, blend) and an opacity float.
    // It outputs the blended result for each pixel coordinate.

    val MULTIPLY_BLEND = """
        uniform shader base;
        uniform shader blend;
        uniform float opacity;

        half4 main(float2 coord) {
            half4 baseColor = base.eval(coord);
            half4 blendColor = blend.eval(coord);
            // Multiply: darkens. base * blend. Black stays black, white has no effect.
            half4 result = baseColor * blendColor;
            return mix(baseColor, result, blendColor.a * opacity);
        }
    """.trimIndent()

    val SCREEN_BLEND = """
        uniform shader base;
        uniform shader blend;
        uniform float opacity;

        half4 main(float2 coord) {
            half4 b = base.eval(coord);
            half4 s = blend.eval(coord);
            // Screen: lightens. Inverse of Multiply on inverted inputs.
            // 1 - (1-base) * (1-blend). White stays white, black has no effect.
            half4 result = half4(1.0) - (half4(1.0) - b) * (half4(1.0) - s);
            result.a = b.a;
            return mix(b, result, s.a * opacity);
        }
    """.trimIndent()

    val OVERLAY_BLEND = """
        uniform shader base;
        uniform shader blend;
        uniform float opacity;

        half4 main(float2 coord) {
            half4 b = base.eval(coord);
            half4 s = blend.eval(coord);
            // Overlay: Multiply where base is dark, Screen where base is light.
            // Increases contrast. Midtones shift most.
            half3 result;
            result.r = b.r < 0.5 ? 2.0*b.r*s.r : 1.0 - 2.0*(1.0-b.r)*(1.0-s.r);
            result.g = b.g < 0.5 ? 2.0*b.g*s.g : 1.0 - 2.0*(1.0-b.g)*(1.0-s.g);
            result.b = b.b < 0.5 ? 2.0*b.b*s.b : 1.0 - 2.0*(1.0-b.b)*(1.0-s.b);
            return mix(b, half4(result, b.a), s.a * opacity);
        }
    """.trimIndent()

    val SOFT_LIGHT_BLEND = """
        uniform shader base;
        uniform shader blend;
        uniform float opacity;

        half4 main(float2 coord) {
            half4 b = base.eval(coord);
            half4 s = blend.eval(coord);
            // Soft Light: gentler Overlay. Less contrast boost, preserves midtones better.
            // Photoshop formula variant.
            half3 result;
            result.r = s.r < 0.5
                ? b.r - (1.0-2.0*s.r)*b.r*(1.0-b.r)
                : b.r + (2.0*s.r-1.0)*(sqrt(b.r)-b.r);
            result.g = s.g < 0.5
                ? b.g - (1.0-2.0*s.g)*b.g*(1.0-b.g)
                : b.g + (2.0*s.g-1.0)*(sqrt(b.g)-b.g);
            result.b = s.b < 0.5
                ? b.b - (1.0-2.0*s.b)*b.b*(1.0-b.b)
                : b.b + (2.0*s.b-1.0)*(sqrt(b.b)-b.b);
            return mix(b, half4(result, b.a), s.a * opacity);
        }
    """.trimIndent()

    // ── Effect Shaders ────────────────────────────────────────────────────────

    val BRIGHTNESS_CONTRAST = """
        uniform shader source;
        uniform float brightness;    // -1.0 to 1.0
        uniform float contrast;      // -1.0 to 1.0

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            // Apply brightness: simple additive offset
            half3 rgb = color.rgb + half3(brightness);
            // Apply contrast: scale around midpoint 0.5
            float contrastFactor = (1.0 + contrast);
            rgb = (rgb - 0.5) * contrastFactor + 0.5;
            rgb = clamp(rgb, 0.0, 1.0);
            return half4(rgb, color.a);
        }
    """.trimIndent()

    val EXPOSURE = """
        uniform shader source;
        uniform float stops;    // -3.0 to 3.0 EV stops

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            // Exposure in stops: multiply by 2^stops
            float factor = pow(2.0, stops);
            half3 rgb = clamp(color.rgb * factor, 0.0, 1.0);
            return half4(rgb, color.a);
        }
    """.trimIndent()

    // Gaussian blur requires two passes (horizontal + vertical) for performance.
    // The horizontal pass is applied first to a temp bitmap, then vertical to the result.
    val GAUSSIAN_BLUR_HORIZONTAL = """
        uniform shader source;
        uniform float radius;       // 0 to 50
        uniform float imageWidth;   // Source image width in pixels

        half4 main(float2 coord) {
            int r = int(radius);
            if (r == 0) return source.eval(coord);
            half4 sum = half4(0.0);
            float weightSum = 0.0;
            float sigma = float(r) / 3.0;
            for (int i = -r; i <= r; i++) {
                float weight = exp(-float(i*i) / (2.0*sigma*sigma));
                sum += source.eval(float2(coord.x + float(i), coord.y)) * weight;
                weightSum += weight;
            }
            return sum / weightSum;
        }
    """.trimIndent()

    val GAUSSIAN_BLUR_VERTICAL = """
        uniform shader source;
        uniform float radius;
        uniform float imageHeight;

        half4 main(float2 coord) {
            int r = int(radius);
            if (r == 0) return source.eval(coord);
            half4 sum = half4(0.0);
            float weightSum = 0.0;
            float sigma = float(r) / 3.0;
            for (int i = -r; i <= r; i++) {
                float weight = exp(-float(i*i) / (2.0*sigma*sigma));
                sum += source.eval(float2(coord.x, coord.y + float(i))) * weight;
                weightSum += weight;
            }
            return sum / weightSum;
        }
    """.trimIndent()

    val VIGNETTE = """
        uniform shader source;
        uniform float intensity;    // 0 to 1.0
        uniform float feather;      // 0 to 1.0
        uniform float2 resolution;

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            // Normalized UV coordinates (0,0) center, (-1,-1) to (1,1) edges
            float2 uv = (coord / resolution) * 2.0 - 1.0;
            // Distance from center
            float dist = length(uv);
            // Smooth vignette based on feather
            float vignetteRadius = 1.0 - feather * 0.5;
            float vignette = smoothstep(vignetteRadius, vignetteRadius - feather * 0.5, dist);
            // Apply: darken edges by intensity
            float darkening = 1.0 - intensity * (1.0 - vignette);
            return half4(color.rgb * darkening, color.a);
        }
    """.trimIndent()

    val COLOR_TEMPERATURE = """
        uniform shader source;
        uniform float temperature;   // -1.0 cool to +1.0 warm
        uniform float tint;          // -1.0 green to +1.0 magenta

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            // Warm = boost red + reduce blue. Cool = boost blue + reduce red.
            float warmShift = temperature * 0.15;
            float tintShift = tint * 0.1;
            half3 rgb = color.rgb;
            rgb.r = clamp(rgb.r + warmShift + tintShift, 0.0, 1.0);
            rgb.g = clamp(rgb.g - tintShift * 0.5, 0.0, 1.0);
            rgb.b = clamp(rgb.b - warmShift + tintShift, 0.0, 1.0);
            return half4(rgb, color.a);
        }
    """.trimIndent()

    val HSL = """
        uniform shader source;
        uniform float hue;           // -180 to +180 degrees
        uniform float saturation;    // -1.0 to +1.0
        uniform float lightness;     // -1.0 to +1.0

        // RGB to HSL conversion
        half3 rgb2hsl(half3 c) {
            float maxC = max(max(c.r, c.g), c.b);
            float minC = min(min(c.r, c.g), c.b);
            float l = (maxC + minC) / 2.0;
            if (maxC == minC) return half3(0.0, 0.0, l);
            float d = maxC - minC;
            float s = l > 0.5 ? d / (2.0 - maxC - minC) : d / (maxC + minC);
            float h;
            if (maxC == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
            else if (maxC == c.g) h = (c.b - c.r) / d + 2.0;
            else h = (c.r - c.g) / d + 4.0;
            h /= 6.0;
            return half3(h, s, l);
        }

        float hue2rgb(float p, float q, float t) {
            if (t < 0.0) t += 1.0;
            if (t > 1.0) t -= 1.0;
            if (t < 1.0/6.0) return p + (q-p)*6.0*t;
            if (t < 1.0/2.0) return q;
            if (t < 2.0/3.0) return p + (q-p)*(2.0/3.0-t)*6.0;
            return p;
        }

        half3 hsl2rgb(half3 hsl) {
            if (hsl.y == 0.0) return half3(hsl.z);
            float q = hsl.z < 0.5 ? hsl.z*(1.0+hsl.y) : hsl.z+hsl.y-hsl.z*hsl.y;
            float p = 2.0*hsl.z - q;
            return half3(hue2rgb(p,q,hsl.x+1.0/3.0), hue2rgb(p,q,hsl.x), hue2rgb(p,q,hsl.x-1.0/3.0));
        }

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            half3 hsl = rgb2hsl(color.rgb);
            hsl.x = fract(hsl.x + hue / 360.0);
            hsl.y = clamp(hsl.y + saturation, 0.0, 1.0);
            hsl.z = clamp(hsl.z + lightness, 0.0, 1.0);
            return half4(hsl2rgb(hsl), color.a);
        }
    """.trimIndent()

    val SHARPEN = """
        uniform shader source;
        uniform float amount;        // 0 to 1.0
        uniform float2 resolution;

        half4 main(float2 coord) {
            // Unsharp mask: sharpen = original + amount * (original - blur)
            half4 center = source.eval(coord);
            half4 blur = (
                source.eval(coord + float2(-1, 0)) +
                source.eval(coord + float2(1, 0)) +
                source.eval(coord + float2(0, -1)) +
                source.eval(coord + float2(0, 1))
            ) / 4.0;
            half4 sharpened = center + (center - blur) * amount;
            return clamp(sharpened, 0.0, 1.0);
        }
    """.trimIndent()

    val GRAIN = """
        uniform shader source;
        uniform float amount;
        uniform float size;
        uniform float time;          // Seeded from System.currentTimeMillis() for variation

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            float grain = rand(floor(coord / size) + time) * 2.0 - 1.0;
            half3 noisy = color.rgb + half3(grain * amount);
            return half4(clamp(noisy, 0.0, 1.0), color.a);
        }
    """.trimIndent()

    val PIXELATE = """
        uniform shader source;
        uniform float blockSize;    // 2 to 100px

        half4 main(float2 coord) {
            float2 snapped = floor(coord / blockSize) * blockSize + blockSize * 0.5;
            return source.eval(snapped);
        }
    """.trimIndent()

    val EDGE_DETECTION = """
        uniform shader source;
        uniform float threshold;

        half4 main(float2 coord) {
            // Sobel edge detection
            half4 tl = source.eval(coord + float2(-1,-1));
            half4 tc = source.eval(coord + float2(0,-1));
            half4 tr = source.eval(coord + float2(1,-1));
            half4 ml = source.eval(coord + float2(-1,0));
            half4 mr = source.eval(coord + float2(1,0));
            half4 bl = source.eval(coord + float2(-1,1));
            half4 bc = source.eval(coord + float2(0,1));
            half4 br = source.eval(coord + float2(1,1));

            half gx = (-tl.r - 2.0*ml.r - bl.r + tr.r + 2.0*mr.r + br.r);
            half gy = (-tl.r - 2.0*tc.r - tr.r + bl.r + 2.0*bc.r + br.r);
            half edge = sqrt(gx*gx + gy*gy);
            edge = step(threshold, edge);
            return half4(half3(edge), 1.0);
        }
    """.trimIndent()
}
```

### 6.3 EffectProcessor.kt

```kotlin
// engine/EffectProcessor.kt
class EffectProcessor @Inject constructor() {

    // Apply an ordered effect chain to a source bitmap.
    // Each enabled effect's output is the next effect's input.
    // Disabled effects pass their input through unchanged.
    // Runs on Dispatchers.Default (called from within RenderEngine coroutine scope).
    suspend fun apply(source: Bitmap, chain: List<Effect>): Bitmap =
        withContext(Dispatchers.Default) {
            chain.fold(source) { current, effect ->
                if (effect.isEnabled) applyEffect(current, effect) else current
            }
        }

    private fun applyEffect(source: Bitmap, effect: Effect): Bitmap = when (effect) {
        is Effect.BrightnessContrast -> applyShader(
            source = source,
            shaderSource = AgslShaders.BRIGHTNESS_CONTRAST,
            uniforms = { shader ->
                shader.setFloatUniform("brightness", effect.brightness)
                shader.setFloatUniform("contrast", effect.contrast)
            }
        )
        is Effect.Exposure -> applyShader(
            source = source,
            shaderSource = AgslShaders.EXPOSURE,
            uniforms = { shader ->
                shader.setFloatUniform("stops", effect.stops)
            }
        )
        is Effect.GaussianBlur -> applyTwoPassBlur(source, effect.radius)
        is Effect.Sharpen -> applyShader(
            source = source,
            shaderSource = AgslShaders.SHARPEN,
            uniforms = { shader ->
                shader.setFloatUniform("amount", effect.amount)
                shader.setFloatUniform("resolution", floatArrayOf(source.width.toFloat(), source.height.toFloat()))
            }
        )
        is Effect.Vignette -> applyShader(
            source = source,
            shaderSource = AgslShaders.VIGNETTE,
            uniforms = { shader ->
                shader.setFloatUniform("intensity", effect.intensity)
                shader.setFloatUniform("feather", effect.feather)
                shader.setFloatUniform("resolution", floatArrayOf(source.width.toFloat(), source.height.toFloat()))
            }
        )
        is Effect.HueSaturationLightness -> applyShader(
            source = source,
            shaderSource = AgslShaders.HSL,
            uniforms = { shader ->
                shader.setFloatUniform("hue", effect.hue)
                shader.setFloatUniform("saturation", effect.saturation)
                shader.setFloatUniform("lightness", effect.lightness)
            }
        )
        is Effect.ColorTemperature -> applyShader(
            source = source,
            shaderSource = AgslShaders.COLOR_TEMPERATURE,
            uniforms = { shader ->
                shader.setFloatUniform("temperature", effect.temperature)
                shader.setFloatUniform("tint", effect.tint)
            }
        )
        is Effect.Grain -> applyShader(
            source = source,
            shaderSource = AgslShaders.GRAIN,
            uniforms = { shader ->
                shader.setFloatUniform("amount", effect.amount)
                shader.setFloatUniform("size", effect.size)
                shader.setFloatUniform("time", (System.currentTimeMillis() % 1000).toFloat() / 1000f)
            }
        )
        is Effect.Pixelate -> applyShader(
            source = source,
            shaderSource = AgslShaders.PIXELATE,
            uniforms = { shader ->
                shader.setFloatUniform("blockSize", effect.blockSize)
            }
        )
        is Effect.EdgeDetection -> applyShader(
            source = source,
            shaderSource = AgslShaders.EDGE_DETECTION,
            uniforms = { shader ->
                shader.setFloatUniform("threshold", effect.threshold)
            }
        )
        else -> source
    }

    // Generic shader application helper. Creates a RuntimeShader, binds the source
    // bitmap as an input uniform, applies caller-provided additional uniforms,
    // draws to a new output bitmap, and returns it.
    private fun applyShader(
        source: Bitmap,
        shaderSource: String,
        uniforms: (RuntimeShader) -> Unit
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val runtimeShader = RuntimeShader(shaderSource)
        runtimeShader.setInputBuffer(
            "source",
            BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        uniforms(runtimeShader)
        val paint = Paint().apply { shader = runtimeShader }
        Canvas(output).drawRect(
            0f, 0f, source.width.toFloat(), source.height.toFloat(), paint
        )
        return output
    }

    // Gaussian blur requires two passes for performance.
    // A single-pass 2D Gaussian on a 50px radius would require ~10,000 texture samples per pixel.
    // Two separable 1D passes require only ~200 samples per pixel.
    private fun applyTwoPassBlur(source: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return source

        // Pass 1: Horizontal blur
        val horizontal = applyShader(source, AgslShaders.GAUSSIAN_BLUR_HORIZONTAL) { shader ->
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("imageWidth", source.width.toFloat())
        }
        // Pass 2: Vertical blur on horizontal result
        return applyShader(horizontal, AgslShaders.GAUSSIAN_BLUR_VERTICAL) { shader ->
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("imageHeight", source.height.toFloat())
        }
    }
}
```

### 6.4 BlendModeProcessor.kt

```kotlin
// engine/BlendModeProcessor.kt
class BlendModeProcessor @Inject constructor() {

    // Composite a blend layer on top of a base layer using the given blend mode.
    // Returns a new bitmap. Does not mutate inputs.
    // 'base' is the running composite. 'blend' is the current layer (already transformed).
    fun composite(
        base: Bitmap,
        blend: Bitmap,
        blendMode: BlendMode,
        opacity: Float
    ): Bitmap {
        if (blendMode == BlendMode.NORMAL) {
            // Normal mode: no AGSL needed. Use standard Android Canvas with Paint alpha.
            return compositeNormal(base, blend, opacity)
        }

        val shaderSource = when (blendMode) {
            BlendMode.MULTIPLY -> AgslShaders.MULTIPLY_BLEND
            BlendMode.SCREEN -> AgslShaders.SCREEN_BLEND
            BlendMode.OVERLAY -> AgslShaders.OVERLAY_BLEND
            BlendMode.SOFT_LIGHT -> AgslShaders.SOFT_LIGHT_BLEND
            BlendMode.NORMAL -> throw IllegalStateException("Handled above")
        }

        val output = base.copy(base.config!!, true)  // Start with base as mutable copy
        val runtimeShader = RuntimeShader(shaderSource)
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
        Canvas(output).drawRect(0f, 0f, base.width.toFloat(), base.height.toFloat(), paint)
        return output
    }

    private fun compositeNormal(base: Bitmap, blend: Bitmap, opacity: Float): Bitmap {
        val output = base.copy(base.config!!, true)
        val paint = Paint().apply { alpha = (opacity * 255).toInt() }
        Canvas(output).drawBitmap(blend, 0f, 0f, paint)
        return output
    }
}
```

### 6.5 DirtyFlagTracker.kt

```kotlin
// engine/DirtyFlagTracker.kt
// Tracks which layers have changed since their last render.
// A layer is dirty if ANY of its visual parameters changed.
// Note: transform is NOT tracked here. Transforms are applied during compositing
// (Step 2 of renderFrame), not during per-layer effect processing (Step 1).
// So moving a layer does NOT trigger a re-render of its effect chain.
class DirtyFlagTracker {
    private val layerStateHashes = ConcurrentHashMap<String, Int>()

    fun isDirty(layer: Layer): Boolean {
        val cached = layerStateHashes[layer.id] ?: return true  // New layer = always dirty
        return cached != computeHash(layer)
    }

    fun markClean(layerId: String, layer: Layer) {
        layerStateHashes[layerId] = computeHash(layer)
    }

    fun invalidate(layerId: String) {
        layerStateHashes.remove(layerId)
    }

    fun invalidateAll() {
        layerStateHashes.clear()
    }

    // Hash covers all properties that affect pixel output.
    // transform, isVisible, isLocked are deliberately excluded.
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
}
```

### 6.6 RenderCache.kt

```kotlin
// engine/RenderCache.kt
// In-memory LRU cache for processed layer bitmaps.
// Avoids re-applying effect chains on every render for unchanged layers.
// Cache is cleared when memory is low (onTrimMemory).
class RenderCache @Inject constructor() {
    // LRU cache. Max 20 entries. Older entries evicted first.
    private val cache = LruCache<String, Bitmap>(20)

    fun get(layerId: String): Bitmap? = cache.get(layerId)

    fun put(layerId: String, bitmap: Bitmap) = cache.put(layerId, bitmap)

    fun remove(layerId: String) = cache.remove(layerId)

    fun clear() = cache.evictAll()

    // Called from Application.onTrimMemory()
    fun onLowMemory() = cache.evictAll()
}
```

### 6.7 TransformProcessor.kt

```kotlin
// engine/TransformProcessor.kt
// Applies LayerTransform to position, scale, and rotate a layer bitmap
// within the canvas coordinate space.
class TransformProcessor @Inject constructor() {

    fun apply(
        layerBitmap: Bitmap,
        transform: LayerTransform,
        canvasWidth: Int,
        canvasHeight: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val matrix = Matrix().apply {
            // 1. Start at canvas center
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f

            // 2. Place layer center at canvas center
            val layerCenterX = layerBitmap.width / 2f
            val layerCenterY = layerBitmap.height / 2f

            // 3. Apply flip
            if (transform.isFlippedHorizontal) postScale(-1f, 1f, layerCenterX, layerCenterY)
            if (transform.isFlippedVertical) postScale(1f, -1f, layerCenterX, layerCenterY)

            // 4. Apply scale around layer center
            postScale(transform.scaleX, transform.scaleY, layerCenterX, layerCenterY)

            // 5. Apply rotation around layer center
            postRotate(transform.rotation, layerCenterX, layerCenterY)

            // 6. Translate to canvas center + user offset
            postTranslate(
                centerX - layerCenterX + transform.translateX,
                centerY - layerCenterY + transform.translateY
            )
        }

        canvas.drawBitmap(layerBitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
        return output
    }
}
```

---

## 7. Every Screen — Full Specification

---

### Screen 1: Splash Screen

**File:** `ui/screens/splash/SplashScreen.kt`

**Purpose:** Cold start entry point. Uses the official AndroidX Splash Screen API for the animated icon. After animation completes, checks auth state and routes accordingly.

**Implementation notes:**
- In `MainActivity.onCreate()`, call `installSplashScreen()` before `setContent { }`.
- Use `splashScreen.setKeepOnScreenCondition { authViewModel.isLoading.value }` to hold the splash until auth state resolves.
- No custom Compose UI needed — the splash screen is handled entirely by the system API and the theme.

**Routing logic (in AuthViewModel or SplashViewModel):**
```kotlin
init {
    viewModelScope.launch {
        val user = Firebase.auth.currentUser
        if (user == null) {
            _destination.value = SplashDestination.Auth
        } else {
            _destination.value = SplashDestination.Home
        }
    }
}
// Note: No onboarding check. Onboarding has been cut.
// Permissions are requested on first editor launch instead.
```

**Navigation triggers:**
- User not logged in → AuthScreen (clear back stack)
- User logged in → HomeScreen (clear back stack)

---

### Screen 2: Auth Screen

**File:** `ui/screens/auth/AuthScreen.kt`

**Purpose:** Google Sign-In. Single screen. No email/password. No sign-up flow.

**UI elements (top to bottom):**
- Full-screen Box with gradient background using `MaterialTheme.colorScheme.primaryContainer` to `background`
- Spacer — 20% of screen height
- `Image` — CanvasX logo vector drawable, 80dp × 80dp, centered
- `Text` — "CanvasX" in `MaterialTheme.typography.displayMedium`, centered
- `Text` — "Create together." in `MaterialTheme.typography.bodyLarge`, `onSurfaceVariant` color, centered
- Spacer — weight 1f (pushes button to bottom half)
- `FilledTonalButton` — full width (padding 24dp horizontal), "Continue with Google"
  - Left icon: Google logo drawable, 20dp
  - While loading: replace content with `CircularProgressIndicator` (24dp), same button dimensions
  - Disabled while `isLoading == true`
- `Spacer` — 16dp
- `Text` — "By continuing you agree to our Terms and Privacy Policy"
  - `bodySmall` typography, `onSurfaceVariant`, centered
  - "Terms" and "Privacy Policy" are `ClickableText` spans (link to web URLs)
- `Spacer` — 32dp + `WindowInsets.navigationBars` bottom padding

**Error handling:**
- On sign-in failure: `LaunchedEffect(error)` shows a `SnackbarHost` snackbar at the bottom
- Error messages: "Sign-in cancelled", "No internet connection. Please try again.", "Sign-in failed. Please try again."

**ViewModel:**
```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val result = authRepository.signInWithGoogle(account.idToken!!)
                result.onSuccess {
                    _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            } catch (e: ApiException) {
                val message = when (e.statusCode) {
                    CommonStatusCodes.NETWORK_ERROR -> "No internet connection. Please try again."
                    CommonStatusCodes.CANCELED -> "Sign-in cancelled."
                    else -> "Sign-in failed. Please try again."
                }
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)
```

---

### Screen 3: Home Screen

**File:** `ui/screens/home/HomeScreen.kt`

**Purpose:** Browse all canvases. Create new canvases. Primary landing screen.

**Layout — Phone Portrait Only:**
```
┌─────────────────────────────┐
│  CanvasX        [🔍] [👤]   │  ← TopAppBar
├─────────────────────────────┤
│ ┌─────────┐ ┌─────────┐    │
│ │ Canvas  │ │ Canvas  │    │  ← LazyVerticalGrid, 2 columns
│ │ thumb   │ │ thumb   │    │     GridCells.Fixed(2), 8dp gap
│ │ Name    │ │ Name    │    │
│ │ 2h ago  │ │ 1d ago  │    │
│ └─────────┘ └─────────┘    │
│ ┌─────────┐ ┌─────────┐    │
│ │         │ │         │    │
│ └─────────┘ └─────────┘    │
│                             │
│                       [+]   │  ← LargeFloatingActionButton
└─────────────────────────────┘
```

**TopAppBar:**
- Title: `Text("CanvasX")` in `headlineMedium`
- Actions:
  - `IconButton` with search icon: expands inline search (see Search State below)
  - `IconButton` with user avatar: navigates to `Settings`

**Search State (expanded):**
When search is active, the `TopAppBar` is replaced with a full-width search bar:
- `TextField` with no border, placeholder "Search canvases…", auto-focused
- Leading icon: magnifier
- Trailing icon: `X` clear button (visible when query is non-empty)
- Navigation icon: back arrow to collapse search and clear query
- Canvas grid filters in real time: `viewModel.filteredCanvases` derived from `query`

**HomeViewModel:**
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCanvasListUseCase: GetCanvasListUseCase,
    private val createCanvasUseCase: CreateCanvasUseCase,
    private val deleteCanvasUseCase: DeleteCanvasUseCase,
    private val updateCanvasUseCase: UpdateCanvasUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCanvasListUseCase().collect { canvases ->
                _uiState.update { it.copy(canvases = canvases, isLoading = false) }
            }
        }
    }

    fun handleIntent(intent: HomeIntent) { /* ... */ }

    // filteredCanvases is derived, not stored
    val filteredCanvases: StateFlow<List<Canvas>> = combine(
        uiState.map { it.canvases },
        uiState.map { it.searchQuery }
    ) { canvases, query ->
        if (query.isBlank()) canvases
        else canvases.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

data class HomeUiState(
    val canvases: List<Canvas> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val error: String? = null
)

sealed class HomeIntent {
    data class SetSearchQuery(val query: String) : HomeIntent()
    data class SetSearchActive(val active: Boolean) : HomeIntent()
    data class DeleteCanvas(val canvasId: String) : HomeIntent()
    data class TogglePin(val canvasId: String, val isPinned: Boolean) : HomeIntent()
    data class RenameCanvas(val canvasId: String, val newName: String) : HomeIntent()
    data class CreateCanvas(val name: String, val widthPx: Int, val heightPx: Int) : HomeIntent()
}
```

**CanvasCard composable:**
```kotlin
// ui/screens/home/components/CanvasCard.kt
@Composable
fun CanvasCard(
    canvas: Canvas,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(canvas.widthPx.toFloat() / canvas.heightPx.toFloat())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail or shimmer
            if (canvas.thumbnailLocalPath != null) {
                AsyncImage(
                    model = canvas.thumbnailLocalPath,
                    contentDescription = canvas.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder shimmer while thumbnail is generating
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            // Pin indicator
            if (canvas.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Bottom scrim with name + timestamp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = canvas.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = canvas.updatedAt.toRelativeTime(), // "2h ago", "Yesterday"
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
```

**Long press context menu (DropdownMenu):**
- Rename → inline rename dialog (`AlertDialog` with `TextField`)
- Duplicate → creates copy with name "[Name] Copy"
- Pin / Unpin → toggles isPinned
- Delete → `AlertDialog` confirmation: "Delete [name]? This cannot be undone." with Delete (error color) + Cancel buttons

**NewCanvasBottomSheet:**
```
Title: "New Canvas"

Size presets (horizontal LazyRow of FilterChips):
  "Square" (1080×1080)  "Portrait" (1080×1350)  "Landscape" (1920×1080)
  "Wallpaper" (1080×2340)  "Poster" (1080×1527)  "Custom"

If "Custom" selected:
  Row: [Width TextField] × [Height TextField]
  Both show keyboard type number, suffix "px"

Name field:
  OutlinedTextField, label "Canvas name", default "Untitled Canvas"

Import toggle:
  "Start with a photo" Switch
  If enabled: shows "Choose Photo" OutlinedButton → photo picker

Bottom:
  Spacer weight 1f
  FilledButton "Create Canvas" — full width — disabled if Custom and width/height empty
```

**Empty State:**
Shown when `canvases.isEmpty()` and `!isLoading`:
```
Centered Column:
  Icon: custom canvas outline illustration, 120dp
  Text: "No canvases yet" (titleMedium)
  Text: "Tap + to create your first" (bodyMedium, onSurfaceVariant)
  FilledTonalButton: "Create Canvas" → opens NewCanvasBottomSheet
```

**Permissions dialog on first editor launch (replaces Onboarding):**
When user first navigates to `EditorScreen`, if camera or storage permissions are not granted, show an `AlertDialog` before the editor opens:
```
Title: "CanvasX needs a couple of permissions"
Body:
  "• Camera — to capture photos directly into layers"
  "• Photos — to import images from your gallery"
Action: "Grant Permissions" → triggers Accompanist permission request
Dismiss: "Not now" → opens editor in limited mode (no camera, no gallery import)
```
This dialog is shown at most once. Its shown-state is stored in DataStore as `permissionsDialogShown: Boolean`.

---

### Screen 4: Editor Screen

**File:** `ui/screens/editor/EditorScreen.kt`

This is the most complex screen in the app. It is full-screen with the system navigation bar handled via `WindowInsets`. The `CanvasRenderer` extends all the way to the status bar using `consumeWindowInsets`.

**Overall structure:**
```kotlin
@Composable
fun EditorScreen(canvasId: String, ...) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopEditorBar(uiState, onIntent = viewModel::handleIntent) },
        bottomBar = { ToolBar(uiState, onIntent = viewModel::handleIntent) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)  // Let content control its own insets
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Main canvas render area
            CanvasRenderer(
                compositedBitmap = uiState.compositedBitmap,
                layers = uiState.layers,
                selectedLayerId = uiState.selectedLayerId,
                collaborators = uiState.collaborators,
                onLayerTransformChange = { layerId, transform ->
                    viewModel.handleIntent(EditorIntent.MoveLayer(layerId, transform.translateX, transform.translateY))
                },
                onLayerSelect = { layerId ->
                    viewModel.handleIntent(EditorIntent.SelectLayer(layerId))
                }
            )

            // Render loading indicator
            if (uiState.isRendering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }

            // AI generation overlay
            if (uiState.aiGenerationState is AiGenerationState.Loading) {
                AiGeneratingOverlay()
            }
        }

        // Bottom sheets — animated in/out based on activeSheet
        when (val sheet = uiState.activeSheet) {
            is EditorSheet.LayerPanel -> LayerPanel(...)
            is EditorSheet.AddLayer -> AddLayerSheet(...)
            is EditorSheet.EffectChain -> EffectChainSheet(layerId = sheet.layerId, ...)
            is EditorSheet.BlendModePicker -> BlendModePickerSheet(layerId = sheet.layerId, ...)
            is EditorSheet.Collaborators -> CollaboratorsSheet(...)
            is EditorSheet.Export -> { navController.navigate(Export(canvasId)) }
            is EditorSheet.None -> {}
        }
    }
}
```

**TopEditorBar:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopEditorBar(uiState: EditorUiState, onIntent: (EditorIntent) -> Unit) {
    var isRenaming by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(uiState.canvasName) }

    TopAppBar(
        title = {
            if (isRenaming) {
                BasicTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        onIntent(EditorIntent.RenameCanvas(nameField))
                        isRenaming = false
                    }),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = uiState.canvasName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isRenaming = true }
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { /* show save & exit dialog */ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Undo button — disabled if canUndo == false
            IconButton(
                onClick = { onIntent(EditorIntent.Undo) },
                enabled = uiState.canUndo
            ) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            // Redo button — disabled if canRedo == false
            IconButton(
                onClick = { onIntent(EditorIntent.Redo) },
                enabled = uiState.canRedo
            ) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            // Collaborators button — shows badge with online count
            BadgedBox(
                badge = {
                    val onlineCount = uiState.collaborators.count { it.isOnline }
                    if (onlineCount > 0) Badge { Text("$onlineCount") }
                }
            ) {
                IconButton(onClick = { onIntent(EditorIntent.ShowSheet(EditorSheet.Collaborators)) }) {
                    Icon(Icons.Default.People, contentDescription = "Collaborators")
                }
            }
            // Export button
            IconButton(onClick = { onIntent(EditorIntent.ShowSheet(EditorSheet.Export)) }) {
                Icon(Icons.Default.Share, contentDescription = "Export")
            }
            // Overflow menu
            OverflowMenu { /* Rename Canvas, Canvas Settings, Clear All, Help */ }
        }
    )
}
```

**ToolBar (bottom):**
```kotlin
@Composable
fun ToolBar(uiState: EditorUiState, onIntent: (EditorIntent) -> Unit) {
    val hasSelectedLayer = uiState.selectedLayerId != null
    val isLayerLocked = uiState.selectedLayer?.isLocked == true

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Layers panel toggle
            ToolbarButton(icon = Icons.Default.Layers, label = "Layers",
                isActive = uiState.activeSheet is EditorSheet.LayerPanel,
                onClick = { onIntent(EditorIntent.ShowSheet(EditorSheet.LayerPanel)) })

            // Effects — only active if a layer is selected and not locked
            ToolbarButton(icon = Icons.Default.Tune, label = "Effects",
                isActive = uiState.activeSheet is EditorSheet.EffectChain,
                enabled = hasSelectedLayer && !isLayerLocked,
                onClick = {
                    uiState.selectedLayerId?.let {
                        onIntent(EditorIntent.ShowSheet(EditorSheet.EffectChain(it)))
                    }
                })

            // Blend mode
            ToolbarButton(icon = Icons.Default.BlurOn, label = "Blend",
                enabled = hasSelectedLayer && !isLayerLocked,
                onClick = {
                    uiState.selectedLayerId?.let {
                        onIntent(EditorIntent.ShowSheet(EditorSheet.BlendModePicker(it)))
                    }
                })

            // Mask
            ToolbarButton(icon = Icons.Default.BrushOutlined, label = "Mask",
                enabled = hasSelectedLayer && !isLayerLocked,
                onClick = { /* navigate to MaskEditorScreen */ })

            // Add layer
            ToolbarButton(icon = Icons.Default.AddPhotoAlternate, label = "Add",
                onClick = { onIntent(EditorIntent.ShowSheet(EditorSheet.AddLayer)) })
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector, label: String,
    isActive: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
```

**CanvasRenderer component:**
```kotlin
// ui/screens/editor/components/CanvasRenderer.kt
@Composable
fun CanvasRenderer(
    compositedBitmap: Bitmap?,
    layers: List<Layer>,
    selectedLayerId: String?,
    collaborators: List<Collaborator>,
    onLayerTransformChange: (String, Float, Float) -> Unit,
    onLayerSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Canvas viewport pan and zoom state
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportScale by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))   // Dark gray surround — Photoshop style
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = false
                ) { _, pan, zoom, _ ->
                    viewportOffset += pan
                    viewportScale = (viewportScale * zoom).coerceIn(0.05f, 20f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Checkerboard pattern (indicates transparency)
        Canvas(modifier = Modifier.matchParentSize()) {
            val tileSize = 16.dp.toPx()
            val cols = (size.width / tileSize).toInt() + 1
            val rows = (size.height / tileSize).toInt() + 1
            val colors = listOf(Color(0xFF808080), Color(0xFF606060))
            for (row in 0..rows) {
                for (col in 0..cols) {
                    drawRect(
                        color = colors[(row + col) % 2],
                        topLeft = Offset(col * tileSize, row * tileSize),
                        size = Size(tileSize, tileSize)
                    )
                }
            }
        }

        // Composited canvas image
        compositedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Canvas",
                modifier = Modifier
                    .graphicsLayer {
                        translationX = viewportOffset.x
                        translationY = viewportOffset.y
                        scaleX = viewportScale
                        scaleY = viewportScale
                    }
                    .shadow(elevation = 16.dp)
            )
        }

        // Selection overlay — dashed border around selected layer bounds
        // (drawn on a separate Canvas layer for performance)

        // Collaborator presence dots
        collaborators.filter { it.isOnline }.forEach { collaborator ->
            // Show small avatar dot at a fixed position tied to their active layer
        }
    }
}
```

**LayerPanel (BottomSheet):**
```kotlin
// ui/screens/editor/components/LayerPanel.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPanel(
    layers: List<Layer>,
    selectedLayerId: String?,
    collaborators: List<Collaborator>,
    onIntent: (EditorIntent) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(EditorIntent.DismissSheet) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layers", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onIntent(EditorIntent.ShowSheet(EditorSheet.AddLayer)) }) {
                Icon(Icons.Default.Add, contentDescription = "Add layer")
            }
        }

        // Layers listed top-to-bottom (reversed from z-order so top layer shows first)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(
                items = layers.reversed(),
                key = { it.id }
            ) { layer ->
                LayerItem(
                    layer = layer,
                    isSelected = layer.id == selectedLayerId,
                    collaboratorEditing = collaborators.find { it.activeLayerId == layer.id },
                    onSelect = { onIntent(EditorIntent.SelectLayer(layer.id)) },
                    onToggleVisibility = {
                        onIntent(EditorIntent.SetLayerVisibility(layer.id, !layer.isVisible))
                    },
                    onToggleLock = {
                        onIntent(EditorIntent.SetLayerLock(layer.id, !layer.isLocked))
                    },
                    onDelete = { onIntent(EditorIntent.DeleteLayer(layer.id)) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
```

**LayerItem composable:**
```kotlin
@Composable
fun LayerItem(
    layer: Layer,
    isSelected: Boolean,
    collaboratorEditing: Collaborator?,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Red delete background shown during swipe
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collaborator color border on thumbnail
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = if (collaboratorEditing != null) 2.dp else 0.dp,
                        color = Color(collaboratorEditing?.presenceColor ?: 0),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                // Layer thumbnail (36×36dp)
                if (layer.type == LayerType.SOLID_COLOR) {
                    Box(Modifier.fillMaxSize().background(Color(layer.solidColor ?: Color.Gray.toArgb())))
                } else {
                    AsyncImage(
                        model = layer.sourceBitmapPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Layer name + type badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = layer.type.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Visibility toggle
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Lock toggle
            IconButton(onClick = onToggleLock, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (layer.isLocked) "Unlock layer" else "Lock layer",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Trigger delete after swipe
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }
}
```

**AddLayerSheet:**
```kotlin
@Composable
fun AddLayerSheet(onIntent: (EditorIntent) -> Unit, onDismiss: () -> Unit) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onIntent(EditorIntent.AddImageLayer(it)) } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Add Layer", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))

        ListItem(
            headlineContent = { Text("From Gallery") },
            leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
            modifier = Modifier.clickable {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                onDismiss()
            }
        )
        ListItem(
            headlineContent = { Text("Take Photo") },
            leadingContent = { Icon(Icons.Default.CameraAlt, null) },
            modifier = Modifier.clickable { /* navigate to camera screen */ }
        )
        ListItem(
            headlineContent = { Text("AI Generate") },
            supportingContent = { Text("Describe an image with text") },
            leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
            modifier = Modifier.clickable { /* show AiGenerationDialog */ }
        )
        ListItem(
            headlineContent = { Text("Solid Color") },
            leadingContent = { Icon(Icons.Default.Rectangle, null) },
            modifier = Modifier.clickable { /* show color picker */ }
        )
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}
```

**EffectChainSheet:**
```kotlin
@Composable
fun EffectChainSheet(layer: Layer, onIntent: (EditorIntent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Effects — ${layer.name}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { /* show effect picker */ }) { Text("Add Effect") }
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            items(layer.effectChain, key = { it.id }) { effect ->
                EffectItem(
                    effect = effect,
                    onUpdate = { updated -> onIntent(EditorIntent.UpdateEffect(layer.id, updated)) },
                    onRemove = { onIntent(EditorIntent.RemoveEffect(layer.id, effect.id)) },
                    onToggle = { onIntent(EditorIntent.ToggleEffectEnabled(layer.id, effect.id)) }
                )
                HorizontalDivider()
            }
        }

        if (layer.effectChain.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No effects added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}
```

**EffectItem (with expand/collapse):**

Each effect renders as a `ListItem` with:
- Left: effect name
- Right: enabled `Switch` + expand `IconButton`
- When expanded: effect-specific parameter sliders in a `Column` below

Example expanded BrightnessContrast:
```kotlin
if (isExpanded && effect is Effect.BrightnessContrast) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        LabeledSlider(
            label = "Brightness",
            value = effect.brightness,
            valueRange = -1f..1f,
            onValueChange = { v ->
                onUpdate(effect.copy(brightness = v))
            }
        )
        LabeledSlider(
            label = "Contrast",
            value = effect.contrast,
            valueRange = -1f..1f,
            onValueChange = { v ->
                onUpdate(effect.copy(contrast = v))
            }
        )
    }
}
```

Note: Sliders use `snapshotFlow` + `debounce(50ms)` + `distinctUntilChanged()` so rapid dragging doesn't fire 60 ViewModel updates per second. See Section 10 (Multithreading) for implementation.

**BlendModePickerSheet:**
```kotlin
@Composable
fun BlendModePickerSheet(
    currentMode: BlendMode,
    onSelect: (BlendMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Blend Mode", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))

        // 5 blend modes as a simple chip row — not a 4-column grid since we only have 5
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BlendMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == currentMode,
                    onClick = { onSelect(mode); onDismiss() },
                    label = { Text(mode.displayName()) }
                )
            }
        }

        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
```

**AiGenerationDialog:**
```kotlin
@Composable
fun AiGenerationDialog(
    aiState: AiGenerationState,
    onGenerate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate AI Layer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe the image…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (aiState is AiGenerationState.Error) {
                    Text(
                        text = aiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            FilledButton(
                onClick = { if (prompt.isNotBlank()) onGenerate(prompt) },
                enabled = prompt.isNotBlank() && aiState !is AiGenerationState.Loading
            ) {
                if (aiState is AiGenerationState.Loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating…")
                } else {
                    Text("Generate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

**CollaboratorsSheet:**
```kotlin
@Composable
fun CollaboratorsSheet(
    collaborators: List<Collaborator>,
    canvasId: String,
    isOwner: Boolean,
    isViewOnly: Boolean,
    onKick: (String) -> Unit,
    onToggleViewOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Collaborators", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))

        // Invite button — copies link to clipboard
        OutlinedButton(
            onClick = { /* copy "canvasx://canvas/$canvasId" to clipboard */ },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.Link, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy Invite Link")
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(collaborators) { collaborator ->
                ListItem(
                    headlineContent = { Text(collaborator.displayName) },
                    leadingContent = {
                        Box {
                            AsyncImage(model = collaborator.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape))
                            // Online indicator dot
                            if (collaborator.isOnline) {
                                Box(Modifier.size(10.dp).align(Alignment.BottomEnd)
                                    .background(Color(0xFF4CAF50), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape))
                            }
                        }
                    },
                    trailingContent = {
                        if (isOwner && collaborator.userId != FirebaseAuth.getInstance().currentUser?.uid) {
                            TextButton(onClick = { onKick(collaborator.userId) }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
        }

        if (isOwner) {
            ListItem(
                headlineContent = { Text("View-only mode") },
                supportingContent = { Text("Guests can view but not edit") },
                trailingContent = {
                    Switch(checked = isViewOnly, onCheckedChange = onToggleViewOnly)
                }
            )
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}
```

---

### Screen 5: Mask Editor Screen

**File:** `ui/screens/mask/MaskEditorScreen.kt`

**Purpose:** Full-screen mask painting tool. Separate screen (not a bottom sheet) because mask painting requires maximum canvas real estate and its own isolated state.

**Layout:**
```
┌─────────────────────────────┐
│ [←]  Edit Mask — Layer 2    │  ← TopAppBar
│                      [Done] │
├─────────────────────────────┤
│                             │
│   Layer image at full width │
│   with red overlay showing  │
│   masked (hidden) areas     │
│   (Touch to paint mask)     │
│                             │
├─────────────────────────────┤
│ [Brush] [Eraser]            │  ← SegmentedButton
│ Size: ─●────────  32px      │  ← Slider
│ Hardness: ────●──  70%      │  ← Slider
│ [Invert] [Clear] [Remove BG]│  ← Action buttons
└─────────────────────────────┘
```

**MaskEditorViewModel:**
```kotlin
@HiltViewModel
class MaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val layerRepository: LayerRepository,
    private val removeBackgroundUseCase: RemoveBackgroundUseCase
) : ViewModel() {

    private val layerId: String = savedStateHandle["layerId"]!!
    private val canvasId: String = savedStateHandle["canvasId"]!!

    // The mask is an in-memory Bitmap with ALPHA_8 config.
    // White pixels (255) = layer is visible. Black pixels (0) = layer is masked/hidden.
    private val _maskBitmap = MutableStateFlow<Bitmap?>(null)
    val maskBitmap = _maskBitmap.asStateFlow()

    private val _isBrushMode = MutableStateFlow(true)  // true = brush, false = eraser
    val isBrushMode = _isBrushMode.asStateFlow()

    private val _brushSize = MutableStateFlow(32f)
    val brushSize = _brushSize.asStateFlow()

    private val _brushHardness = MutableStateFlow(0.7f)
    val brushHardness = _brushHardness.asStateFlow()

    private val _isRemovingBg = MutableStateFlow(false)
    val isRemovingBg = _isRemovingBg.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var maskCanvas: Canvas? = null

    init { loadExistingMask() }

    fun initMaskBitmap(width: Int, height: Int) {
        if (_maskBitmap.value != null) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Start fully white = no masking
        Canvas(bitmap).drawColor(Color.WHITE)
        maskCanvas = Canvas(bitmap)
        _maskBitmap.value = bitmap
    }

    fun paintStroke(x: Float, y: Float, pressure: Float = 1f) {
        val bitmap = _maskBitmap.value ?: return
        val paint = Paint().apply {
            isAntiAlias = true
            color = if (_isBrushMode.value) Color.BLACK else Color.WHITE
            style = Paint.Style.FILL
            val radius = _brushSize.value * pressure
            // Soft brush: radial gradient shader for feathering
            if (_brushHardness.value < 1f) {
                shader = RadialGradient(
                    x, y, radius,
                    intArrayOf(
                        if (_isBrushMode.value) Color.BLACK else Color.WHITE,
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
        maskCanvas?.drawCircle(x, y, _brushSize.value, paint)
        // Trigger UI update by emitting same reference (bitmap is mutable)
        _maskBitmap.value = bitmap
    }

    fun invertMask() {
        val bitmap = _maskBitmap.value ?: return
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                bitmap.setPixel(x, y, pixel xor 0x00FFFFFF)  // Invert RGB, keep alpha
            }
        }
        _maskBitmap.value = bitmap
    }

    fun clearMask() {
        val bitmap = _maskBitmap.value ?: return
        Canvas(bitmap).drawColor(Color.WHITE)
        _maskBitmap.value = bitmap
    }

    fun applyRemoveBackground(sourceImagePath: String) {
        viewModelScope.launch {
            _isRemovingBg.value = true
            try {
                val resultBitmap = removeBackgroundUseCase(sourceImagePath)
                // Extract alpha channel from Remove.bg result and use as mask
                val alphaMask = Bitmap.createBitmap(resultBitmap.width, resultBitmap.height, Bitmap.Config.ARGB_8888)
                for (x in 0 until resultBitmap.width) {
                    for (y in 0 until resultBitmap.height) {
                        val alpha = Color.alpha(resultBitmap.getPixel(x, y))
                        alphaMask.setPixel(x, y, Color.argb(255, alpha, alpha, alpha))
                    }
                }
                _maskBitmap.value = alphaMask
            } catch (e: Exception) {
                _error.value = "Background removal failed: ${e.message}"
            } finally {
                _isRemovingBg.value = false
            }
        }
    }

    // Called when user taps "Done"
    fun saveMask() {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = _maskBitmap.value ?: return@launch
            val path = layerRepository.saveMaskToFile(canvasId, layerId, bitmap)
            layerRepository.updateLayerMaskPath(layerId, path)
        }
    }
}
```

**MaskEditorScreen Compose UI:**
```kotlin
@Composable
fun MaskEditorScreen(
    canvasId: String,
    layerId: String,
    onDone: () -> Unit,
    viewModel: MaskEditorViewModel = hiltViewModel()
) {
    val maskBitmap by viewModel.maskBitmap.collectAsStateWithLifecycle()
    val isBrushMode by viewModel.isBrushMode.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val brushHardness by viewModel.brushHardness.collectAsStateWithLifecycle()
    val isRemovingBg by viewModel.isRemovingBg.collectAsStateWithLifecycle()

    // Load layer source bitmap for display
    val layer by remember { /* load from repository */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Mask") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveMask()
                        onDone()
                    }) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Canvas area — takes all remaining space above toolbar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C))
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            viewModel.paintStroke(change.position.x, change.position.y)
                        }
                    }
            ) {
                // Layer source image
                layer?.sourceBitmapPath?.let { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Red overlay for masked areas
                maskBitmap?.let { mask ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.tint(
                                Color.Red.copy(alpha = 0.4f),
                                BlendMode.SrcIn
                            )
                        }
                        // Invert mask for display: black mask areas show red
                        drawImage(mask.asImageBitmap(), blendMode = BlendMode.Multiply, paint = paint)
                    }
                }
            }

            // Bottom toolbar
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    // Brush / Eraser toggle
                    SegmentedButton(
                        segments = listOf("Brush", "Eraser"),
                        selectedIndex = if (isBrushMode) 0 else 1,
                        onSelect = { viewModel.setMode(it == 0) }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Brush size slider
                    LabeledSlider("Size", brushSize, 8f..80f) { viewModel.setBrushSize(it) }

                    // Brush hardness slider
                    LabeledSlider("Hardness", brushHardness, 0f..1f) { viewModel.setBrushHardness(it) }

                    Spacer(Modifier.height(8.dp))

                    // Action buttons row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.invertMask() }, Modifier.weight(1f)) {
                            Text("Invert")
                        }
                        OutlinedButton(onClick = { viewModel.clearMask() }, Modifier.weight(1f)) {
                            Text("Clear")
                        }
                        FilledTonalButton(
                            onClick = { layer?.sourceBitmapPath?.let { viewModel.applyRemoveBackground(it) } },
                            enabled = !isRemovingBg,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            if (isRemovingBg) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (isRemovingBg) "Removing…" else "Remove BG")
                        }
                    }
                }
            }
        }
    }
}
```

---

### Screen 6: Export Screen

**File:** `ui/screens/export/ExportScreen.kt`

**Purpose:** Export the final composited canvas as PNG or JPEG to the device gallery.

**Layout:**
```
TopAppBar: [←] Export

Canvas preview (small, centered, drop shadow, rounded corners)
Canvas dimensions label: "1080 × 1080 px"

──── Format ────────────────────────
SegmentedButton: [PNG]  [JPEG]

(JPEG only, visible when JPEG selected:)
Quality: ─────●──  85%

──── Resolution ────────────────────
RadioGroup:
  ● 1× — Original (1080 × 1080 px)
  ○ 2× — (2160 × 2160 px)
  ○ Custom
  
(Custom only:)
  Row: [Width px] × [Height px]

──── Export ─────────────────────────
FilledButton "Save to Gallery" — full width

(Visible while exporting:)
LinearProgressIndicator
"Saving…  45%"
OutlinedButton "Cancel"

(After success:)
Icon ✅ + Text "Saved to Gallery"
FilledTonalButton "Share" → opens Android share sheet
```

**ExportViewModel:**
```kotlin
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exportCanvasUseCase: ExportCanvasUseCase,
    private val workManager: WorkManager
) : ViewModel() {

    private val canvasId: String = savedStateHandle["canvasId"]!!

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    fun startExport() {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(workDataOf(
                "canvasId" to canvasId,
                "format" to _uiState.value.format.name,
                "quality" to _uiState.value.jpegQuality,
                "scale" to _uiState.value.scale
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueue(request)

        // Observe progress
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt("progress", 0)
                        _uiState.update { it.copy(exportState = ExportState.InProgress(progress)) }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val uri = info.outputData.getString("outputUri")
                        _uiState.update { it.copy(exportState = ExportState.Success(uri)) }
                    }
                    WorkInfo.State.FAILED -> {
                        _uiState.update { it.copy(exportState = ExportState.Failed("Export failed")) }
                    }
                    else -> {}
                }
            }
        }
    }
}

data class ExportUiState(
    val format: ExportFormat = ExportFormat.PNG,
    val jpegQuality: Int = 90,         // 50 to 100
    val scale: Float = 1f,             // 1x, 2x, or custom
    val customWidthPx: Int = 1080,
    val customHeightPx: Int = 1080,
    val exportState: ExportState = ExportState.Idle
)

sealed class ExportState {
    data object Idle : ExportState()
    data class InProgress(val percent: Int) : ExportState()
    data class Success(val savedUri: String?) : ExportState()
    data class Failed(val message: String) : ExportState()
}

enum class ExportFormat { PNG, JPEG }
```

---

### Screen 7: Settings Screen

**File:** `ui/screens/settings/SettingsScreen.kt`

**Layout:**
```
TopAppBar: [←] Settings

──── Account ──────────────────────
ListItem: [Avatar] Display Name
          email@gmail.com

OutlinedButton "Sign Out" (shows confirmation AlertDialog)

──── Appearance ───────────────────
ListItem "Theme"
  SegmentedButton: [Light] [Dark] [System]

SwitchListTile "Dynamic Color"
  "Use your wallpaper colors (Android 12+)"
  [Switch]

──── Canvas Defaults ──────────────
ListItem "Default canvas size"
  [Square ▾] dropdown / bottom sheet picker

──── Notifications ────────────────
SwitchListTile "Collaborator joined canvas"
SwitchListTile "Export completed"
SwitchListTile "AI generation completed"

──── Storage ──────────────────────
ListItem "Cache size: 245 MB"
  trailing: OutlinedButton "Clear Cache"

──── About ────────────────────────
ListItem "Version 1.0.0"
ListItem "Privacy Policy" → open URL
ListItem "Terms of Service" → open URL
```

**Note on Dynamic Color:** Only enable the switch on Android 12+. Use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` guard. If below S, show the switch as disabled with a subtitle "Available on Android 12+".

---

## 8. Navigation Graph

### Type-Safe Routes

```kotlin
// core/navigation/Routes.kt
// Uses Navigation 2.8's type-safe API with @Serializable data classes.
// This replaces string routes and is the current Google guideline.

@Serializable
object Auth                                    // No arguments

@Serializable
object Home                                    // No arguments

@Serializable
data class Editor(
    val canvasId: String                       // UUID of canvas to open
)

@Serializable
data class MaskEditor(
    val canvasId: String,
    val layerId: String                        // Which layer's mask to edit
)

@Serializable
data class Export(
    val canvasId: String
)

@Serializable
object Settings
```

### NavGraph Implementation

```kotlin
// core/navigation/AppNavGraph.kt
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Auth              // Splash handles initial routing inline
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) }
    ) {

        // ── Auth ─────────────────────────────────────────────────────────────
        composable<Auth> {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate(Home) {
                        popUpTo(Auth) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Home ─────────────────────────────────────────────────────────────
        composable<Home> {
            HomeScreen(
                onOpenCanvas = { canvasId ->
                    navController.navigate(Editor(canvasId))
                },
                onOpenSettings = {
                    navController.navigate(Settings)
                },
                onSignedOut = {
                    navController.navigate(Auth) {
                        popUpTo(Home) { inclusive = true }
                    }
                }
            )
        }

        // ── Editor ───────────────────────────────────────────────────────────
        composable<Editor>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetX = { it }
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    targetOffsetX = { it }
                )
            },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { it }
                )
            }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Editor>()
            EditorScreen(
                canvasId = route.canvasId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMask = { layerId ->
                    navController.navigate(MaskEditor(route.canvasId, layerId))
                },
                onNavigateToExport = {
                    navController.navigate(Export(route.canvasId))
                }
            )
        }

        // ── MaskEditor ────────────────────────────────────────────────────────
        composable<MaskEditor>(
            enterTransition = {
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialOffsetY = { it }
                )
            },
            exitTransition = {
                slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it }
                )
            }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<MaskEditor>()
            MaskEditorScreen(
                canvasId = route.canvasId,
                layerId = route.layerId,
                onDone = { navController.popBackStack() }
            )
        }

        // ── Export ───────────────────────────────────────────────────────────
        composable<Export> { backStackEntry ->
            val route = backStackEntry.toRoute<Export>()
            ExportScreen(
                canvasId = route.canvasId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
```

### Predictive Back (Google New Guideline)

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Edge-to-edge enforcement — Google new guidelines
        enableEdgeToEdge()

        // Predictive Back API — Android 14+
        // Each screen/ViewModel registers its own OnBackPressedCallback when needed
        // (e.g. editor shows "save & exit" dialog on back)

        setContent {
            val navController = rememberNavController()

            // Collect initial route from SplashViewModel
            val splashViewModel: SplashViewModel = hiltViewModel()
            val destination by splashViewModel.destination.collectAsStateWithLifecycle()

            CanvasXTheme {
                AppNavGraph(
                    navController = navController,
                    startDestination = destination ?: Auth
                )
            }
        }
    }
}
```

**Custom Predictive Back in Editor:**
```kotlin
// In EditorScreen — intercept back to show "Save & Exit" dialog
val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

DisposableEffect(Unit) {
    val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Show save & exit confirmation dialog
            viewModel.handleIntent(EditorIntent.ShowSheet(EditorSheet.ConfirmExit))
        }
        // Predictive back: animate the canvas sliding away
        // as user swipes, before they release
        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            // Scale + translate canvas based on backEvent.progress
        }
    }
    onBackPressedDispatcher?.addCallback(callback)
    onDispose { callback.remove() }
}
```

---

## 9. API Integrations

### 9.1 DALL-E (OpenAI Images API)

**Endpoint:** `POST https://api.openai.com/v1/images/generations`

**Retrofit interface:**
```kotlin
// data/remote/api/DalleApiService.kt
interface DalleApiService {
    @POST("v1/images/generations")
    suspend fun generateImage(
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.OPENAI_API_KEY}",
        @Body request: DalleRequest
    ): Response<DalleResponse>
}

@Serializable
data class DalleRequest(
    val model: String = "dall-e-3",
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",       // dall-e-3 supports: 1024x1024, 1792x1024, 1024x1792
    val quality: String = "standard",     // "standard" or "hd"
    val response_format: String = "url"   // "url" or "b64_json"
)

@Serializable
data class DalleResponse(
    val created: Long,
    val data: List<DalleImageData>
)

@Serializable
data class DalleImageData(
    val url: String,
    val revised_prompt: String?           // DALL-E 3 may revise your prompt
)
```

**GenerateAiLayerUseCase:**
```kotlin
// domain/usecase/ai/GenerateAiLayerUseCase.kt
class GenerateAiLayerUseCase @Inject constructor(
    private val dalleApiService: DalleApiService,
    private val layerRepository: LayerRepository
) {
    suspend operator fun invoke(canvasId: String, prompt: String): Result<Layer> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Call DALL-E API
                val response = dalleApiService.generateImage(
                    request = DalleRequest(prompt = prompt)
                )
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code()) {
                        429 -> "Too many requests. Please wait a moment."
                        400 -> "Your prompt was rejected by the content policy."
                        401 -> "API key is invalid."
                        else -> "Generation failed (${response.code()})"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val imageUrl = response.body()?.data?.firstOrNull()?.url
                    ?: return@withContext Result.failure(Exception("No image returned"))

                // 2. Download image to local file
                val localPath = layerRepository.downloadImageToFile(imageUrl, canvasId)

                // 3. Create Layer domain object
                val layer = Layer(
                    id = UUID.randomUUID().toString(),
                    canvasId = canvasId,
                    ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    name = "AI Layer",
                    type = LayerType.AI_GENERATED,
                    sourceBitmapPath = localPath,
                    transform = LayerTransform(),
                    effectChain = emptyList(),
                    blendMode = BlendMode.NORMAL,
                    opacity = 1f,
                    isVisible = true,
                    isLocked = false,
                    maskPath = null,
                    zIndex = 0,  // Will be set by repository to be top of stack
                    updatedAt = System.currentTimeMillis()
                )

                // 4. Persist to Room + Firestore
                layerRepository.addLayer(layer)

                Result.success(layer)

            } catch (e: CancellationException) {
                throw e  // Always rethrow
            } catch (e: IOException) {
                Result.failure(Exception("No internet connection. Please try again."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
```

### 9.2 Remove.bg API

**Endpoint:** `POST https://api.remove.bg/v1.0/removebg`

```kotlin
// data/remote/api/RemoveBgApiService.kt
interface RemoveBgApiService {
    @Multipart
    @POST("v1.0/removebg")
    suspend fun removeBackground(
        @Header("X-Api-Key") apiKey: String = BuildConfig.REMOVE_BG_API_KEY,
        @Part imageFile: MultipartBody.Part,
        @Part("size") size: RequestBody = "auto".toRequestBody("text/plain".toMediaType()),
        @Part("type") type: RequestBody = "auto".toRequestBody("text/plain".toMediaType())
    ): Response<ResponseBody>  // Returns raw PNG bytes with transparent background
}
```

**RemoveBackgroundUseCase:**
```kotlin
// domain/usecase/ai/RemoveBackgroundUseCase.kt
class RemoveBackgroundUseCase @Inject constructor(
    private val removeBgApiService: RemoveBgApiService
) {
    // Returns a Bitmap with background pixels set to transparent
    suspend operator fun invoke(sourceImagePath: String): Bitmap =
        withContext(Dispatchers.IO) {
            // 1. Load source bitmap and compress to JPEG for upload
            val sourceBitmap = BitmapUtils.loadBitmapFromPath(sourceImagePath)
            val imageBytes = ByteArrayOutputStream().also { stream ->
                // Downscale if > 2MP to stay within Remove.bg free tier limits
                val scaled = BitmapUtils.scaleIfNeeded(sourceBitmap, maxDimension = 1500)
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }.toByteArray()

            // 2. Create multipart body
            val imagePart = MultipartBody.Part.createFormData(
                name = "image_file",
                filename = "layer.jpg",
                body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            )

            // 3. Call API
            val response = removeBgApiService.removeBackground(imageFile = imagePart)
            if (!response.isSuccessful) {
                val errorMsg = when (response.code()) {
                    402 -> "Remove.bg credit limit reached."
                    403 -> "Remove.bg API key is invalid."
                    else -> "Background removal failed (${response.code()})"
                }
                throw Exception(errorMsg)
            }

            // 4. Decode response PNG (has transparent background) to Bitmap
            val responseBytes = response.body()?.bytes()
                ?: throw Exception("Empty response from Remove.bg")

            BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                ?: throw Exception("Failed to decode Remove.bg response image")
        }
}
```

### 9.3 Firebase Auth — Google Sign-In

```kotlin
// data/repository/AuthRepository.kt
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val googleSignInClient: GoogleSignInClient
) {
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            Result.success(authResult.user!!)
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception("Sign-in failed: ${e.errorCode}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    fun isSignedIn(): Boolean = auth.currentUser != null
}

// core/di/FirebaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance().also { db ->
            // Enable offline persistence
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            db.firestoreSettings = settings
        }
    }

    @Provides @Singleton
    fun provideGoogleSignInClient(@ApplicationContext context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
}
```

### 9.4 Firebase Firestore — Real-Time Layer Sync

```kotlin
// data/remote/firebase/FirestoreLayerDataSource.kt
class FirestoreLayerDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val json: Json
) {
    private fun layersCollection(canvasId: String) =
        firestore.collection("canvases").document(canvasId).collection("layers")

    private fun presenceCollection(canvasId: String) =
        firestore.collection("canvases").document(canvasId).collection("presence")

    // Returns a Flow that emits every time the layers subcollection changes on Firestore.
    // Uses callbackFlow to bridge Firestore's listener-based API to coroutines.
    fun observeLayers(canvasId: String): Flow<List<Layer>> = callbackFlow {
        val subscription = layersCollection(canvasId)
            .orderBy("zIndex", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val layers = snapshot.documents.mapNotNull { doc ->
                        doc.toLayerDomain(json)
                    }
                    trySend(layers)
                }
            }
        // Clean up listener when Flow is cancelled
        awaitClose { subscription.remove() }
    }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()   // Don't re-emit if nothing actually changed

    // Write a layer's metadata to Firestore. Uses SetOptions.merge() so
    // each field write is independent — critical for last-write-wins conflict strategy.
    suspend fun upsertLayer(canvasId: String, layer: Layer) {
        layersCollection(canvasId)
            .document(layer.id)
            .set(layer.toFirestoreMap(json), SetOptions.merge())
            .await()
    }

    suspend fun deleteLayer(canvasId: String, layerId: String) {
        layersCollection(canvasId).document(layerId).delete().await()
    }

    // Presence: write current user's active layer and heartbeat timestamp.
    // Uses server timestamp so all clients agree on timing.
    suspend fun updatePresence(canvasId: String, userId: String, activeLayerId: String?) {
        presenceCollection(canvasId)
            .document(userId)
            .set(mapOf(
                "userId" to userId,
                "activeLayerId" to activeLayerId,
                "lastSeen" to FieldValue.serverTimestamp()
            ), SetOptions.merge())
            .await()
    }

    // Remove presence on canvas close
    suspend fun removePresence(canvasId: String, userId: String) {
        presenceCollection(canvasId).document(userId).delete().await()
    }

    // Observe presence for all users in a canvas
    fun observePresence(canvasId: String): Flow<List<Collaborator>> = callbackFlow {
        val subscription = presenceCollection(canvasId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    val collaborators = snapshot.documents.mapNotNull { doc ->
                        val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                        val isOnline = (now - lastSeen) < 30_000L  // Online = seen < 30s ago
                        Collaborator(
                            userId = doc.getString("userId") ?: return@mapNotNull null,
                            displayName = doc.getString("displayName") ?: "Unknown",
                            avatarUrl = doc.getString("avatarUrl"),
                            isOnline = isOnline,
                            activeLayerId = doc.getString("activeLayerId"),
                            presenceColor = doc.getString("userId")
                                .hashCode()
                                .let { hash -> presenceColors[abs(hash) % presenceColors.size] }
                        )
                    }
                    trySend(collaborators)
                }
            }
        awaitClose { subscription.remove() }
    }

    companion object {
        // Pre-defined colors for collaborator presence indicators
        private val presenceColors = listOf(
            0xFF2196F3.toInt(),   // Blue
            0xFF4CAF50.toInt(),   // Green
            0xFFFF9800.toInt(),   // Orange
            0xFFE91E63.toInt(),   // Pink
            0xFF9C27B0.toInt()    // Purple
        )
    }
}
```

---

## 10. Multithreading Model

### Dispatcher Assignment Table

| Operation | Dispatcher | Reason |
|---|---|---|
| AGSL shader execution | `Dispatchers.Default` | CPU-intensive, must not run on Main thread |
| Per-layer effect processing | `Dispatchers.Default` | CPU-bound, parallelized across layers |
| Bitmap compositing | `Dispatchers.Default` | CPU-bound, must be sequential |
| Loading bitmaps from disk | `Dispatchers.IO` | Blocking file I/O |
| Saving mask to file | `Dispatchers.IO` | Blocking file write |
| Firestore reads and writes | `Dispatchers.IO` | Network I/O (though Firebase uses its own executor) |
| Retrofit API calls | `Dispatchers.IO` | Network I/O |
| Room DAO queries | `Dispatchers.IO` | Room enforces this — will crash on Main |
| UI state updates | `Dispatchers.Main` | StateFlow emissions consumed by Compose |
| WorkManager jobs | Own managed thread pool | WorkManager manages its own threads |

### Render Queue — Channel(CONFLATED) Pattern

This is the most critical threading decision in the entire app. Slider effects update at 60fps during dragging. Without the CONFLATED channel, a user dragging a blur radius slider would create a queue of 60 pending renders per second that would never clear.

```kotlin
// The Channel(CONFLATED) pattern:
// - Producer: calls trySend() with new layer state on every UI change
// - Consumer: the for-loop in startRenderLoop() processes one at a time
// - Key behavior: if a new item arrives while the consumer is processing,
//   the buffered (unprocessed) item is REPLACED by the new one.
//   The consumer always gets the most recent state.

private val renderChannel = Channel<RenderRequest>(Channel.CONFLATED)

// Producer side — called from ViewModel on every state change
fun requestRender(layers: List<Layer>, w: Int, h: Int) {
    renderChannel.trySend(RenderRequest(layers, w, h))
    // trySend never suspends. If buffer is full, it replaces the buffered item.
}

// Consumer side — runs forever in viewModelScope
suspend fun startRenderLoop(canvasWidth: Int, canvasHeight: Int) {
    for (request in renderChannel) {         // suspends until next item available
        isRendering.value = true
        val result = renderFrame(request)    // may take 50-200ms
        compositedBitmap.value = result
        isRendering.value = false
        // If 10 render requests arrived during renderFrame(), only the last one
        // is in the channel. The for-loop immediately processes it.
        // The other 9 were silently discarded.
    }
}
```

### Parallel Layer Processing with async/awaitAll

```kotlin
// Dirty layers are processed in parallel.
// Each layer's effect chain is independent, so there are no data dependencies.
// This cuts render time roughly in proportion to the number of dirty layers.

val parallelJobs = dirtyLayers.map { layer ->
    async(Dispatchers.Default) {
        val processed = effectProcessor.apply(loadBitmap(layer), layer.effectChain)
        layer.id to processed
    }
}
// Suspends until ALL parallel jobs complete, then continues to compositing
val results: Map<String, Bitmap> = parallelJobs.awaitAll().toMap()

// Compositing is then sequential — CANNOT be parallelized
// because each step reads the output of the previous step
visibleLayers.forEach { layer ->
    composite = blendModeProcessor.composite(composite, renderCache.get(layer.id)!!, ...)
}
```

### Effect Slider Debounce Pattern

Effect parameter sliders update state at the display refresh rate during drag (up to 120fps on modern phones). This pattern ensures only meaningful changes reach the render engine:

```kotlin
// In EffectItem composable — slider for blur radius
var sliderValue by remember { mutableStateOf(effect.radius) }

// Slider updates local state immediately (instant visual feedback on slider track)
Slider(
    value = sliderValue,
    onValueChange = { sliderValue = it },
    valueRange = 0f..50f
)

// snapshotFlow observes Compose state outside Compose's recomposition cycle
// debounce(50) waits 50ms of no change before emitting
// distinctUntilChanged prevents redundant render triggers
LaunchedEffect(effect.id) {
    snapshotFlow { sliderValue }
        .distinctUntilChanged()
        .debounce(50L)
        .collect { value ->
            onUpdate(effect.copy(radius = value))
            // This triggers EditorIntent.UpdateEffect → ViewModel → render request
        }
}
```

### Firestore Sync — callbackFlow Bridge

```kotlin
// callbackFlow bridges Firestore's listener-based API to Kotlin coroutines.
// This is the canonical pattern for converting callback APIs to Flow.

fun observeLayers(canvasId: String): Flow<List<Layer>> = callbackFlow {
    val listenerRegistration = firestore
        .collection("canvases/$canvasId/layers")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)           // Terminates the Flow with an error
                return@addSnapshotListener
            }
            snapshot?.let { trySend(it.toLayerList()) }
        }

    // awaitClose is called when the Flow collector cancels (e.g. ViewModel is cleared).
    // It's essential to remove the Firestore listener here to prevent memory leaks.
    awaitClose {
        listenerRegistration.remove()
    }
}
    .catch { error ->
        // Handle Firestore errors gracefully — show offline banner instead of crashing
        emit(emptyList())
    }
    .flowOn(Dispatchers.IO)
```

### Background Export — WorkManager + Progress

```kotlin
// Reporting progress from inside WorkManager:
// setProgress() updates the WorkInfo which is observed by the ViewModel.
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exportRepository: ExportRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val canvasId = inputData.getString("canvasId") ?: return Result.failure()
        val format = inputData.getString("format") ?: "PNG"
        val quality = inputData.getInt("quality", 90)
        val scale = inputData.getFloat("scale", 1f)

        // Show persistent notification during export
        setForeground(createForegroundInfo(0))

        return try {
            // Step 1: Render final composite (may take 5-30 seconds for complex canvas)
            setProgress(workDataOf("progress" to 10))
            val bitmap = exportRepository.renderFinalComposite(canvasId)

            // Step 2: Scale if needed
            setProgress(workDataOf("progress" to 40))
            val scaledBitmap = if (scale != 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap

            // Step 3: Save to gallery
            setProgress(workDataOf("progress" to 70))
            val uri = exportRepository.saveBitmapToGallery(
                bitmap = scaledBitmap,
                format = format,
                quality = quality
            )

            setProgress(workDataOf("progress" to 100))
            val outputData = workDataOf("outputUri" to uri.toString())
            Result.success(outputData)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, EXPORT_CHANNEL_ID)
            .setContentTitle("CanvasX")
            .setContentText("Exporting canvas…")
            .setSmallIcon(R.drawable.ic_export)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
        return ForegroundInfo(EXPORT_NOTIFICATION_ID, notification)
    }
}
```

---

## 11. Real-Time Collaboration Design

### Sync Philosophy

**What syncs to Firestore (metadata only — never pixel data):**
- Layer z-order (zIndex)
- Layer transform (translate, scale, rotate as JSON)
- Layer opacity
- Layer blend mode
- Layer effect chain parameters (as JSON)
- Layer visibility and lock state
- Presence: which user is editing which layer

**What stays local only:**
- Bitmap pixel data (loaded from local file, not synced)
- Mask bitmaps (local file)
- Render cache contents
- Undo/redo history (local only for MVP)

**How collaborators see each other's images:**
When a user adds a new image layer, the source bitmap is uploaded to Firebase Storage. The Firestore layer document stores the Firebase Storage path. Other collaborators' devices download the source bitmap from Storage when they first see that layer in the Firestore snapshot.

### Conflict Resolution Strategy — Last Write Wins Per Field

Firestore uses `SetOptions.merge()` for all layer writes. This means each field (opacity, blendMode, transformJson, etc.) is written and read independently. If two users change different fields of the same layer simultaneously, both changes are preserved. If two users change the same field simultaneously, Firestore's server-side ordering determines the winner — the last write wins.

This is acceptable for MVP because:
1. Simultaneous same-field conflicts on the same layer are rare in practice
2. The result is always a valid, consistent state (not corruption)
3. Users can see each other's cursors and avoid editing the same thing

True CRDT-based conflict resolution is deferred to V2.

### Undo — Local Only (MVP)

Undo/redo stacks are per-device and not synced. This is intentional. Collaborative undo (where User A can undo User B's changes) is one of the hardest problems in distributed systems engineering. It requires a full operation transform (OT) or CRDT implementation and is explicitly deferred to V2.

```kotlin
// domain/usecase/history/PushHistoryUseCase.kt
// Snapshot the current layer stack before every destructive operation.
class PushHistoryUseCase @Inject constructor(private val historyDao: HistoryDao) {
    suspend operator fun invoke(
        canvasId: String,
        layers: List<Layer>,
        description: String
    ) {
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            canvasId = canvasId,
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "",
            description = description,
            layerStackJson = Json.encodeToString(layers),
            timestamp = System.currentTimeMillis()
        )
        historyDao.insertEntry(entry.toEntity())
        historyDao.pruneHistory(canvasId)    // Keep only 50 most recent
    }
}

// domain/usecase/history/UndoUseCase.kt
class UndoUseCase @Inject constructor(private val historyDao: HistoryDao) {
    // The HistoryManager keeps an in-memory deque that mirrors the DB state.
    // We pop from the undo stack and push to redo stack.
    suspend operator fun invoke(
        canvasId: String,
        historyManager: HistoryManager
    ): List<Layer>? {
        return historyManager.undo()    // Returns the previous state's layer list, or null
    }
}
```

### Presence System Implementation

```kotlin
// In EditorViewModel — updated whenever selected layer changes
private fun onLayerSelected(layerId: String?) {
    _uiState.update { it.copy(selectedLayerId = layerId) }
    updatePresenceDebounced(layerId)    // Don't flood Firestore on rapid taps
}

private val presenceUpdateDebouncer = MutableStateFlow<String?>(null)

init {
    viewModelScope.launch {
        presenceUpdateDebouncer
            .debounce(500L)
            .distinctUntilChanged()
            .collect { activeLayerId ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@collect
                firestoreDataSource.updatePresence(canvasId, userId, activeLayerId)
            }
    }
}

// Presence heartbeat — keeps user's presence alive
private fun startPresenceHeartbeat() {
    viewModelScope.launch {
        while (true) {
            delay(20_000L)
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            firestoreDataSource.updatePresence(canvasId, userId, _uiState.value.selectedLayerId)
        }
    }
}
```

---

## 12. Adaptive UI System

### Scope

CanvasX targets **phone portrait only** as its primary layout. `WindowSizeClass` is still wired into the app using the correct Google guideline approach — the Compact layout is fully implemented. Medium and Expanded breakpoints exist in code as stubs that fall through to the Compact layout. This means the app is architecturally prepared for tablet support (V2) without building it now.

### WindowSizeClass Wiring

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Compute window size class — this is the Google-recommended way
            val windowSizeClass = calculateWindowSizeClass(this)

            CanvasXTheme {
                AppNavGraph(windowSizeClass = windowSizeClass)
            }
        }
    }
}

// Pass to screens that need it
@Composable
fun EditorScreen(
    canvasId: String,
    windowSizeClass: WindowSizeClass,
    ...
) {
    // For MVP: always use compact layout
    // For V2: add when (windowSizeClass.widthSizeClass) branching here
    CompactEditorLayout(...)
}
```

### Edge-to-Edge Implementation

```kotlin
// Every Scaffold handles insets explicitly.
// No content is clipped by system bars.

Scaffold(
    topBar = { TopEditorBar(...) },
    bottomBar = { ToolBar(...) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0)  // Scaffold doesn't add default padding
) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            // TopBar and BottomBar handle their own insets internally
    ) {
        CanvasRenderer(
            modifier = Modifier.fillMaxSize()
            // Canvas render area extends under status bar and nav bar
            // — intentional for maximum editing space
        )
    }
}

// ToolBar handles navigation bar inset:
Row(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()   // Adds bottom padding = navigation bar height
        .padding(vertical = 4.dp)
) { ... }

// TopEditorBar handles status bar inset:
TopAppBar(
    // M3 TopAppBar handles statusBarsPadding internally
    modifier = Modifier.statusBarsPadding()
)
```

### Material 3 Expressive — Spring Animations

All transitions in CanvasX use spring-based motion per the Material 3 Expressive guidelines (2025). Tween animations are used only for simple fades.

```kotlin
// core/theme/Theme.kt — M3 theme with spring motion tokens
object CanvasXMotion {
    // Standard spring for most transitions
    val StandardSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    // Decelerate spring for content entering from off-screen
    val DecelerateSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // Bouncy spring for FAB and sheet entrance
    val BounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

// Applied to AnimatedVisibility for sheets:
AnimatedVisibility(
    visible = isSheetVisible,
    enter = slideInVertically(animationSpec = CanvasXMotion.BounceSpring) { it }
        + fadeIn(tween(150)),
    exit = slideOutVertically(animationSpec = CanvasXMotion.DecelerateSpring) { it }
        + fadeOut(tween(100))
) {
    content()
}
```

### Material 3 Theme Setup

```kotlin
// core/theme/Theme.kt
@Composable
fun CanvasXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color — Material You, Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Fallback: static brand colors
        darkTheme -> darkColorScheme(
            primary = Color(0xFF7B61FF),          // Brand purple
            onPrimary = Color.White,
            surface = Color(0xFF1C1B1F)
        )
        else -> lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color.White
        )
    }

    // Set system bar colors (edge-to-edge)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CanvasXTypography,
        shapes = CanvasXShapes,
        content = content
    )
}
```

---

## 13. Background Work & Notifications

### Notification Channels Setup

```kotlin
// CanvasXApplication.kt
@HiltAndroidApp
class CanvasXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val exportChannel = NotificationChannel(
                EXPORT_CHANNEL_ID,
                "Export",
                NotificationManager.IMPORTANCE_LOW   // Low = no sound
            ).apply {
                description = "Shown while exporting a canvas to your gallery"
            }

            val collaborationChannel = NotificationChannel(
                COLLAB_CHANNEL_ID,
                "Collaboration",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when collaborators join or edit your canvas"
            }

            val aiChannel = NotificationChannel(
                AI_CHANNEL_ID,
                "AI Generation",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when an AI image generation completes"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(
                listOf(exportChannel, collaborationChannel, aiChannel)
            )
        }
    }

    companion object {
        const val EXPORT_CHANNEL_ID = "canvasx_export"
        const val COLLAB_CHANNEL_ID = "canvasx_collab"
        const val AI_CHANNEL_ID = "canvasx_ai"
        const val EXPORT_NOTIFICATION_ID = 1001
    }
}
```

### ExportWorker — Full Implementation

```kotlin
// ui/worker/ExportWorker.kt
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val layerRepository: LayerRepository,
    private val renderEngine: RenderEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val canvasId = inputData.getString("canvasId")
            ?: return Result.failure(workDataOf("error" to "Missing canvasId"))
        val formatString = inputData.getString("format") ?: "PNG"
        val quality = inputData.getInt("quality", 90).coerceIn(50, 100)
        val scale = inputData.getFloat("scale", 1f).coerceIn(0.5f, 4f)

        // Show foreground notification immediately (required for long work)
        setForeground(buildForegroundInfo(progress = 0))

        return try {
            // Step 1: Load layers from Room (not from network)
            setProgress(workDataOf("progress" to 5))
            val layers = layerRepository.getLayersForCanvas(canvasId)
            val canvas = layerRepository.getCanvas(canvasId)
                ?: return Result.failure(workDataOf("error" to "Canvas not found"))

            // Step 2: Render final composite (this is a one-shot render, not loop)
            setProgress(workDataOf("progress" to 15))
            val compositedBitmap = renderFinalFrame(
                layers = layers,
                canvasWidthPx = canvas.widthPx,
                canvasHeightPx = canvas.heightPx
            )
            setProgress(workDataOf("progress" to 60))

            // Step 3: Scale if requested
            val outputBitmap = if (scale != 1f) {
                Bitmap.createScaledBitmap(
                    compositedBitmap,
                    (compositedBitmap.width * scale).toInt(),
                    (compositedBitmap.height * scale).toInt(),
                    true
                )
            } else compositedBitmap
            setProgress(workDataOf("progress" to 75))

            // Step 4: Save to device gallery via MediaStore
            val format = if (formatString == "JPEG") Bitmap.CompressFormat.JPEG
                         else Bitmap.CompressFormat.PNG
            val mimeType = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
            val filename = "${canvas.name}_${System.currentTimeMillis()}"
            val uri = saveBitmapToGallery(outputBitmap, filename, format, mimeType, quality)
            setProgress(workDataOf("progress" to 100))

            // Step 5: Show completion notification
            showExportCompleteNotification(uri)

            Result.success(workDataOf("outputUri" to uri.toString()))

        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            // Large bitmaps can OOM — retry with lower scale
            Result.failure(workDataOf("error" to "Out of memory. Try a smaller export size."))
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }

    private suspend fun renderFinalFrame(
        layers: List<Layer>,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        // Same pipeline as RenderEngine but single-shot (no Channel, no caching)
        val effectProcessor = EffectProcessor()
        val blendProcessor = BlendModeProcessor()
        val maskProcessor = MaskProcessor()
        val transformProcessor = TransformProcessor()

        val visibleLayers = layers.filter { it.isVisible }

        // Process all layers
        val processedBitmaps = visibleLayers.map { layer ->
            async {
                val source = withContext(Dispatchers.IO) {
                    BitmapUtils.loadBitmapFromPath(layer.sourceBitmapPath!!)
                }
                val withEffects = effectProcessor.apply(source, layer.effectChain)
                val withMask = if (layer.maskPath != null) {
                    maskProcessor.apply(withEffects, BitmapUtils.loadBitmapFromPath(layer.maskPath))
                } else withEffects
                layer.id to withMask
            }
        }.awaitAll().toMap()

        // Composite
        var composite = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        visibleLayers.forEach { layer ->
            val bitmap = processedBitmaps[layer.id] ?: return@forEach
            val transformed = transformProcessor.apply(bitmap, layer.transform, canvasWidthPx, canvasHeightPx)
            composite = blendProcessor.composite(composite, transformed, layer.blendMode, layer.opacity)
        }
        composite
    }

    private fun saveBitmapToGallery(
        bitmap: Bitmap,
        filename: String,
        format: Bitmap.CompressFormat,
        mimeType: String,
        quality: Int
    ): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CanvasX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!

        resolver.openOutputStream(uri)!!.use { stream ->
            bitmap.compress(format, quality, stream)
        }

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri
    }

    private fun buildForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, EXPORT_CHANNEL_ID)
            .setContentTitle("CanvasX")
            .setContentText("Saving your canvas…")
            .setSmallIcon(R.drawable.ic_export)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(EXPORT_NOTIFICATION_ID, notification)
    }

    private fun showExportCompleteNotification(savedUri: Uri) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, "image/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, EXPORT_CHANNEL_ID)
            .setContentTitle("Canvas saved")
            .setContentText("Tap to open in your gallery")
            .setSmallIcon(R.drawable.ic_export)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(EXPORT_COMPLETE_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXPORT_CHANNEL_ID = "canvasx_export"
        const val EXPORT_NOTIFICATION_ID = 1001
        const val EXPORT_COMPLETE_NOTIFICATION_ID = 1002
    }
}
```

### Firebase Cloud Messaging

```kotlin
// FCM handles 3 notification types — all are triggered server-side
// (via Firebase Cloud Functions, or manually for MVP testing)

// data payload schemas:

// Collaborator joined:
// { "type": "collab_joined", "canvasId": "abc123",
//   "userName": "Alex Chen", "avatarUrl": "https://..." }

// Export complete (if triggered remotely):
// { "type": "export_complete", "canvasId": "abc123" }

// AI generation complete (if app was backgrounded during generation):
// { "type": "ai_complete", "canvasId": "abc123", "layerId": "xyz789" }

class CanvasXMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        when (data["type"]) {
            "collab_joined" -> showCollabNotification(
                canvasId = data["canvasId"] ?: return,
                userName = data["userName"] ?: "Someone",
                avatarUrl = data["avatarUrl"]
            )
            "export_complete" -> showExportNotification(data["canvasId"] ?: return)
            "ai_complete" -> showAiCompleteNotification(
                canvasId = data["canvasId"] ?: return,
                layerId = data["layerId"] ?: return
            )
        }
    }

    override fun onNewToken(token: String) {
        // Save FCM token to Firestore so server can send targeted notifications
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    private fun showCollabNotification(canvasId: String, userName: String, avatarUrl: String?) {
        // Deep link intent — opens editor for the specific canvas
        val deepLinkIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "canvasx://canvas/$canvasId".toUri()
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, deepLinkIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, COLLAB_CHANNEL_ID)
            .setContentTitle("$userName joined your canvas")
            .setContentText("Tap to open the canvas")
            .setSmallIcon(R.drawable.ic_export)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(COLLAB_NOTIFICATION_ID, notification)
    }
}
```

---

## 14. 8-Week Sprint Plan

### Week 1 — Foundation & Auth (Days 1–7)

**Goal:** App compiles, authenticates, creates canvases, navigates to an empty editor. All architecture in place.

**Day 1–2: Project setup**
- Create Android project, min SDK 33, target SDK 35
- Add all Gradle dependencies from Section 2
- Set up `google-services.json` (Firebase)
- Configure `local.properties` for API keys (OPENAI_API_KEY, REMOVE_BG_API_KEY)
- Hilt setup: `CanvasXApplication.kt`, `@HiltAndroidApp`
- `MainActivity.kt`: `enableEdgeToEdge()`, `installSplashScreen()`, `setContent {}`

**Day 3: Core data layer**
- All domain model data classes (Canvas, Layer, Effect sealed class, BlendMode enum, LayerTransform, all others)
- Room database: all entities, all DAOs, `CanvasXDatabase.kt`
- DataStore: `UserPreferencesDataStore.kt`
- All Hilt modules: `DatabaseModule`, `FirebaseModule`, `NetworkModule`, `RepositoryModule`

**Day 4: Navigation skeleton**
- All `@Serializable` route objects in `Routes.kt`
- Full `AppNavGraph.kt` with all destinations stubbed as placeholder screens
- `SplashViewModel.kt` with auth routing logic
- Verify navigation works end-to-end with placeholder screens

**Day 5–6: Auth screen**
- `AuthScreen.kt` full UI implementation
- `AuthRepository.kt` with Google Sign-In + Firebase credential
- `AuthViewModel.kt` with full error handling
- Test sign-in on device

**Day 7: Home screen shell**
- `HomeScreen.kt` grid layout with placeholder cards
- `HomeViewModel.kt` observing Room canvas list via Flow
- `NewCanvasBottomSheet.kt` with size presets
- Canvas creation flow: creates `CanvasEntity` in Room, navigates to `Editor(canvasId)`

**Week 1 exit criteria:** App launches, Google Sign-In works, creating a canvas navigates to an empty editor shell.

---

### Week 2 — Single Layer Render (Days 8–14)

**Goal:** Load one image from gallery, see it rendered on the editor canvas via AGSL.

**Day 8–9: AGSL infrastructure**
- Write `AgslShaders.kt` with BrightnessContrast and GaussianBlur shader source strings
- Write `EffectProcessor.kt` with `applyShader()` helper and two-pass Gaussian blur
- Write a standalone test: apply BrightnessContrast shader to a test bitmap, verify output
- Confirm `RuntimeShader` works on target device (API 33+)

**Day 10–11: RenderEngine + DirtyFlagTracker**
- `DirtyFlagTracker.kt` — hash-based dirty detection
- `RenderCache.kt` — LRU cache
- `RenderEngine.kt` — `Channel(CONFLATED)`, `startRenderLoop()`, `requestRender()`
- Wire `RenderEngine` into `EditorViewModel` — `compositedBitmap: StateFlow<Bitmap?>`

**Day 12–13: CanvasRenderer Compose component**
- `CanvasRenderer.kt` — draws `compositedBitmap` using `Image`, pan + zoom gestures
- Checkerboard transparency background drawn via `Canvas` API
- `EditorScreen.kt` basic phone portrait layout: `TopEditorBar` + canvas area + `ToolBar`

**Day 14: First image layer end-to-end**
- `AddImageLayerUseCase.kt` — photo picker → local file → `Layer` domain object
- Wire `EditorIntent.AddImageLayer` through ViewModel → UseCase → Room → render trigger
- Test: pick an image from gallery, see it appear on the canvas with BrightnessContrast shader applied

**Week 2 exit criteria:** Pick a photo from gallery. It appears on the canvas, rendered via AGSL.

---

### Week 3 — Multi-Layer Compositing + Blend Modes (Days 15–21)

**Goal:** Multiple layers compositing in correct z-order, 5 blend modes working.

**Day 15–16: BlendModeProcessor**
- Write all 5 AGSL blend mode shaders in `AgslShaders.kt`
- `BlendModeProcessor.kt` — `composite()` function dispatches to correct shader
- Unit test: Multiply blend on two known test bitmaps, verify pixel values mathematically

**Day 17–18: Multi-layer state management**
- `ReorderLayersUseCase.kt`
- `DeleteLayerUseCase.kt` with undo support (push history before delete)
- `SetLayerOpacityUseCase.kt`
- `SetBlendModeUseCase.kt`
- ViewModel handles all new intents, triggers render on each

**Day 19–20: Layer panel UI**
- `LayerPanel.kt` — `ModalBottomSheet` with `LazyColumn`
- `LayerItem.kt` — thumbnail, name, visibility toggle, lock toggle, swipe-to-delete
- Wire layer panel open/close to `EditorIntent.ShowSheet(EditorSheet.LayerPanel)`
- Drag-to-reorder using `SwipeToDismissBox` and manual drag handle gesture

**Day 21: Effect chain UI — first pass**
- `EffectChainSheet.kt` — `ModalBottomSheet` with `LazyColumn` of `EffectItem`
- `EffectItem.kt` — expand/collapse, enabled toggle, delete button
- Parameter sliders for BrightnessContrast and GaussianBlur with `snapshotFlow` debounce
- Add remaining effect AGSL shaders: Vignette, ColorTemperature, HSL, Exposure, Sharpen, Grain, Pixelate, EdgeDetection

**Week 3 exit criteria:** 3+ image layers with different blend modes compositing correctly. Layer panel drag-to-reorder works. BrightnessContrast and Blur sliders live-update the canvas.

---

### Week 4 — Transforms + Mask Editor (Days 22–28)

**Goal:** Per-layer gesture transforms. Full mask painting screen.

**Day 22–23: Transform gestures**
- `TransformProcessor.kt` — Matrix-based transform application
- `pointerInput` gesture handlers on `CanvasRenderer` for translate, scale, rotate on selected layer
- `TransformOverlay.kt` — dashed selection border drawn around selected layer bounds
- Note: transforms do NOT dirty the layer (no effect re-render on move — only compositing re-runs)

**Day 24–25: MaskEditorScreen**
- `MaskEditorViewModel.kt` — full implementation as per Section 7 spec
- `MaskEditorScreen.kt` — canvas painting area, brush/eraser toggle, size/hardness sliders
- `detectDragGestures` for stroke painting
- Red overlay rendering on masked areas
- Save mask to local file on "Done"

**Day 26: MaskProcessor + integration**
- `MaskProcessor.kt` — applies ALPHA_8 mask bitmap to layer using AGSL
- Wire mask into RenderEngine render pipeline
- Test: paint a mask in MaskEditorScreen, return to editor, verify masked areas are transparent

**Day 27–28: Solid color layer + remaining effect UI**
- `AddSolidColorLayerUseCase.kt` — creates SOLID_COLOR layer with no source bitmap
- Color picker dialog (M3 `AlertDialog` with custom hex input + ARGB sliders)
- Remaining effect sliders: Sharpen, HSL, Exposure, Grain, Pixelate, ColorTemperature
- Effect picker dialog in `EffectChainSheet`
- All 11 effects reachable and functional

**Week 4 exit criteria:** Transform any layer with gestures. Paint a mask. Add a solid color layer. All 11 effects accessible and applying correctly.

---

### Week 5 — Firebase Sync (Days 29–35)

**Goal:** Two devices editing the same canvas in real time. Collaboration indicators visible.

**Day 29–30: Firestore data sources**
- `FirestoreCanvasDataSource.kt` — canvas CRUD
- `FirestoreLayerDataSource.kt` — `observeLayers()` with `callbackFlow`, `upsertLayer()`, `deleteLayer()`, `updatePresence()`, `observePresence()`
- Mapper extension functions: `Layer.toFirestoreMap()`, `DocumentSnapshot.toLayerDomain()`

**Day 31–32: Repository sync integration**
- `LayerRepository` combines Room (local) + Firestore (remote)
- On every local write (add/update/delete layer), write to both Room and Firestore
- `observeRemoteChanges()` in `EditorViewModel` subscribes to Firestore listener and dispatches `ApplyRemoteLayer*` intents
- Local-vs-remote deduplication: only apply remote changes where `remoteLayer.updatedAt > localLayer.updatedAt`

**Day 33: Presence system**
- `observePresence()` wired into `EditorViewModel` → updates `collaborators` in `EditorUiState`
- `CollaboratorsSheet.kt` shows online collaborators with avatar, name, online dot
- Collaborator presence heartbeat every 20s
- Colored layer border on `LayerItem` when another user is editing that layer

**Day 34: Canvas sharing + invite**
- Invite link: copy `canvasx://canvas/{canvasId}` to clipboard from `CollaboratorsSheet`
- Deep link handling in `AndroidManifest.xml` — `<intent-filter>` for the canvasx:// scheme
- When deep link opens: check if user is in `collaboratorIds` → if yes, open canvas → if no, add user + open canvas

**Day 35: Offline mode**
- Firestore offline persistence is already enabled in `FirebaseModule`
- Add offline banner: observe `FirebaseFirestore.getInstance().disableNetwork()` / network state
- `ConnectivityManager.registerNetworkCallback()` → update `isOffline: Boolean` in ViewModel
- Show "Working offline — changes will sync when reconnected" `Snackbar` when offline

**Week 5 exit criteria:** Open same canvas on two physical devices. Move a layer on one device. See it update on the other within 2 seconds. Presence indicator shows other user's active layer.

---

### Week 6 — API Integrations (Days 36–42)

**Goal:** DALL-E AI layer generation working. Remove.bg background removal working. FCM notifications delivered.

**Day 36–37: DALL-E integration**
- `DalleApiService.kt` Retrofit interface
- `GenerateAiLayerUseCase.kt` — full implementation with error handling for 429, 400, 401, IOException
- `AiGenerationDialog.kt` composable
- Wire `EditorIntent.AddAiLayer` → ViewModel → UseCase → new AI_GENERATED layer in stack
- Test on device with real API key

**Day 38–39: Remove.bg integration**
- `RemoveBgApiService.kt` Retrofit interface
- `RemoveBackgroundUseCase.kt` — upload JPEG, receive transparent PNG, extract alpha as mask
- Wire into `MaskEditorViewModel.applyRemoveBackground()`
- "Remove BG" button in `MaskEditorScreen` with loading state
- Test on device with real API key

**Day 40: Retrofit networking layer**
- `OkHttpClient` with `HttpLoggingInterceptor` (debug builds only)
- Auth interceptors for OpenAI (Bearer header) and Remove.bg (X-Api-Key header)
- Custom `CallAdapter` for `Result<T>` return type wrapping
- Verify all error codes mapped to user-friendly messages

**Day 41: FCM setup**
- `CanvasXMessagingService.kt` implementing `onMessageReceived()` and `onNewToken()`
- Save FCM token to Firestore on token refresh
- Deep link intents from notification taps (opens specific canvas)
- Test: manually send FCM message from Firebase Console → verify notification appears

**Day 42: API error handling audit**
- All API calls wrapped in `try/catch` with `CancellationException` always re-thrown
- Retry logic: 2 automatic retries for network errors, no retry for 4xx client errors
- Loading states visible for all async operations
- Error snackbars dismissible and informative

**Week 6 exit criteria:** Generate an AI layer from a text prompt. Remove a background with one tap. FCM notification arrives when app is in background.

---

### Week 7 — Undo/Redo, History, Settings (Days 43–49)

**Goal:** Full undo/redo stack. Complete Settings screen. Performance pass.

**Day 43–44: History / Undo-Redo system**
- `HistoryManager.kt` — in-memory `ArrayDeque` undo/redo stacks, max 50 entries
- `PushHistoryUseCase.kt` — called before every destructive operation
- `UndoUseCase.kt` / `RedoUseCase.kt`
- Wire `EditorIntent.Undo` and `EditorIntent.Redo` through ViewModel
- Undo/Redo buttons in `TopEditorBar` correctly enabled/disabled
- Verify undo after: layer add, layer delete, layer move, effect change, blend mode change

**Day 45–46: Export pipeline**
- `ExportScreen.kt` — full UI per Section 7 spec
- `ExportViewModel.kt` with WorkManager enqueueing and progress observation
- `ExportWorker.kt` — full implementation from Section 13
- `ExportRepository.kt` — `saveBitmapToGallery()` via MediaStore
- Test: export PNG and JPEG, verify file appears in gallery

**Day 47–48: Settings screen**
- `SettingsScreen.kt` full implementation per Section 7 spec
- `SettingsViewModel.kt` with DataStore read/write
- Theme switching wired to `CanvasXTheme` via DataStore → `collectAsState`
- Dynamic color toggle (Android 12+ only)
- Cache clearing: delete all files in the render cache directory
- Sign out flow with confirmation dialog

**Day 49: Permissions handling**
- `PermissionsDialog.kt` — shown on first editor open if permissions not granted
- Accompanist `rememberMultiplePermissionsState` for Camera + READ_MEDIA_IMAGES
- Handle permanently denied case (open Settings deep link)
- Store `permissionsDialogShown` in DataStore — show at most once

**Week 7 exit criteria:** Undo/Redo works for all operations. Export saves to gallery. Settings theme switching works. Permissions handled gracefully.

---

### Week 8 — Polish, Performance, Predictive Back (Days 50–56)

**Goal:** Demo-ready. No crashes on happy path. Performant. Passes Google guideline audit.

**Day 50: Predictive Back**
- `OnBackPressedCallback` in `EditorScreen` — intercepts back and shows "Save & Exit" dialog
- `handleOnBackProgressed()` — animate canvas scale/translate as user swipes (Android 14+)
- Predictive back dismiss on all `ModalBottomSheet` instances
- Test with system gesture navigation enabled

**Day 51: Spring animations audit**
- Replace any remaining `tween()` animations with `spring()` in sheet enter/exit transitions
- Editor → Home back transition uses `slideOutHorizontally` with spring spec
- FAB scales in with `BounceSpring` on Home screen
- Layer item animations on add/remove use `AnimatedVisibility` with spring

**Day 52: Performance profiling**
- Profile with Android Studio CPU Profiler, trace on a complex canvas (5 layers, all with 3+ effects)
- Target: render frame < 100ms for typical canvas, < 500ms for complex
- Check for any `Dispatchers.Main` blocking (all rendering must be off main thread)
- Bitmap memory: use `Bitmap.Config.ARGB_8888` everywhere, add `inBitmap` reuse where possible
- `RenderCache.onLowMemory()` wired to `Application.onTrimMemory()`

**Day 53: Edge cases + robustness**
- Empty canvas (no layers): show "Tap ➕ to add your first layer" centered message
- Single layer: skip BlendModeProcessor (no compositing needed), draw directly
- Very large bitmaps (> 8MP from camera): auto-downscale to 2048px max dimension before adding as layer
- Canvas with all layers hidden: render checkerboard only
- WorkManager export retry on failure
- Firestore offline: all operations succeed (via Room) and sync on reconnect

**Day 54: Final screen polish**
- Home screen loading shimmer (replace empty grid with shimmer while Room query runs)
- Editor loading state while initial canvas loads from Room
- Error snackbar styling — use M3 Snackbar with action button
- All bottom sheets have proper `navigationBarsPadding`
- Status bar text color correct in both light and dark mode

**Day 55: Build + manifest audit**
- `AndroidManifest.xml`: all permissions declared, deep link intent filter, FCM service
- ProGuard/R8 rules for Retrofit, Gson, Firebase, AGSL
- BuildConfig fields for API keys (not in source control)
- Test release build (not just debug) — check for R8 stripping issues
- `minifyEnabled = true` for release, verify app still works

**Day 56: Demo preparation**
- Record demo video covering: splash → auth → create canvas → add 3 layers → blend modes → effect chain → mask → AI generate → collaboration → export
- Write README.md with architecture overview, tech decisions, setup instructions
- Add inline code comments to `RenderEngine.kt`, `AgslShaders.kt`, `EditorViewModel.kt`
- Tag `v1.0` in Git

**Week 8 exit criteria:** App runs without crash through the full user journey. Demo video recorded. Repository documented.

---

## 15. MVP Scope vs Deferred Features

### In MVP — Build All of This

**Layer types:**
- Image (from gallery or camera)
- Solid color
- AI Generated (DALL-E)

**Blend modes (5):**
- Normal, Multiply, Screen, Overlay, Soft Light

**Effects (11):**
- Brightness/Contrast, Exposure, Gaussian Blur, Sharpen, Vignette, Color Grade, HSL, Color Temperature, Grain, Pixelate, Edge Detection

**Masking:**
- Freehand brush/eraser painting
- Invert mask
- Clear mask
- Remove.bg one-tap background removal

**Transforms:**
- Translate (drag), Scale (pinch), Rotate (two-finger), Flip H/V

**Collaboration:**
- Firebase Firestore metadata sync
- Presence indicators (which layer each user is editing)
- Collaborator invite link
- View-only mode
- Local undo only (50 steps)

**Export:**
- PNG / JPEG to device gallery
- 1x / 2x / custom scale
- WorkManager background export
- Export progress notification

**APIs:**
- DALL-E layer generation
- Remove.bg background removal
- Firebase Auth (Google Sign-In)
- Firebase Firestore (sync)
- Firebase Cloud Messaging (notifications)

**Platform / Google Guidelines:**
- AGSL shaders (API 33+)
- Edge-to-edge with `WindowInsets`
- Material 3 Expressive spring animations
- Predictive Back API
- Type-safe Navigation (Navigation 2.8)
- WorkManager for guaranteed background work
- `collectAsStateWithLifecycle()` (not `collectAsState()`)
- Dynamic color (Material You, Android 12+)
- `WindowSizeClass` wired in (Compact implemented, Medium/Expanded stubbed)

---

### Deferred to V2 — Do Not Build in 8 Weeks

**UI / Screens:**
- Tablet two-pane layout
- Phone landscape layout
- Foldable / `FoldingFeature` detection
- Onboarding HorizontalPager (replaced by permissions dialog in MVP)

**Layer types:**
- Text layer (requires separate rendering path + font handling)
- Shape layer (requires vector-to-bitmap renderer)
- Layer groups / folder layers

**Blend modes (7):**
- Hard Light, Difference, Exclusion, Hue, Luminosity, Color Dodge, Color Burn

**Masking:**
- Linear gradient mask
- Radial gradient mask
- Smart subject detection (Google Vision API)

**Layer management:**
- Merge layers
- Duplicate canvas

**Collaboration:**
- Collaborative undo (one of the hardest problems in distributed systems)
- Kick collaborator from canvas
- Collaborative named history steps

**Home screen:**
- Pin canvas to top
- Duplicate canvas

**Cloud / Sharing:**
- Cloudinary upload and shareable link

**Settings:**
- Per-app language preference

**Performance:**
- `inBitmap` recycling pool
- Tiled rendering for very large canvases

---

*End of CanvasX Master Development Plan — Version 2.0*
*Cuts applied: Cloudinary removed, Onboarding removed, Blend modes reduced to 5 (Normal, Multiply, Screen, Overlay, Soft Light)*
*Target: 8-week solo build, phone portrait only, API 33+*
