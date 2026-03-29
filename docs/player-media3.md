# Module: `player-media3`

## Overview

**player-media3** is the **recommended integration** for Android apps using **AndroidX Media3**. It offers an **IMA-like API**: the app keeps ownership of the **content** **`ExoPlayer`** and **`PlayerView`**, while the SDK supplies **`Media3AdsLoader`**, **`AdDisplayContainerView`**, a dedicated **ad** **`ExoPlayer`**, inline ad UI (**`AdOverlayView`**), and wiring into **`DefaultAdEngine`**.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`Media3AdsLoader`** | Entry point: **`Media3AdsLoader.builder(context)`** with **`network`**, **`tracking`**, **`scope`**, **`debugLogging`**; the previous **`Context`**-first constructor is **deprecated** but still builds a loader with **`SampleNetworkLayer`**. Then: **`setPlayer`**, **`setAdDisplayContainer`**, optional **`setAdMarkersContainerView`**, **`setVideoSurfaceView`**, **`setUiConfig`**, **`setContentUi`**. **`requestAds(adTagUri)`** fetches XML, detects VMAP vs VAST, loads into engine. **`requestAdsFromVMAPXml`** for pre-fetched VMAP. **`start`**, **`release`**. Exposes **`StateFlow`** **`isAdPlaying`** and **`playerState`** (**`PlayerState`**: content/ad positions, **`isInAd`**, **`adSkipOffsetMs`**, **`isAdSkippable`**). **`setShowBuiltInAdOverlay(false)`** omits **`AdOverlayView`** so hosts can render Compose (or other) chrome from **`playerState`**. **`skipCurrentAd()`** calls **`PlayerAdapter.resumeContent()`** (posts to main if needed). **`AdsEventListener`**, **`AdPlaybackListener`**. |
| **`AdDisplayContainerView`** | **`FrameLayout`** host for ad **`PlayerView`** and overlay; clips disabled for overlay stacking. |
| **`Media3ImaLikePlayerAdapter`** (internal) | Dual-player strategy: pauses content, shows ad surface, **`playAd`** / **`resumeContent`**, propagates **`PlayerState`**. Respects **`showBuiltInAdOverlay`** (skip/countdown **`AdOverlayView`** optional). |
| **`AdOverlayView`** (internal) | Traditional View hierarchy: countdown, skip-in, skip button; styled via **`AdSdkUiConfig`** and string resources. |
| **`AdSdkUiConfig`** | Optional accent color, typeface, button corner radius for the overlay. |
| **`AdPlaybackSignal`** (internal) | Derives start/end/error ad playback events from player state for **`isAdPlaying`** and listeners. |
| **`SampleNetworkLayer`** | Demo **`NetworkLayer`** using Android **`HttpURLConnection`**. |

## Dependencies

- **Project modules:** `player-common`, `core`, `parser`, `scheduler`, `tracking`, `network`
- **Third-party:** Media3 ExoPlayer, Media3 Common, Media3 UI, Kotlin Coroutines (Android), AndroidX Annotation, Timber

## Integration / Usage

```kotlin
val adsLoader = Media3AdsLoader.builder(context)
    .network(network)
    .scope(scope)
    .build()
    .apply {
        setPlayer(contentExoPlayer)
        setAdDisplayContainer(adDisplayContainerView)
        setAdMarkersContainerView(playerView) // optional midroll markers
    }
adsLoader.requestAds("https://example.com/ad-tag")
adsLoader.start()
// onDestroy:
adsLoader.release()
```

Requirements: configure **`setPlayer`** and **`setAdDisplayContainer`** on the **main thread** before **`requestAds`**. Use **`getVideoSurfaceSizePx`** when you need dimensions for VAST resolution rules that depend on player size.

### Compose overlay instead of built-in UI

```kotlin
adsLoader.setShowBuiltInAdOverlay(false)
// Collect adsLoader.playerState → map to AdUiState → AdOverlay(...)
// adsLoader.skipCurrentAd()
```

See **[ui-compose](ui-compose.md)** and **[sample-app](sample-app.md)**.
