package tech.done.ads.sample

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.done.ads.core.AdsLoader
import tech.done.ads.player.PlayerCommandListener
import tech.done.ads.player.PlayerState
import tech.done.ads.player.media3.ExternalPlayerControllerHidingCommandListener

class ExternalPlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var playerView: PlayerView? = null
    private var pollJob: Job? = null

    private var inAd: Boolean = false
    private var savedContentItem: MediaItem? = null
    private var savedContentPositionMs: Long = 0L
    private var savedContentPlayWhenReady: Boolean = true

    private var setup: AdsLoader.ExternalSetup? = null
    private var adSkipOffsetMs: Long? = null
    private var hasStartedAds: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(SampleConfig.Urls.CONTENT_VIDEO))
            prepare()
            playWhenReady = true
        }

        val commands = object : PlayerCommandListener {
            override fun onPlayAdRequested(
                mediaUri: String,
                adSkipOffsetMs: Long?,
                simidInteractiveCreativeUrl: String?
            ) {
                if (!inAd) {
                    savedContentItem = player.currentMediaItem
                    savedContentPositionMs = player.currentPosition
                    savedContentPlayWhenReady = player.playWhenReady
                }
                inAd = true
                this@ExternalPlayerActivity.adSkipOffsetMs = adSkipOffsetMs
                player.setMediaItem(MediaItem.fromUri(mediaUri))
                player.prepare()
                player.playWhenReady = true
            }

            override fun onResumeContentRequested() {
                val item = savedContentItem
                inAd = false
                this@ExternalPlayerActivity.adSkipOffsetMs = null
                if (item != null) {
                    player.setMediaItem(item)
                    player.prepare()
                    player.seekTo(savedContentPositionMs)
                    player.playWhenReady = savedContentPlayWhenReady
                } else {
                    player.playWhenReady = savedContentPlayWhenReady
                }
            }

            override fun onPauseRequested() {
                player.pause()
            }

            override fun onPlayRequested() {
                player.play()
            }

            override fun onSeekingEnabledChanged(enabled: Boolean) {
            }
        }

        val libraryCommands = ExternalPlayerControllerHidingCommandListener(
            delegate = commands,
            playerViewProvider = { playerView },
        )

        setup = AdsLoader.createWithExternalPlayer(
            commands = libraryCommands,
            network = SampleNetworkLayer(this),
            debugLogging = true,
        ).also { it.adsLoader.addAdSdkEventListener(SampleAdsEventLogger()) }

        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    val s = setup ?: return
                    if (inAd) {
                        s.playerAdapter.notifyAdEnded()
                    } else {
                        s.playerAdapter.notifyContentEnded()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    setup?.playerAdapter?.notifyPlayerError(error)
                }
            },
        )

        startPolling()

        setContent {
            MaterialTheme {
                BackHandler {
                    if (inAd) {
                        finish()
                        return@BackHandler
                    }
                    val pv = playerView
                    if (pv != null && !pv.isControllerFullyVisible) {
                        pv.showController()
                    } else {
                        finish()
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                playerView = this
                                this.player = this@ExternalPlayerActivity.player
                                useController = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                // On TV/D-pad devices, give initial focus to player controls.
                                post { requestFocus() }
                            }
                        }
                    )

                    // Keep external-player sample behavior aligned with other scenario screens:
                    // open screen -> load ad tag URL -> start ad engine.
                    LaunchedEffect(Unit) {
                        if (hasStartedAds) return@LaunchedEffect
                        hasStartedAds = true
                        startAdsFromUrl()
                    }
                }
            }
        }
    }

    private fun startAdsFromUrl() {
        val l = setup?.adsLoader ?: return
        l.requestAds(SampleConfig.Urls.ADS_TAG)
        l.start()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                val s = setup ?: break
                val pos = player.currentPosition
                val dur = player.duration.takeIf { it > 0 }
                val state =
                    if (inAd) {
                        PlayerState(
                            isPlaying = player.isPlaying,
                            isInAd = true,
                            contentPositionMs = savedContentPositionMs,
                            contentDurationMs = null,
                            adPositionMs = pos,
                            adDurationMs = dur,
                            adSkipOffsetMs = adSkipOffsetMs,
                            isAdSkippable = adSkipOffsetMs != null,
                        )
                    } else {
                        PlayerState(
                            isPlaying = player.isPlaying,
                            isInAd = false,
                            contentPositionMs = pos,
                            contentDurationMs = dur,
                            adPositionMs = 0L,
                            adDurationMs = null,
                            adSkipOffsetMs = null,
                            isAdSkippable = false,
                        )
                    }

                s.playerAdapter.updateState(state)
                if (inAd) s.playerAdapter.notifyAdProgress(pos, dur)
                delay(250)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView = null
        runCatching { pollJob?.cancel() }
        runCatching { setup?.adsLoader?.release() }
        setup = null
        runCatching { player.release() }
    }
}

