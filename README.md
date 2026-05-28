# CanvasX

CanvasX is a collaborative Android image editor built with Kotlin, Jetpack Compose, Firebase, Room, Hilt, and a custom GPU-assisted rendering pipeline. It is designed for fast layer-based editing, real-time collaboration, and AI-assisted image generation in a polished mobile experience.

<p align="center">
  <img src="docs/screenshots/01-home.png" alt="CanvasX home screen" width="180" />
  <img src="docs/screenshots/02-editor.png" alt="CanvasX editor" width="180" />
  <img src="docs/screenshots/04-effect-options.png" alt="CanvasX effect options" width="180" />
  <img src="docs/screenshots/06-text-options.png" alt="CanvasX text options" width="180" />
</p>

## Why It Stands Out

- Real-time collaborative canvas editing backed by Firebase Realtime Database and Firestore.
- Layer-based image editor with image, solid color, text, and AI-generated layers.
- GPU-assisted rendering with AGSL shaders, blend modes, effects, transforms, caching, and responsive gesture feedback.
- Offline-first canvas library powered by Room and Firebase sync.
- Clean Compose UI with Material 3, edge-to-edge support, bottom sheets, dialogs, and adaptive surfaces.
- Google authentication, share codes, collaborator management, account deletion, and local session cleanup.
- Gemini-powered image generation flow that inserts AI output as a canvas layer.

## Screenshots

| Canvas Library | Editor | Layers | Add Layer | Effects | Text |
|---|---|---|---|---|---|
| <img src="docs/screenshots/01-home.png" width="150" alt="Canvas library" /> | <img src="docs/screenshots/02-editor.png" width="150" alt="Canvas editor" /> | <img src="docs/screenshots/03-layers.png" width="150" alt="Layers panel" /> | <img src="docs/screenshots/05-add-layer.png" width="150" alt="Add layer sheet" /> | <img src="docs/screenshots/04-effect-options.png" width="150" alt="Effect options" /> | <img src="docs/screenshots/06-text-options.png" width="150" alt="Text options" /> |

## Feature Set

CanvasX supports creating and joining shared canvases, pinning and searching projects, renaming or deleting canvases, and managing collaborators. Inside the editor, users can add image, solid color, text, and AI-generated layers; transform layers with touch gestures; adjust opacity and blend modes; apply effects such as saturation, blur, sharpen, exposure, vignette, grain, pixelate, and color temperature; and export or share the final composition.

The app keeps canvas metadata locally in Room for a fast library experience, synchronizes remote state through Firebase, and stores source images and thumbnails in Firebase Storage. Real-time layer operations are pushed as small collaboration events instead of repeatedly uploading large image files.

## Tech Stack

- Kotlin and Jetpack Compose
- Material 3 and AndroidX Navigation
- Hilt dependency injection
- Room local persistence
- Firebase Authentication, Firestore, Realtime Database, and Storage
- Google Sign-In
- Kotlin Serialization and Coroutines/Flow
- Coil image loading
- AGSL shaders, Android Canvas APIs, RenderNode, and HardwareRenderer
- Gemini image generation API
- JUnit and Android instrumented tests

## Architecture

The project follows a layered Android architecture:

- `domain`: canvas, layer, collaborator, effect, and repository contracts.
- `data`: Firebase repositories, Room entities/DAO/database, bitmap loading, thumbnail storage, and export helpers.
- `engine`: render loop, GPU shader rendering, transforms, blend modes, and render caching.
- `ui`: Compose screens for authentication, the canvas library, editor, navigation, and theme.
- `di`: Hilt modules for Firebase, storage, database, repositories, and rendering services.

Rendering is split into an instant display path and an asynchronous processing path. The editor can keep gestures responsive while background rendering applies heavier effects and recomposites the canvas.

## Getting Started

### Prerequisites

- Android Studio or the Android SDK command-line tools
- JDK 17
- Android SDK platform 36 or newer
- A Firebase project configured for package `com.my_app.art_collab`

### Firebase Setup

Place your Firebase Android config at:

```text
app/google-services.json
```

Create a local `local.properties` file with your SDK path and Gemini API key:

```properties
sdk.dir=/path/to/Android/sdk
GEMINI_API_KEY=your_gemini_api_key
```

Do not commit `local.properties`; it is intentionally ignored.

### Run Locally

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run the `app` configuration.

## Validation

The final release-prep pass was verified with:

```bash
./gradlew testDebugUnitTest :app:assembleDebug
./gradlew connectedDebugAndroidTest
```

## Project Highlights For Reviewers

- Built a non-trivial image editing engine rather than relying on a single off-the-shelf editor component.
- Designed a Firebase collaboration model that syncs lightweight layer operations and stores image blobs separately.
- Used Compose state, ViewModels, Flows, and Hilt to keep UI and data responsibilities separated.
- Added account/session cleanup and local persistence so the app behaves predictably across sign-in, sign-out, and account deletion.
- Captured the final screenshots from a running Pixel 9 Pro emulator with a real authenticated app session.

## License

This project is intended as a portfolio project. Add a license before accepting external contributions or reuse.
