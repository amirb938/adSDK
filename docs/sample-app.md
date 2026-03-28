# Module: `sample-app`

## Overview

**sample-app** is an **Android application** module that demonstrates end-to-end integration of **`Media3AdsLoader`** with **Jetpack Compose** as the activity shell (**`ComponentActivity`**, **`setContent`**). It loads a **bundled VMAP** from assets, enables **Timber** logging, and registers **`SampleAdsEventLogger`** as an **`AdsEventListener`**. It depends on **`ui-compose`** for optional Compose-based ad UI experiments (the default demo uses the SDK’s built-in **`AdOverlayView`** inside **`AdDisplayContainerView`**).

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`MainActivity`** | Builds content **`ExoPlayer`**, **`Media3AdsLoader`**, **`AndroidView`** for **`PlayerView`** and **`AdDisplayContainerView`**, calls **`requestAdsFromVMAPXml`** + **`start`**. Releases player and loader on **`onStop`**; recreates on **`onStart`** after teardown (demo lifecycle). |
| **`SampleApplication`** | Application class (Timber tree setup if configured). |
| **`SampleNetworkLayer`** | App-level **`NetworkLayer`** implementation for real device fetches when not using the loader’s **`SampleNetworkLayer`**. |
| **`SampleAdsEventLogger`** | Example **`AdsEventListener`** implementation logging SDK callbacks. |
| **`assets/sample_vmap.xml`** | Sample VMAP payload for local testing. |

## Dependencies

- **Project modules:** `core`, `parser`, `scheduler`, `tracking`, `network`, `player-common`, `player-media3`, `ui-compose`
- **Third-party:** Media3, Compose, Material3, Activity Compose, Lifecycle, Coroutines, Timber

## Integration / Usage

This module is not published as a library. Run it from Android Studio or Gradle:

```bash
./gradlew :sample-app:installDebug
```

Use it as a reference for **`Media3AdsLoader`** wiring, asset-based VMAP testing, and **`AdsEventListener`** usage.
