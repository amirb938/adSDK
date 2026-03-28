package tech.done.ads.player.media3.ima.internal


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

        if (this.inAd && !inAd) {
            this.inAd = false
            this.hasStartedThisBreak = false
            if (isPlayingSignal) {
                isPlayingSignal = false
                events += Event.Ended
            }
            return events
        }

        if (!this.inAd && inAd) {
            this.inAd = true
            this.hasStartedThisBreak = false
        }

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

        if (isPlayingSignal) {
            isPlayingSignal = false
            events += Event.Ended
        }

        return events
    }

    fun onReleased(): List<Event> {
        val events = if (isPlayingSignal) listOf(Event.Ended) else emptyList()
        inAd = false
        hasStartedThisBreak = false
        isPlayingSignal = false
        return events
    }
}

