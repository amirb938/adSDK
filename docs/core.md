# Module: `core`

## Overview

The **core** module hosts the primary ad orchestration engine. It connects VMAP-driven timelines, VAST resolution, a **`PlayerAdapter`** implementation, network I/O, and tracking into one cohesive playback controller. The canonical implementation is **`DefaultAdEngine`**, which implements **`AdEngine`**.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`AdEngine`** | Lifecycle API: `initialize`, `start`, `stop`, `release`, and suspend `loadVMAP(xml)`. Exposes read-only **`AdState`**. |
| **`DefaultAdEngine`** | Parses VMAP, builds timeline via **`AdScheduler`**, fetches and parses VAST, plays ad breaks in sequence, handles skip offsets, fires **`TrackingEngine`** events, and notifies **`AdsEventListener`** through **`dispatchAdsEvent`** (**`AdsEventKind`**, **`AdsEventPayload`**). Maintains **`StateFlow<AdEngineState>`** (`stateFlow`) for fine-grained UI/state observation. While a linear ad is playing, the engine waits for playback to end **or** for **`PlayerState.isInAd`** to become **`false`** (for example after the host calls **`PlayerAdapter.resumeContent()`** / user skip), so **`onAdEnded`** is not required when the ad **`ExoPlayer`** is stopped without reaching **`STATE_ENDED`**. The internal “ad wait” watchdog is based on **playing time** (paused time excluded) so pausing an ad does not cause the engine to exit the ad break prematurely. |
| **`AdState` / `AdEngineState`** | Phase (`Idle`, `Initialized`, `Running`, `Stopped`, `Released`, `Error`), `loaded`, `inAd`, `currentBreak`, `lastError`. |

Internal helpers (for example **`AdSdkDebugLog`**) support consistent debug tagging.

## Dependencies

- **Project modules:** `parser`, `scheduler`, `tracking`, `network`, `player-common`
- **Third-party:** Kotlin Coroutines, Timber

## Integration / Usage

**Direct engine usage** (advanced or custom player integration):

```kotlin
val engine = DefaultAdEngine(
    player = playerAdapter,
    vmapParser = VMAPPullParser(),
    vastParser = VASTPullParser(),
    scheduler = VMAPScheduler(),
    network = networkLayer,
    tracking = trackingEngine,
    mainDispatcher = Dispatchers.Main,
    adsEventListener = optionalListener,
).apply { initialize() }

// After obtaining VMAP XML (e.g. from your own fetch):
lifecycleScope.launch { engine.loadVMAP(vmapXml) }
engine.start()
```

The **Media3** integration module **`player-media3`** constructs **`DefaultAdEngine`** internally via **`Media3AdsLoader`**; most applications do not reference **`DefaultAdEngine`** directly.
