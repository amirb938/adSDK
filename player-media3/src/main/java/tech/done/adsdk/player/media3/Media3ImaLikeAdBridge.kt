package tech.done.adsdk.player.media3

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import tech.done.adsdk.player.media3.internal.AdSdkDebugLog

/**
 * IMA-like integration for Media3:
 * - User keeps their own content ExoPlayer + PlayerView.
 * - SDK creates a separate ExoPlayer + PlayerView for ads.
 * - SDK overlays the ad view on top of the content view during ad playback and suppresses content controllers.
 *
 * This keeps controller-hiding behavior inside the SDK (not in app code).
 */
class Media3ImaLikeAdBridge(
    private val contentPlayer: ExoPlayer,
    private val contentPlayerView: PlayerView,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 250L,
) {
    private val logTag = "Media3ImaBridge"

    private data class PendingAd(val uri: String, val adSkipOffsetMs: Long?)

    private var pendingAd: PendingAd? = null
    private var isAttachingOverlay: Boolean = false

    // Force main looper to avoid creating players on a transient thread/looper (Compose can create Views during setup).
    private val adPlayer: ExoPlayer = ExoPlayer.Builder(contentPlayerView.context)
        .setLooper(Looper.getMainLooper())
        .build()
    private val adPlayerView: PlayerView = PlayerView(contentPlayerView.context).apply {
        player = adPlayer
        useController = false
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // We intentionally do NOT re-parent contentPlayerView (SurfaceView-based TVs often break).
    // Instead, we overlay adPlayerView as a sibling on top of the contentPlayerView.
    private var overlayParent: ViewGroup? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listeners = LinkedHashSet<PlayerListener>()

    private var pollJob: Job? = null

    private var savedContentItem: MediaItem? = null
    private var savedContentPositionMs: Long = 0L
    private var savedContentPlayWhenReady: Boolean = true

    val playerAdapter: PlayerAdapter = object : PlayerAdapter {
        override val state: StateFlow<PlayerState> = this@Media3ImaLikeAdBridge.state

        override fun addListener(listener: PlayerListener) {
            listeners += listener
        }

        override fun removeListener(listener: PlayerListener) {
            listeners -= listener
        }

        override fun setSeekingEnabled(enabled: Boolean) {
            // SDK-level policy hook; UI-side enforcement is done in the host app.
        }

        override fun playAd(mediaUri: String, adSkipOffsetMs: Long?) {
            startAd(mediaUri, adSkipOffsetMs)
        }

        override fun resumeContent() {
            endAdAndResumeContent()
        }

        override fun pause() {
            if (_state.value.isInAd) adPlayer.pause() else contentPlayer.pause()
        }

        override fun play() {
            if (_state.value.isInAd) adPlayer.play() else contentPlayer.play()
        }
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
                AdSdkDebugLog.d(logTag, "ad ended (STATE_ENDED)")
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

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            AdSdkDebugLog.d(logTag, "contentPlayerView attached")
            tryAttachOverlayAndMaybeStartPending()
        }

        override fun onViewDetachedFromWindow(v: View) {
            // Best-effort cleanup.
            runCatching { detachOverlay() }
        }
    }

    init {
        contentPlayer.addListener(contentListener)
        adPlayer.addListener(adListener)
        // In Compose/AndroidView the view may not be attached yet. Defer overlay attachment.
        contentPlayerView.addOnAttachStateChangeListener(attachListener)
        tryAttachOverlayAndMaybeStartPending()
        startPolling()
    }

    fun release() {
        runCatching { pollJob?.cancel() }
        runCatching { contentPlayer.removeListener(contentListener) }
        runCatching { adPlayer.removeListener(adListener) }
        runCatching { contentPlayerView.removeOnAttachStateChangeListener(attachListener) }
        runCatching { detachOverlay() }
        runCatching { adPlayer.release() }
    }

    private fun ensureOverlayAttachedOrNull(): ViewGroup? {
        if (overlayParent != null) return overlayParent
        if (isAttachingOverlay) return null

        val parent = contentPlayerView.parent as? ViewGroup ?: return null

        isAttachingOverlay = true
        try {
            // Ensure ad view is detached from any previous parent.
            (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView)

            // Add ad view on top as a sibling overlay.
            val index = parent.indexOfChild(contentPlayerView).takeIf { it >= 0 } ?: (parent.childCount - 1)
            parent.addView(adPlayerView, index + 1)

            overlayParent = parent
            AdSdkDebugLog.d(logTag, "overlay attached as sibling parent=${parent::class.java.simpleName}")
            return parent
        } finally {
            isAttachingOverlay = false
        }
    }

    private fun tryAttachOverlayAndMaybeStartPending() {
        ensureOverlayAttachedOrNull() ?: return
        // If overlay attached and an ad was requested before attach, start it now.
        val p = pendingAd ?: return
        pendingAd = null
        AdSdkDebugLog.d(logTag, "starting pending ad uri=${p.uri}")
        startAdInternal(p.uri, p.adSkipOffsetMs)
    }

    private fun detachOverlay() {
        (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView)
        overlayParent = null
    }

    private fun startAd(mediaUri: String, adSkipOffsetMs: Long?) {
        // If view not attached yet (Compose), defer starting ad until attach.
        if (ensureOverlayAttachedOrNull() == null) {
            pendingAd = PendingAd(mediaUri, adSkipOffsetMs)
            AdSdkDebugLog.d(logTag, "deferring ad start until view attached uri=$mediaUri")
            return
        }
        startAdInternal(mediaUri, adSkipOffsetMs)
    }

    private fun startAdInternal(mediaUri: String, adSkipOffsetMs: Long?) {
        if (!_state.value.isInAd) {
            savedContentItem = contentPlayer.currentMediaItem
            savedContentPositionMs = contentPlayer.currentPosition
            savedContentPlayWhenReady = contentPlayer.playWhenReady
        }

        // Stop content playback.
        contentPlayer.playWhenReady = false
        contentPlayer.pause()

        // Suppress content controllers at SDK level.
        suppressContentControllers(true)

        // Show ad view and start ad playback on separate player.
        adPlayerView.visibility = View.VISIBLE
        adPlayerView.bringToFront()

        _state.value = _state.value.copy(
            isInAd = true,
            adPositionMs = 0L,
            adDurationMs = null,
            isPlaying = false,
            adSkipOffsetMs = adSkipOffsetMs,
        )
        AdSdkDebugLog.d(logTag, "startAd uri=$mediaUri savedPosMs=$savedContentPositionMs")

        adPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        adPlayer.prepare()
        adPlayer.playWhenReady = true
    }

    private fun endAdAndResumeContent() {
        if (!_state.value.isInAd) return
        AdSdkDebugLog.d(logTag, "endAdAndResumeContent()")

        // Hide ad view and stop ad player.
        adPlayer.playWhenReady = false
        adPlayer.stop()
        adPlayer.clearMediaItems()
        adPlayerView.visibility = View.GONE

        // Re-enable content controllers.
        suppressContentControllers(false)

        // Resume content.
        val item = savedContentItem
        if (item != null) {
            contentPlayer.setMediaItem(item)
            contentPlayer.prepare()
            contentPlayer.seekTo(savedContentPositionMs)
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        } else {
            // If we couldn't snapshot content item, just resume.
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        }

        _state.value = _state.value.copy(isInAd = false, adPositionMs = 0L, adDurationMs = null, adSkipOffsetMs = null)
    }

    private fun suppressContentControllers(inAd: Boolean) {
        // Hard-disable any content controllers while ads are showing.
        contentPlayerView.useController = !inAd
        if (inAd) {
            contentPlayerView.hideController()
            contentPlayerView.setControllerAutoShow(false)
            contentPlayerView.setControllerHideOnTouch(false)
            // Swallow touches/DPAD on the content view; ad UI should be the only visible controls.
            contentPlayerView.setOnTouchListener { _, _ -> true }
            contentPlayerView.isFocusable = false
            contentPlayerView.isClickable = false
        } else {
            contentPlayerView.setOnTouchListener(null)
            contentPlayerView.setControllerAutoShow(true)
            contentPlayerView.setControllerHideOnTouch(true)
            contentPlayerView.isFocusable = true
            contentPlayerView.isClickable = true
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

