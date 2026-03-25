package tech.done.adsdk.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Player abstraction used by the SDK core.
 *
 * Pure Kotlin (no Android types). Platform/player-specific adapters live in other modules.
 */
interface PlayerAdapter {
    val state: StateFlow<PlayerState>

    fun addListener(listener: PlayerListener)
    fun removeListener(listener: PlayerListener)

    /**
     * For SDK policy enforcement (e.g., disabling seeks during ads).
     */
    fun setSeekingEnabled(enabled: Boolean)

    /**
     * Request playback of an ad media URI. Implementation decides whether this is
     * a separate player instance, a transient media item, etc.
     */
    fun playAd(mediaUri: String)

    /**
     * Resume main content after an ad break (or after ad failure).
     */
    fun resumeContent()

    /**
     * Pause / resume used for app backgrounding coordination.
     */
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
)

