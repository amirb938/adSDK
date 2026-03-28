package tech.done.ads.tracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.done.ads.network.NetworkLayer
import tech.done.ads.network.NetworkResponse

class RetryingTrackingEngineTest {

    private class FakeNetwork : NetworkLayer {
        override val dispatcher = Dispatchers.Unconfined
        val requested = mutableListOf<String>()
        override suspend fun get(url: String, timeoutMs: Long?, headers: Map<String, String>): NetworkResponse {
            requested += url
            return NetworkResponse(200, "ok")
        }
    }

    @Test
    fun firesGetForEachTrackingUrl() = runBlocking {
        val net = FakeNetwork()
        val engine = RetryingTrackingEngine(net, dispatcher = Dispatchers.Unconfined, maxAttempts = 1)
        engine.track(TrackingEvent.Start, listOf("https://t1/a", "https://t1/b"))
        assertEquals(listOf("https://t1/a", "https://t1/b"), net.requested)
    }

    @Test
    fun emptyUrlsNoNetwork() = runBlocking {
        val net = FakeNetwork()
        val engine = RetryingTrackingEngine(net, dispatcher = Dispatchers.Unconfined)
        engine.track(TrackingEvent.Complete, emptyList())
        assertTrue(net.requested.isEmpty())
    }
}
