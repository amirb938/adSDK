package tech.done.ads.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tech.done.ads.player.media3.ima.AdDisplayContainerView
import tech.done.ads.player.media3.ima.Media3AdsLoader

class ExternalPlayerActivity : ComponentActivity() {
    private companion object {
        private const val TEST_START_POSITION_MS = 50L * 60L * 1000L
    }

    private lateinit var player: ExoPlayer
    private var playerView: PlayerView? = null
    private var adsLoader: Media3AdsLoader? = null
    private var hasStartedAds: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(SampleConfig.Urls.CONTENT_VIDEO))
            prepare()
//            seekTo(TEST_START_POSITION_MS)
            playWhenReady = true
        }

        adsLoader = Media3AdsLoader.builder(this)
            .network(SampleNetworkLayer(this))
            .debugLogging(true)
            .build()
            .also {
                it.addAdSdkEventListener(SampleAdsEventLogger())
                it.setContentPlaybackController(
                    object : Media3AdsLoader.ContentPlaybackController {
                        override fun onPauseContentRequested() {
                            player.pause()
                        }

                        override fun onResumeContentRequested() {
                            player.play()
                            playerView?.hideController()
                        }

                        override fun onPauseRequested() {
                            player.pause()
                        }

                        override fun onPlayRequested() {
                            player.play()
                        }
                    },
                )
                it.setContentUi(
                    object : Media3AdsLoader.ContentUi {
                        override fun onAdStarted() {
                            // Host can update any ad-specific UI state here.
                        }

                        override fun onAdEnded() {
                            // Keep player controller hidden after ad-driven resume.
                            playerView?.hideController()
                        }
                    },
                )
            }

        setContent {
            MaterialTheme {
                BackHandler {
                    val inAd = adsLoader?.playerState?.value?.isInAd == true
                    val pv = playerView
                    if (!inAd && pv != null && !pv.isControllerFullyVisible) {
                        pv.showController()
                    } else {
                        finish()
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    playerView = this
                                    this.player = this@ExternalPlayerActivity.player
                                    adsLoader?.setAdMarkersContainerView(this)
                                }
                            },
                        )

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                AdDisplayContainerView(ctx).also { container ->
                                    adsLoader?.setAdDisplayContainer(container)
                                }
                            },
                        )
                    }

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
        val l = adsLoader ?: return
        l.requestAds(SampleConfig.Urls.ADS_TAG)
        l.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView = null
        runCatching { adsLoader?.release() }
        adsLoader = null
        runCatching { player.release() }
    }
}

