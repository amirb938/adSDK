# Module: `player-common`

## Overview

The **player-common** module defines the **player-agnostic boundary** between the ad engine and any concrete media stack. **DefaultAdEngine** depends only on **PlayerAdapter** and **PlayerListener**, not on ExoPlayer or Media3 types.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **PlayerAdapter** | Exposes `StateFlow<PlayerState>`, listener registration, **playAd(mediaUri, adSkipOffsetMs?, simidInteractiveCreativeUrl?)**, **resumeContent()**, **pause()** and **play()**, **setSeekingEnabled**. |
| **PlayerState** | Fields: **isPlaying**, **isInAd**, content and ad positions and durations, **adSkipOffsetMs**, **isAdSkippable** (whether the current linear ad may show skip UI). **Media3AdsLoader** exposes **`StateFlow<PlayerState>`** as **`playerState`** for Compose or other UI that maps into **ui-compose** **`AdUiState`**. |
| **PlayerListener** | Content and ad lifecycle hooks: ended, app foreground and background, ad progress, errors (default empty implementations). |
| **AdsSchedulingListener** | Timeline and break-level callbacks: VMAP parsed, ad break loading, content pause/resume, VAST loaded, errors. |
| **AdsCreativePlaybackListener** | Creative playback callbacks: impression, start, progress, quartiles, pause/resume, skip, complete. |
| **AdsEventListener** | Combines **AdsSchedulingListener** and **AdsCreativePlaybackListener** (single type for full analytics). |
| **AdsEventKind** / **AdsEventPayload** / **dispatchAdsEvent** | Central dispatch used by **DefaultAdEngine** so each lifecycle point maps to one enum kind and optional payload (durations, URIs, errors, etc.). |
| **AdsEventMulticaster** | Thread-safe fan-out implementation of **AdsEventListener** used by **Media3AdsLoader**. |

## Dependencies

- **Third-party:** Kotlin Coroutines, Timber

## Integration / Usage

Custom player integrations implement **PlayerAdapter** and pass it to **DefaultAdEngine**. Reference implementations:

- **player-media3:** **Media3ImaLikePlayerAdapter** exposes an internal **PlayerAdapter** to the engine.

Subscribe to SDK events via **`Media3AdsLoader.addAdSdkEventListener`**. **`AdsEventListener`** inherits default empty implementations from **`AdsSchedulingListener`** and **`AdsCreativePlaybackListener`**, so you override only the callbacks you care about:

```kotlin
adsLoader.addAdSdkEventListener(object : AdsEventListener {
    override fun onAdStarted(breakId: String?) { }
    override fun onVMAPParsed(version: String?, adBreakCount: Int) { }
})
```
