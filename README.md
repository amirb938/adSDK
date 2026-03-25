# AdSDK (project skeleton)

Multi-module Android Ads SDK skeleton with clean separation of concerns.

## Modules

- `:ad-sdk-api`: Public SDK entrypoints and configuration
- `:ad-sdk-core`: Core engine abstractions (pure Kotlin, no Android)
- `:ad-sdk-parser`: VAST/VMAP parsing abstractions + models (pure Kotlin)
- `:ad-sdk-scheduler`: Ad scheduling abstractions
- `:ad-sdk-tracking`: Tracking abstractions + models
- `:ad-sdk-network`: Network abstractions + models
- `:ad-sdk-ui-compose`: Optional Compose UI components (kept independent)
- `:ad-sdk-player-common`: Player-agnostic adapter interfaces + models
- `:ad-sdk-player-media3`: Media3 adapter surface (depends on `:ad-sdk-player-common`)
- `:ad-sdk-player-exoplayer2`: ExoPlayer v2 adapter surface (depends on `:ad-sdk-player-common`)
- `:sample-app`: Minimal Media3 sample app (integration placeholder)

## Dependency rules enforced by design

- `:ad-sdk-core` does **not** depend on Android framework (pure Kotlin JVM)
- `:ad-sdk-parser` is **pure Kotlin** (Kotlin JVM)
- Player modules depend on `:ad-sdk-player-common` only (plus their player libs)
- `:ad-sdk-ui-compose` is optional and kept independent (no direct player dependencies)

## Build

Open in Android Studio and sync Gradle.

