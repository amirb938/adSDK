# Module: `player-common`

## Overview

The **player-common** module defines the **player-agnostic boundary** between the ad engine and any concrete media stack. **DefaultAdEngine** depends only on **PlayerAdapter** and **PlayerListener**, not on ExoPlayer or Media3 types.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **PlayerAdapter** | Exposes `StateFlow<PlayerState>`, listener registration, **playAd(mediaUri, adSkipOffsetMs?)**, **resumeContent()**, **pause()** and **play()**, **setSeekingEnabled**. |
| **PlayerState** | Fields: **isPlaying**, **isInAd**, content and ad positions and durations, **adSkipOffsetMs**. |
| **PlayerListener** | Content and ad lifecycle hooks: ended, app foreground and background, ad progress, errors (default empty implementations). |
| **AdsEventListener** | Rich SDK-level callbacks (VMAP parsed, break loading, content pause and resume, quartiles, skip, complete, errors). |
| **AdsEventMulticaster** | Thread-safe fan-out implementation of **AdsEventListener** used by **Media3AdsLoader**. |

## Dependencies

- **Third-party:** Kotlin Coroutines, Timber

## Integration / Usage

Custom player integrations implement **PlayerAdapter** and pass it to **DefaultAdEngine**. Reference implementations:

- **player-media3:** **Media3ImaLikePlayerAdapter** exposes an internal **PlayerAdapter** to the engine.
- **player-exoplayer2:** **ExoPlayer2Adapter** uses a single ExoPlayer2 instance and swaps **MediaItem** for ads versus content.

Subscribe to SDK events:

```kotlin
adsLoader.addAdSdkEventListener(object : AdsEventListener {
    override fun onAdStarted(breakId: String?) { }
})
```
