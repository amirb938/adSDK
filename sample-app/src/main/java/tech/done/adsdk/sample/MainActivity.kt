package tech.done.adsdk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tech.done.adsdk.player.media3.ima.AdDisplayContainerView
import tech.done.adsdk.player.media3.ima.Media3AdsLoader
import tech.done.adsdk.tracking.RetryingTrackingEngine
import tech.done.adsdk.ui.compose.AdOverlay
import tech.done.adsdk.ui.compose.AdUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable ultra-verbose AdSDK logs (println -> Logcat).
        System.setProperty("adsdk.debug", "true")

        val exo = ExoPlayer.Builder(this).build().apply {
            // Sample content
            setMediaItem(androidx.media3.common.MediaItem.fromUri("https://dls5.iran-gamecenter-host.com/DonyayeSerial/series/Vikings/Dubbed/S02/480p.BluRay/Vikings.S02E09.480p.Farsi.Dubbed.DonyayeSerial.mkv"))
            prepare()
            playWhenReady = true
        }

        val network = SampleNetworkLayer(this)
        val tracking = RetryingTrackingEngine(network)
        val adsLoader = Media3AdsLoader(network = network, tracking = tracking, scope = scope)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Keep the ad overlay UI driven by SDK engine/player state.
                    // For sample simplicity we reuse our existing AdOverlay from player state, but the IMA-like integration
                    // focuses on the "view + loader" API (no app-side controller hacks).
                    val playerState = remember { tech.done.adsdk.player.PlayerState() }

                    val adUi = if (playerState.isInAd) {
                        val remaining = playerState.adDurationMs?.let { dur ->
                            val remMs = (dur - playerState.adPositionMs).coerceAtLeast(0L)
                            ceil(remMs / 1000.0).toInt()
                        }
                        AdUiState(
                            visible = true,
                            canSkip = playerState.adPositionMs >= 3_000L,
                            skipInSeconds = if (playerState.adPositionMs >= 3_000L) null else ceil((3_000L - playerState.adPositionMs).coerceAtLeast(0L) / 1000.0).toInt(),
                            remainingSeconds = remaining,
                            adIndex = 1,
                            adCount = 1,
                        )
                    } else AdUiState(visible = false)

                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exo
                                    adsLoader.setPlayer(exo)
                                    adsLoader.setPlayerView(this)
                                }
                            },
                        )

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                AdDisplayContainerView(ctx).also { adsLoader.setAdDisplayContainer(it) }
                            },
                        )

                        AdOverlay(
                            state = adUi,
                            onSkip = {
                                // Minimal skip: resume content immediately.
                                // In full IMA-like API this would call adsLoader / ad manager.
                            },
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (System.getProperty("adsdk.debug") == "true") {
                            println("AdSDK/Sample D loading VMAP from res/raw/sample_vmap.xml")
                        }
                        val vmapXml = resources.openRawResource(R.raw.sample_vmap).bufferedReader().use { it.readText() }
                        adsLoader.requestAdsFromVmapXml(vmapXml)
                        adsLoader.start()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Engine expects background pause during ads.
        // For sample: pause player; adapter will keep state updated.
    }
}

