package tech.done.adsdk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.MainScope
import tech.done.adsdk.player.media3.ima.AdDisplayContainerView
import tech.done.adsdk.player.media3.ima.Media3AdsLoader
import tech.done.adsdk.tracking.RetryingTrackingEngine

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
                                AdDisplayContainerView(ctx).also {
                                    adsLoader.setAdDisplayContainer(
                                        it
                                    )
                                }
                            },
                        )

                        // No ad UI in the app. SDK renders overlay inside AdDisplayContainerView (IMA-like).
                    }

                    LaunchedEffect(Unit) {
                        if (System.getProperty("adsdk.debug") == "true") {
                            println("AdSDK/Sample D loading VMAP from assets/sample_vmap.xml")
                        }
//                        val adTagUri = listOf(
//                            "https://ads.kianoosh.dev/ads/ads",
//                            "https://ads.kianoosh.dev/ads/vast",
//                        )[Random.nextInt(1)]
//                        adsLoader.requestAds(adTagUri)
                        val vmapXml =
                            assets.open("sample_vmap.xml").bufferedReader().use { it.readText() }
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

