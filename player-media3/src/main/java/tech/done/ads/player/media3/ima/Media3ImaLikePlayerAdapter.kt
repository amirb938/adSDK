package tech.done.ads.player.media3.ima

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import tech.done.ads.player.SimidEventListener
import tech.done.ads.player.PlayerAdapter
import tech.done.ads.player.PlayerListener
import tech.done.ads.player.PlayerState
import tech.done.ads.player.media3.ima.internal.AdOverlayView
import timber.log.Timber
import java.util.UUID


internal class Media3ImaLikePlayerAdapter(
    private val contentPlayer: ExoPlayer,
    private val adDisplayContainer: AdDisplayContainerView,
    private val scope: CoroutineScope,
    private val contentUi: ContentUi? = null,
    private val pollIntervalMs: Long = 250L,
    private val uiConfig: AdSdkUiConfig? = null,
    private val showBuiltInAdOverlay: Boolean = true,
) {
    private val logTag = "Player/Media3SIMID"
    private var simidReadySessionId: String? = null
    private var simidSessionId: String? = null
    private var simidHandshakeJob: Job? = null
    private var lastSimidTimeUpdateSecond: Long? = null


    interface ContentUi {
        fun onAdStarted()
        fun onAdEnded()
    }

    @SuppressLint("UnsafeOptInUsageError")
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

    private val adOverlayView: AdOverlayView = AdOverlayView(adDisplayContainer.context).apply {
        onSkip = { endAdAndResumeContent() }
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
        override fun addListener(listener: PlayerListener) {
            listeners += listener
        }

        override fun removeListener(listener: PlayerListener) {
            listeners -= listener
        }

        override fun setSeekingEnabled(enabled: Boolean) {}
        override fun playAd(mediaUri: String, adSkipOffsetMs: Long?, simidInteractiveCreativeUrl: String?) =
            startAd(mediaUri, adSkipOffsetMs, simidInteractiveCreativeUrl)

        override fun resumeContent() = endAdAndResumeContent()
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
                listeners.forEach { it.onAdEnded() }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_state.value.isInAd) listeners.forEach { it.onPlayerError(error) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value.isInAd) _state.value = _state.value.copy(isPlaying = isPlaying)
            if (_state.value.isInAd && simidSessionId != null) {
                val t = JSONObject().put("currentTime", adPlayer.currentPosition / 1000.0).toString()
                adDisplayContainer.sendSimidMessage(if (isPlaying) "play" else "pause", t)
            }
        }
    }

    init {
        contentPlayer.addListener(contentListener)
        adPlayer.addListener(adListener)
        if (showBuiltInAdOverlay) {
            uiConfig?.let { adOverlayView.applyUiConfig(it) }
        }
        ensureAdViewsAdded()
        adDisplayContainer.setSimidEventListener(
            object : SimidEventListener {
                override fun onSimidReady(sessionId: String) {
                    simidReadySessionId = sessionId
                }

                override fun onSimidAction(sessionId: String, type: String, args: JSONObject?) {
                    when (type.lowercase()) {
                        "requestpause" -> playerAdapter.pause()
                        "requestplay" -> playerAdapter.play()
                        "requeststop", "requestskip" -> playerAdapter.resumeContent()
                        "click" -> {
                            val url = args?.optString("url")?.takeIf { it.isNotBlank() } ?: return
                            openClickUrl(url)
                        }
                    }
                }
            },
        )
        startPolling()
    }

    fun release() {
        runCatching { pollJob?.cancel() }
        runCatching { simidHandshakeJob?.cancel() }
        runCatching { contentPlayer.removeListener(contentListener) }
        runCatching { adPlayer.removeListener(adListener) }
        runCatching { adDisplayContainer.setSimidEventListener(null) }
        runCatching { adDisplayContainer.hideSimidCreative() }
        runCatching { (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView) }
        runCatching { (adOverlayView.parent as? ViewGroup)?.removeView(adOverlayView) }
        runCatching { adPlayer.release() }
    }

    private fun ensureAdViewsAdded() {
        if (adPlayerView.parent != adDisplayContainer) {
            (adPlayerView.parent as? ViewGroup)?.removeView(adPlayerView)
            adDisplayContainer.addView(adPlayerView)
        }

        if (showBuiltInAdOverlay && adOverlayView.parent != adDisplayContainer) {
            (adOverlayView.parent as? ViewGroup)?.removeView(adOverlayView)
            adDisplayContainer.addView(adOverlayView)
        }
    }

    private fun startAd(mediaUri: String, adSkipOffsetMs: Long?, simidInteractiveCreativeUrl: String?) {
        ensureAdViewsAdded()
        if (adPlayerView.player !== adPlayer) {
            adPlayerView.player = adPlayer
        }

        if (!_state.value.isInAd) {
            savedContentItem = contentPlayer.currentMediaItem
            savedContentPositionMs = contentPlayer.currentPosition
            savedContentPlayWhenReady = contentPlayer.playWhenReady
        }

        contentPlayer.playWhenReady = false
        contentPlayer.pause()

        contentUi?.onAdStarted()

        adPlayerView.visibility = View.VISIBLE
        if (showBuiltInAdOverlay) {
            adOverlayView.setVisible(true)
        }
        _state.value = _state.value.copy(
            isInAd = true,
            adPositionMs = 0L,
            adDurationMs = null,
            isPlaying = false,
            adSkipOffsetMs = adSkipOffsetMs,
            isAdSkippable = adSkipOffsetMs != null,
        )

        adPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        adPlayer.prepare()
        adPlayer.playWhenReady = false

        if (!simidInteractiveCreativeUrl.isNullOrBlank()) {
            simidSessionId = UUID.randomUUID().toString()
            simidReadySessionId = null
            lastSimidTimeUpdateSecond = null
            adDisplayContainer.loadSimidCreative(simidInteractiveCreativeUrl, simidSessionId!!)
            // Keep built-in skip / ad chrome above the SIMID layer when enabled.
            if (showBuiltInAdOverlay) {
                adOverlayView.bringToFront()
            }
            simidHandshakeJob?.cancel()
            simidHandshakeJob = scope.launch {
                val sid = simidSessionId ?: return@launch
                val ready = withTimeoutOrNull(10_000L) {
                    while (simidReadySessionId != sid) {
                        delay(50)
                        if (!_state.value.isInAd) return@withTimeoutOrNull false
                    }
                    true
                } == true

                if (!ready) {
                    Timber.tag(logTag).w("SIMID ready timeout; fallback to linear ad playback sid=%s", sid)
                }
                adPlayer.playWhenReady = true
            }
            return
        }
        adPlayer.playWhenReady = true
    }

    private fun endAdAndResumeContent() {
        if (!_state.value.isInAd) return

        simidHandshakeJob?.cancel()
        simidHandshakeJob = null
        simidSessionId = null
        simidReadySessionId = null
        lastSimidTimeUpdateSecond = null
        runCatching {
            adDisplayContainer.sendSimidMessage("ended", JSONObject().put("currentTime", adPlayer.currentPosition / 1000.0).toString())
        }
        adDisplayContainer.hideSimidCreative()

        adPlayer.playWhenReady = false
        adPlayer.stop()
        adPlayer.clearMediaItems()
        runCatching { adPlayerView.player = null }
        runCatching { adPlayer.clearVideoSurface() }
        adPlayerView.visibility = View.GONE
        if (showBuiltInAdOverlay) {
            adOverlayView.setVisible(false)
        }

        contentUi?.onAdEnded()

        val item = savedContentItem
        if (item != null) {
            contentPlayer.setMediaItem(item)
            contentPlayer.prepare()
            contentPlayer.seekTo(savedContentPositionMs)
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        } else {
            contentPlayer.playWhenReady = savedContentPlayWhenReady
        }

        _state.value = _state.value.copy(
            isInAd = false,
            adPositionMs = 0L,
            adDurationMs = null,
            adSkipOffsetMs = null,
            isAdSkippable = false,
        )
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

                    // SIMID outgoing timeUpdate.
                    if (simidSessionId != null) {
                        val sec = pos / 1000L
                        if (lastSimidTimeUpdateSecond != sec) {
                            lastSimidTimeUpdateSecond = sec
                            val args = JSONObject().put("currentTime", pos / 1000.0).toString()
                            adDisplayContainer.sendSimidMessage("timeUpdate", args)
                        }
                    }

                    if (showBuiltInAdOverlay) {
                        adOverlayView.render(
                            inAd = true,
                            adPositionMs = pos,
                            adDurationMs = dur,
                            skipOffsetMs = _state.value.adSkipOffsetMs,
                        )
                    }
                    listeners.forEach { it.onAdProgress(pos, dur) }
                } else {
                    val pos = contentPlayer.currentPosition
                    val dur = contentPlayer.duration.takeIf { it > 0 }
                    _state.value =
                        _state.value.copy(contentPositionMs = pos, contentDurationMs = dur)
                    if (showBuiltInAdOverlay) {
                        adOverlayView.render(
                            inAd = false,
                            adPositionMs = 0L,
                            adDurationMs = null,
                            skipOffsetMs = null,
                        )
                    }
                }
                delay(pollIntervalMs)
            }
        }
    }

    private fun openClickUrl(url: String) {
        runCatching {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val ctx = adDisplayContainer.context
            if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            Timber.tag(logTag).d("opened click url=%s", url)
        }.onFailure { t ->
            if (t is ActivityNotFoundException) {
                Timber.tag(logTag).w(t, "no activity found for click url=%s", url)
            } else {
                Timber.tag(logTag).e(t, "failed to open click url=%s", url)
            }
        }
    }
}

