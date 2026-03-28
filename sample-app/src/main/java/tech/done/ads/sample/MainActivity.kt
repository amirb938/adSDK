package tech.done.ads.sample

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
import kotlinx.coroutines.cancel
import tech.done.ads.player.media3.ima.AdDisplayContainerView
import tech.done.ads.player.media3.ima.Media3AdsLoader
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var scope = MainScope()
    private var exo: ExoPlayer? = null
    private var adsLoader: Media3AdsLoader? = null
    private var didReleaseOnStop: Boolean = false
    private val adSdkEventLogger = SampleAdsEventLogger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val adSdkDebugLogging = true

        val exo = ExoPlayer.Builder(this).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri("https://dls5.iran-gamecenter-host.com/DonyayeSerial/series/Vikings/Dubbed/S02/480p.BluRay/Vikings.S02E09.480p.Farsi.Dubbed.DonyayeSerial.mkv"))
            prepare()
            playWhenReady = true
        }
        this.exo = exo

        val adsLoader =
            Media3AdsLoader(context = this, scope = scope, debugLogging = adSdkDebugLogging)
        adsLoader.addAdSdkEventListener(adSdkEventLogger)
        this.adsLoader = adsLoader

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
                                    adsLoader.setAdMarkersContainerView(this)
                                }
                            },
                        )

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                AdDisplayContainerView(ctx).also {
                                    adsLoader.setAdDisplayContainer(it)
                                }
                            },
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (adSdkDebugLogging) {
                            Timber.tag("AdSDK/Sample").d("loading VMAP from assets/sample_vmap.xml")
                        }
                        val vmapXml =
                            assets.open("sample_vmap.xml").bufferedReader().use { it.readText() }
                        adsLoader.requestAdsFromVMAPXml(vmapXml)
                        adsLoader.start()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        runCatching { adsLoader?.release() }
        runCatching { exo?.stop() }
        runCatching { exo?.clearMediaItems() }
        runCatching { exo?.release() }
        runCatching { scope.cancel() }

        adsLoader = null
        exo = null
        didReleaseOnStop = true
    }

    override fun onStart() {
        super.onStart()
        if (didReleaseOnStop) {
            didReleaseOnStop = false
            scope = MainScope()
            recreate()
        }
    }
}
