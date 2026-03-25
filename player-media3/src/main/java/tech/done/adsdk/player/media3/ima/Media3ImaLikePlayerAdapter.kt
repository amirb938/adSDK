package tech.done.adsdk.player.media3.ima

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.done.adsdk.player.PlayerAdapter
import tech.done.adsdk.player.PlayerListener
import tech.done.adsdk.player.PlayerState

/**
 * Bridges SDK core PlayerAdapter to:
 * - user content ExoPlayer + PlayerView
 * - SDK-owned ad ExoPlayer + PlayerView rendered inside AdDisplayContainerView
 *
 * No re-parenting of the content view is performed (TV safe).
 */
internal class Media3ImaLikePlayerAdapter(
    private val contentPlayer: ExoPlayer,
    private val contentPlayerView: PlayerView,
    private val adDisplayContainer: AdDisplayContainerView,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 250L,
) {
    private val adPlayer: ExoPlayer = ExoPlayer.Builder(adDisplayContainer.context)
        .setLooper(Looper.getMainLooper())
        .build()

    private val adPlayerView: PlayerView = PlayerView(adDisplayContainer.context).apply {
        player = adPlayer
        useController = false
        visibility = View.GONE
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listeners = LinkedHashSet<PlayerListener>()

    private var pollJob: Job? = null

    private var savedContentItem: MediaItem? = null
    private var savedContentPositionMs: Long = 0L
    private var savedContentPlayWhenReady: Boolean = true

    val playerAdapter: PlayerAdapter = object : PlayerAdapter {
        override val state: StateFlow<PlayerState> = this@Media3ImaLikePlayerAdapter.state
        override fun addListener(listener: PlayerListener) { listeners += listener }
        override fun removeListener(listener: PlayerListener) { listeners -= listener }
        override fun setSeekingEnabled(enabled: Boolean) { /* UI-side policy */ }
        override fun playAd(mediaUri: String) = startAd(mediaUri)
        override fun resumeContent() = endAdAndResumeContent()
        override fun pause() { if (_state.value.isInAd) adPlayer.pause() else contentPlayer.pause() }
        override fun play() { if (_state.value.isInAd) adPlayer.play() else contentPlayer.play() }
    }

    private val contentListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && !_state.value.isInAd) {
                listeners.forEach { it.onContentEnded() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!_state.value.isInAd) listeners.forEach { it.onPlayerError(error) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!_state.value.isInAd) _state.value = _state.value.copy(isPlaying = isPlaying)
        }
    }

    private val adListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.isInAd) {
                listeners.forEach { it.onAdEnded() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_state.value.isInAd) listeners.forEach { it.onPlayerError(error) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value.isInAd) _state.value = _state.value.copy(isPlaying = isPlaying)
        }
    }

    init {
        contentPlayer.addListener(contentListener)
        adPlayer.addListener(adListener)
        ensureAdViewAdded()
        startPolling()
    }

    fun release() {
        runCatching { pollJob?.cancel() }
        runCatching { contentPlayer.removeListener(contentListener) }
        runCatching { adPlayer.removeListener(adListener) }
        runCatching { (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView) }
        runCatching { adPlayer.release() }
    }

    private fun ensureAdViewAdded() {
        if (adPlayerView.parent == adDisplayContainer) return
        (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView)
        adDisplayContainer.addView(adPlayerView)
    }

    private fun startAd(mediaUri: String) {
        ensureAdViewAdded()

        if (!_state.value.isInAd) {
            savedContentItem = contentPlayer.currentMediaItem
            savedContentPositionMs = contentPlayer.currentPosition
            savedContentPlayWhenReady = contentPlayer.playWhenReady
        }

        // Stop content playback.
        contentPlayer.playWhenReady = false
        contentPlayer.pause()

        suppressContentControllers(true)

        adPlayerView.visibility = View.VISIBLE
        _state.value = _state.value.copy(isInAd = true, adPositionMs = 0L, adDurationMs = null, isPlaying = false)

        adPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        adPlayer.prepare()
        adPlayer.playWhenReady = true
    }

    private fun endAdAndResumeContent() {
        if (!_state.value.isInAd) return

        adPlayer.playWhenReady = false
        adPlayer.stop()
        adPlayer.clearMediaItems()
        adPlayerView.visibility = View.GONE

        suppressContentControllers(false)

        val item = savedContentItem
        if (item != null) {
            contentPlayer.setMediaItem(item)
            contentPlayer.prepare()
            contentPlayer.seekTo(savedContentPositionMs)
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        } else {
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        }

        _state.value = _state.value.copy(isInAd = false, adPositionMs = 0L, adDurationMs = null)
    }

    private fun suppressContentControllers(inAd: Boolean) {
        contentPlayerView.useController = !inAd
        if (inAd) {
            contentPlayerView.hideController()
            contentPlayerView.setControllerAutoShow(false)
            contentPlayerView.setControllerHideOnTouch(false)
            contentPlayerView.setOnTouchListener { _, _ -> true }
        } else {
            contentPlayerView.setOnTouchListener(null)
            contentPlayerView.setControllerAutoShow(true)
            contentPlayerView.setControllerHideOnTouch(true)
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val inAd = _state.value.isInAd
                if (inAd) {
                    val pos = adPlayer.currentPosition
                    val dur = adPlayer.duration.takeIf { it > 0 }
                    _state.value = _state.value.copy(adPositionMs = pos, adDurationMs = dur)
                    listeners.forEach { it.onAdProgress(pos, dur) }
                } else {
                    val pos = contentPlayer.currentPosition
                    val dur = contentPlayer.duration.takeIf { it > 0 }
                    _state.value = _state.value.copy(contentPositionMs = pos, contentDurationMs = dur)
                }
                delay(pollIntervalMs)
            }
        }
    }
}

