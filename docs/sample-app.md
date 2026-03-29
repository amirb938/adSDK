# Module: `sample-app`

## Overview

**sample-app** is an **Android application** module that demonstrates end-to-end integration of **`Media3AdsLoader`** with **Jetpack Compose** (**`ComponentActivity`**, **`setContent`**). It loads a **bundled VMAP** from assets, enables **Timber** logging, and registers **`SampleAdsEventLogger`** as an **`AdsEventListener`**.

The demo uses **Compose for the ad overlay** instead of the default View overlay:

- **`Media3AdsLoader.setShowBuiltInAdOverlay(false)`** — built-in **`AdOverlayView`** is not attached.
- **`playerState`** is collected from **`adsLoader.playerState`** and passed into **`SampleCustomAdOverlay`** (**`ui-compose`** **`AdOverlay`** with **`overrideContent`**).
- Skip is wired to **`adsLoader.skipCurrentAd()`**.

**`SampleCustomAdOverlay`** targets **TV-style** use: skip stays in the focus order (**`TextButton`** remains enabled for focus; **`onClick`** only skips when **`canSkip`**), **`FocusRequester`** + **`LaunchedEffect`** request default focus on the skip control, and **`focusGroup()`** scopes D-pad navigation on the top bar.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`MainActivity`** | Builds content **`ExoPlayer`**, **`Media3AdsLoader.builder(context).scope(...).debugLogging(...).build()`**, **`setShowBuiltInAdOverlay(false)`**, **`AndroidView`** for **`PlayerView`** and **`AdDisplayContainerView`**, collects **`playerState`**, **`SampleCustomAdOverlay`** on top, **`requestAdsFromVMAPXml`** + **`start`**. Releases player and loader on **`onStop`**; recreates on **`onStart`** after teardown (demo lifecycle). |
| **`SampleComposeAdOverlay.kt`** | **`playerStateToAdUiState`**, **`SampleCustomAdOverlay`** — maps **`PlayerState`** (including **`isAdSkippable`**: no skip affordance when the creative is not skippable), themed **`AdUiStyle`**, custom top bar via **`overrideContent`**; skip **`FocusRequester`** only when skip UI is shown. |
| **`SampleApplication`** | Application class (Timber tree setup if configured). |
| **`SampleNetworkLayer`** | App-level **`NetworkLayer`** for real device fetches when not using the loader’s **`SampleNetworkLayer`**. |
| **`SampleAdsEventLogger`** | Example **`AdsEventListener`** logging SDK callbacks. |
| **`assets/sample_vmap.xml`** | Sample VMAP payload for local testing. |

## Dependencies

- **Project modules:** `core`, `parser`, `scheduler`, `tracking`, `network`, `player-common`, `player-media3`, `ui-compose`
- **Third-party:** Media3, Compose, Material3, Activity Compose, Lifecycle, Coroutines, Timber

## Integration / Usage

This module is not published as a library. Run it from Android Studio or Gradle:

```bash
./gradlew :sample-app:installDebug
```

Use it as a reference for **`Media3AdsLoader`** wiring, **`playerState`** + **`ui-compose`**, asset-based VMAP testing, and **`AdsEventListener`** usage.

## Related documentation

- **[ui-compose](ui-compose.md)** — **`AdOverlay`** / **`AdUiState`**.
- **[player-media3](player-media3.md)** — loader API surface.
