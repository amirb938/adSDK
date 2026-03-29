package tech.done.ads.player

import kotlinx.coroutines.flow.StateFlow

interface PlayerAdapter {
    val state: StateFlow<PlayerState>

    fun addListener(listener: PlayerListener)
    fun removeListener(listener: PlayerListener)

    fun setSeekingEnabled(enabled: Boolean)

    fun playAd(
        mediaUri: String,
        adSkipOffsetMs: Long? = null,
        simidInteractiveCreativeUrl: String? = null,
    )

    fun resumeContent()

    fun pause()
    fun play()
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isInAd: Boolean = false,
    val contentPositionMs: Long = 0L,
    val contentDurationMs: Long? = null,
    val adPositionMs: Long = 0L,
    val adDurationMs: Long? = null,
    val adSkipOffsetMs: Long? = null,
    val isAdSkippable: Boolean = false,
)

