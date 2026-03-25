package tech.done.adsdk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tech.done.adsdk.core.DefaultAdEngine
import tech.done.adsdk.parser.impl.VastPullParser
import tech.done.adsdk.parser.impl.VmapPullParser
import tech.done.adsdk.player.media3.Media3PlayerAdapter
import tech.done.adsdk.scheduler.VmapScheduler
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

        val exo = ExoPlayer.Builder(this).build().apply {
            // Sample content
            setMediaItem(androidx.media3.common.MediaItem.fromUri("https://amirb938.s3.ir-thr-at1.arvanstorage.ir/ReorderApp.mp4"))
            prepare()
            playWhenReady = true
        }

        val playerAdapter = Media3PlayerAdapter(exo, scope)
        val network = SampleNetworkLayer(this)
        val tracking = RetryingTrackingEngine(network)

        val engine = DefaultAdEngine(
            player = playerAdapter,
            vmapParser = VmapPullParser(),
            vastParser = VastPullParser(),
            scheduler = VmapScheduler(),
            network = network,
            tracking = tracking,
            mainDispatcher = Dispatchers.Main,
        ).apply { initialize() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val playerState by playerAdapter.state.collectAsState()

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
                                PlayerView(ctx).apply { player = exo }
                            },
                        )

                        AdOverlay(
                            state = adUi,
                            onSkip = {
                                // Minimal skip: resume content immediately.
                                playerAdapter.resumeContent()
                            },
                        )
                    }

                    LaunchedEffect(Unit) {
                        val vmapXml = resources.openRawResource(R.raw.sample_vmap).bufferedReader().use { it.readText() }
                        engine.loadVmap(vmapXml)
                        engine.start()
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

