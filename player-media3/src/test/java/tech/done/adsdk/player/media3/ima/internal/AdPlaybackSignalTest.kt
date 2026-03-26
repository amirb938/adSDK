package tech.done.adsdk.player.media3.ima.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class AdPlaybackSignalTest {
    @Test
    fun `starts false until ad actually plays`() {
        val s = AdPlaybackSignal()

        // Enter ad break but not playing yet -> no Started.
        assertEquals(
            emptyList<AdPlaybackSignal.Event>(),
            s.onPlayerState(inAd = true, isPlaying = false),
        )

        // First frame / playback started -> Started.
        assertEquals(
            listOf(AdPlaybackSignal.Event.Started),
            s.onPlayerState(inAd = true, isPlaying = true),
        )
    }

    @Test
    fun `ends when leaving ad break`() {
        val s = AdPlaybackSignal()
        s.onPlayerState(inAd = true, isPlaying = false)
        s.onPlayerState(inAd = true, isPlaying = true) // started

        assertEquals(
            listOf(AdPlaybackSignal.Event.Ended),
            s.onPlayerState(inAd = false, isPlaying = false),
        )
    }

    @Test
    fun `error before start does not emit ended`() {
        val s = AdPlaybackSignal()
        s.onPlayerState(inAd = true, isPlaying = false)

        val events = s.onAdError(RuntimeException("boom"))
        assertEquals(1, events.size)
        assertEquals(true, events[0] is AdPlaybackSignal.Event.Error)
    }

    @Test
    fun `error after start emits error then ended`() {
        val s = AdPlaybackSignal()
        s.onPlayerState(inAd = true, isPlaying = false)
        s.onPlayerState(inAd = true, isPlaying = true) // started

        assertEquals(
            listOf(
                AdPlaybackSignal.Event.Error(null),
                AdPlaybackSignal.Event.Ended,
            ),
            s.onAdError(null),
        )
    }

    @Test
    fun `release ends if previously started`() {
        val s = AdPlaybackSignal()
        s.onPlayerState(inAd = true, isPlaying = true) // started

        assertEquals(
            listOf(AdPlaybackSignal.Event.Ended),
            s.onReleased(),
        )

        // Idempotent after release.
        assertEquals(emptyList<AdPlaybackSignal.Event>(), s.onReleased())
    }
}

