package tech.done.ads.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

class ExternalPlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var pollJob: Job? = null

    private var inAd: Boolean = false
    private var savedContentItem: MediaItem? = null
    private var savedContentPositionMs: Long = 0L
    private var savedContentPlayWhenReady: Boolean = true

    private var setup: AdsLoader.ExternalSetup? = null
    private var adSkipOffsetMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(SampleConfig.Urls.CONTENT_VIDEO))
            prepare()
            playWhenReady = true
        }

        val commands = object : PlayerCommandListener {
            override fun onPlayAdRequested(mediaUri: String, adSkipOffsetMs: Long?, simidInteractiveCreativeUrl: String?) {
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

        setup = AdsLoader.createWithExternalPlayer(
            commands = commands,
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
                BackHandler { finish() }
                Surface(modifier = Modifier.fillMaxSize()) {
                    var started by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> PlayerView(ctx).apply { this.player = this@ExternalPlayerActivity.player } },
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = "ExternalPlayerActivity", style = MaterialTheme.typography.titleMedium)
                            Button(
                                enabled = !started,
                                onClick = { started = true },
                            ) {
                                Text("Start Ads (VMAP asset)")
                            }
                        }

                        LaunchedEffect(started) {
                            if (!started) return@LaunchedEffect
                            val xml = assets.open(SampleConfig.Assets.VMAP).bufferedReader().use { it.readText() }
                            val l = setup?.adsLoader ?: return@LaunchedEffect
                            l.requestAdsFromVMAPXml(xml)
                            l.start()
                        }
                    }
                }
            }
        }
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
        runCatching { pollJob?.cancel() }
        runCatching { setup?.adsLoader?.release() }
        setup = null
        runCatching { player.release() }
    }
}

