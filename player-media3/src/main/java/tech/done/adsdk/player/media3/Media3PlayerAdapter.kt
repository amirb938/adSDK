package tech.done.adsdk.player.media3

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import tech.done.adsdk.player.PlayerAdapter
import tech.done.adsdk.player.PlayerListener
import tech.done.adsdk.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Media3PlayerAdapter(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 250L,
) : PlayerAdapter {

    private val listeners = LinkedHashSet<PlayerListener>()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var contentItem: MediaItem? = null
    private var contentPositionMs: Long = 0L

    private var isSeekingEnabled: Boolean = true
    private var pollJob: Job? = null

    private val media3Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && !_state.value.isInAd) {
                listeners.forEach { it.onContentEnded() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlayerError(error) }
        }
    }

    init {
        player.addListener(media3Listener)
        startPolling()
    }

    override fun addListener(listener: PlayerListener) {
        listeners += listener
    }

    override fun removeListener(listener: PlayerListener) {
        listeners -= listener
    }

    override fun setSeekingEnabled(enabled: Boolean) {
        isSeekingEnabled = enabled
        // Enforcement is UI/controller-side; adapter stores the policy.
    }

    override fun playAd(mediaUri: String) {
        if (!_state.value.isInAd) {
            contentItem = player.currentMediaItem
            contentPositionMs = player.currentPosition
        }

        _state.value = _state.value.copy(isInAd = true, adPositionMs = 0L, adDurationMs = null)

        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.playWhenReady = true
    }

    override fun resumeContent() {
        val item = contentItem ?: return
        _state.value = _state.value.copy(isInAd = false, adPositionMs = 0L, adDurationMs = null)

        player.setMediaItem(item)
        player.prepare()
        player.seekTo(contentPositionMs)
        player.playWhenReady = true
    }

    override fun pause() {
        player.pause()
    }

    override fun play() {
        player.play()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val inAd = _state.value.isInAd
                if (inAd) {
                    val pos = player.currentPosition
                    val dur = player.duration.takeIf { it > 0 }
                    _state.value = _state.value.copy(adPositionMs = pos, adDurationMs = dur)
                    listeners.forEach { it.onAdProgress(pos, dur) }
                } else {
                    val pos = player.currentPosition
                    val dur = player.duration.takeIf { it > 0 }
                    _state.value = _state.value.copy(contentPositionMs = pos, contentDurationMs = dur)
                }
                delay(pollIntervalMs)
            }
        }
    }
}

