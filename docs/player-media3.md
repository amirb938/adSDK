# Module: `player-media3`

## Overview

**player-media3** is the **recommended integration** for Android apps using **AndroidX Media3**. It offers an **IMA-like API**: the app keeps ownership of the **content** **`ExoPlayer`** and **`PlayerView`**, while the SDK supplies **`Media3AdsLoader`**, **`AdDisplayContainerView`**, a dedicated **ad** **`ExoPlayer`**, inline ad UI (**`AdOverlayView`**), and wiring into **`DefaultAdEngine`**.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`Media3AdsLoader`** | Entry point: **`setPlayer`**, **`setAdDisplayContainer`**, optional **`setAdMarkersContainerView`**, **`setVideoSurfaceView`**, **`setUiConfig`**, **`setContentUi`**. **`requestAds(adTagUri)`** fetches XML, detects VMAP vs VAST, loads into engine. **`requestAdsFromVMAPXml`** for pre-fetched VMAP. **`start`**, **`release`**. Exposes **`isAdPlaying`**, **`AdsEventListener`** registration, **`AdPlaybackListener`**, optional **`Context`** constructor with **`SampleNetworkLayer`**. |
| **`AdDisplayContainerView`** | **`FrameLayout`** host for ad **`PlayerView`** and overlay; clips disabled for overlay stacking. |
| **`Media3ImaLikePlayerAdapter`** (internal) | Dual-player strategy: pauses content, shows ad surface, **`playAd`** / **`resumeContent`**, propagates **`PlayerState`**. |
| **`AdOverlayView`** (internal) | Traditional View hierarchy: countdown, skip-in, skip button; styled via **`AdSdkUiConfig`** and string resources. |
| **`AdSdkUiConfig`** | Optional accent color, typeface, button corner radius for the overlay. |
| **`AdPlaybackSignal`** (internal) | Derives start/end/error ad playback events from player state for **`isAdPlaying`** and listeners. |
| **`SampleNetworkLayer`** | Demo **`NetworkLayer`** using Android **`HttpURLConnection`**. |

## Dependencies

- **Project modules:** `player-common`, `core`, `parser`, `scheduler`, `tracking`, `network`
- **Third-party:** Media3 ExoPlayer, Media3 Common, Media3 UI, Kotlin Coroutines (Android), AndroidX Annotation, Timber

## Integration / Usage

```kotlin
val adsLoader = Media3AdsLoader(network = network, scope = scope).apply {
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
