# Module: `network`

## Overview

The **network** module defines the **HTTP abstraction** used across the SDK for downloading ad tags, VAST documents, and tracking beacons. It keeps the stack-agnostic boundary small: a single **`get`** operation plus a **`CoroutineDispatcher`** for I/O.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`NetworkLayer`** | **`suspend fun get(url, timeoutMs?, headers?): NetworkResponse`** and **`val dispatcher: CoroutineDispatcher`**. |
| **`NetworkResponse`** | HTTP status **`code`**, **`body`**, **`headers`**, and **`isSuccessful`** (2xx). |
| **`AdSdkLogConfig`** | Global **`isDebugLoggingEnabled`** flag in package **`tech.done.ads`**, consumed by **`Media3AdsLoader`**, **`RetryingTrackingEngine`**, and debug helpers in other modules. |

## Dependencies

- **Third-party:** Kotlin Coroutines, Timber

## Integration / Usage

Provide an implementation backed by your HTTP client:

```kotlin
class OkHttpNetworkLayer(
    private val client: OkHttpClient,
) : NetworkLayer {
    override val dispatcher = Dispatchers.IO

    override suspend fun get(
        url: String,
        timeoutMs: Long?,
        headers: Map<String, String>,
    ): NetworkResponse = withContext(dispatcher) {
        // execute request, map to NetworkResponse
    }
}
```

**`player-media3`** includes a **`SampleNetworkLayer`** suitable for demos only. The **sample app** uses **`tech.done.ads.sample.SampleNetworkLayer`**.

Enable verbose SDK logging where supported:

```kotlin
AdSdkLogConfig.isDebugLoggingEnabled = true
```
