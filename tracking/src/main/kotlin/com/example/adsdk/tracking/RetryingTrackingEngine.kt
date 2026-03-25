package com.example.adsdk.tracking

import com.example.adsdk.network.NetworkLayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

class RetryingTrackingEngine(
    private val network: NetworkLayer,
    override val dispatcher: CoroutineDispatcher = network.dispatcher,
    private val maxAttempts: Int = 3,
    private val initialBackoffMs: Long = 250,
) : TrackingEngine {

    override suspend fun track(event: TrackingEvent, urls: List<String>) {
        if (urls.isEmpty()) return

        withContext(dispatcher) {
            // Fire sequentially to keep resource usage predictable in SDK core.
            for (url in urls) {
                trackSingle(url)
            }
        }
    }

    private suspend fun trackSingle(url: String) {
        var attempt = 1
        var backoff = initialBackoffMs
        while (attempt <= maxAttempts) {
            val resp = runCatching { network.get(url, timeoutMs = 10_000) }.getOrNull()
            if (resp != null && resp.isSuccessful) return

            if (attempt == maxAttempts) return
            delay(backoff)
            backoff = min(backoff * 2, 5_000)
            attempt++
        }
    }
}

