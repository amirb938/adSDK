# Module: `player-exoplayer2`

## Overview

**player-exoplayer2** targets applications still on **Google ExoPlayer 2.x** (artifact `com.google.android.exoplayer:exoplayer`). It provides **`ExoPlayer2Adapter`**, a **`PlayerAdapter`** that uses **one** `ExoPlayer` instance and alternates between **content** and **ad** **`MediaItem`** instances. Unlike **`player-media3`**, this module does **not** ship an IMA-like container view or built-in ad overlay; the host is responsible for UI and layout.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`ExoPlayer2Adapter`** | Implements **`PlayerAdapter`**: saves content item/position before **`playAd`**, restores on **`resumeContent`**, polls/pushes **`PlayerState`**, forwards **`Player.Listener`** events to **`PlayerListener`**. |

## Dependencies

- **Project modules:** `player-common`
- **Third-party:** ExoPlayer2, Kotlin Coroutines (Android), Timber

## Integration / Usage

```kotlin
val adapter = ExoPlayer2Adapter(player = exoPlayer2, scope = scope)
val engine = DefaultAdEngine(
    player = adapter,
    vmapParser = VMAPPullParser(),
    vastParser = VASTPullParser(),
    scheduler = VMAPScheduler(),
    network = networkLayer,
    tracking = RetryingTrackingEngine(networkLayer),
    mainDispatcher = Dispatchers.Main,
).apply { initialize() }

lifecycleScope.launch { engine.loadVMAP(vmapXml) }
engine.start()
```

For new projects, **Media3** via **`player-media3`** is the preferred path.
