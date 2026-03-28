# Module: `tracking`

## Overview

The **tracking** module defines how the SDK fires **VAST tracking URLs** (impressions, quartiles, progress, complete, errors, etc.). Callers pass a **`TrackingEvent`** and a list of URLs; the engine is responsible for collecting those URLs from parsed VAST.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`TrackingEngine`** | **`suspend fun track(event: TrackingEvent, urls: List<String>)`**; exposes **`dispatcher`** for coroutine context. |
| **`TrackingEvent`** | Enum: `Impression`, `Start`, `FirstQuartile`, `Midpoint`, `ThirdQuartile`, `Progress`, `Pause`, `Resume`, `Complete`, `Error`. |
| **`RetryingTrackingEngine`** | Default implementation: uses **`NetworkLayer.get`** per URL with retry and exponential backoff; runs on **`network.dispatcher`** by default. Respects **`AdSdkLogConfig.isDebugLoggingEnabled`** for diagnostics. |

## Dependencies

- **Project modules:** `network`
- **Third-party:** Kotlin Coroutines, Timber

## Integration / Usage

**`Media3AdsLoader`** defaults to **`RetryingTrackingEngine(network)`**. Override for tests or custom analytics:

```kotlin
Media3AdsLoader(
    network = myNetwork,
    tracking = object : TrackingEngine {
        override val dispatcher = Dispatchers.IO
        override suspend fun track(event: TrackingEvent, urls: List<String>) {
            // custom beacon handling
        }
    },
)
```

**`DefaultAdEngine`** accepts **`TrackingEngine`** in its constructor for direct engine integrations.
