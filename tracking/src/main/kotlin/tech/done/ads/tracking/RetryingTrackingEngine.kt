package tech.done.ads.tracking

import tech.done.ads.AdSdkLogConfig
import tech.done.ads.network.NetworkLayer
import timber.log.Timber
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
        if (AdSdkLogConfig.isDebugLoggingEnabled) {
            Timber.tag("AdSDK/Tracking").d("event=$event urls=${urls.size}")
        }

        withContext(dispatcher) {
            for (url in urls) {
                trackSingle(url)
            }
        }
    }

    private suspend fun trackSingle(url: String) {
        var attempt = 1
        var backoff = initialBackoffMs
        while (attempt <= maxAttempts) {
            if (AdSdkLogConfig.isDebugLoggingEnabled) {
                Timber.tag("AdSDK/Tracking").d("fire attempt=$attempt url=$url")
            }
            val resp = runCatching { network.get(url, timeoutMs = 10_000) }.getOrNull()
            if (AdSdkLogConfig.isDebugLoggingEnabled) {
                Timber.tag("AdSDK/Tracking").d("resp attempt=$attempt code=${resp?.code} ok=${resp?.isSuccessful}")
            }
            if (resp != null && resp.isSuccessful) return

            if (attempt == maxAttempts) return
            delay(backoff)
            backoff = min(backoff * 2, 5_000)
            attempt++
        }
    }
}

