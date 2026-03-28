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
     *
     * @param adSkipOffsetMs When non-null, time from linear ad start when skip is allowed (from VAST).
     */
    fun playAd(mediaUri: String, adSkipOffsetMs: Long? = null)

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
    /** Resolved skip offset from VAST for the current linear ad; null if not skippable. */
    val adSkipOffsetMs: Long? = null,
)

