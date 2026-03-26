package tech.done.adsdk.player.media3.ima.internal

/**
 * Small, deterministic state machine that powers Media3AdsLoader's `isAdPlaying` signal.
 *
 * Requirements:
 * - Don't report "ad playing" merely because we entered an ad break.
 * - Flip to true when ad playback actually starts (first isPlaying=true while inAd).
 * - Keep true for the remainder of the ad break, even if playback pauses/buffers.
 * - Flip to false when the ad break ends, is skipped, errors (after starting), or is released.
 */
internal class AdPlaybackSignal {
    sealed interface Event {
        data object Started : Event
        data object Ended : Event
        data class Error(val throwable: Throwable?) : Event
    }

    private var inAd: Boolean = false
    private var hasStartedThisBreak: Boolean = false
    private var isPlayingSignal: Boolean = false

    fun onPlayerState(inAd: Boolean, isPlaying: Boolean): List<Event> {
        val events = ArrayList<Event>(1)

        // Leaving ad break always ends (if we were signaling).
        if (this.inAd && !inAd) {
            this.inAd = false
            this.hasStartedThisBreak = false
            if (isPlayingSignal) {
                isPlayingSignal = false
                events += Event.Ended
            }
            return events
        }

        // Entering ad break: don't start signaling until playback actually begins.
        if (!this.inAd && inAd) {
            this.inAd = true
            this.hasStartedThisBreak = false
            // do not emit Started yet
        }

        // Once in an ad break, the first isPlaying=true is considered "ad started".
        if (this.inAd && !hasStartedThisBreak && isPlaying) {
            hasStartedThisBreak = true
            if (!isPlayingSignal) {
                isPlayingSignal = true
                events += Event.Started
            }
        }

        return events
    }

    fun onAdError(t: Throwable?): List<Event> {
        val events = ArrayList<Event>(2)
        events += Event.Error(t)

        // If we had already started, an error ends the ad from the host UI perspective.
        if (isPlayingSignal) {
            isPlayingSignal = false
            events += Event.Ended
        }

        return events
    }

    fun onReleased(): List<Event> {
        // Release is always a cleanup boundary: if we were signaling, end it.
        val events = if (isPlayingSignal) listOf(Event.Ended) else emptyList()
        inAd = false
        hasStartedThisBreak = false
        isPlayingSignal = false
        return events
    }
}

