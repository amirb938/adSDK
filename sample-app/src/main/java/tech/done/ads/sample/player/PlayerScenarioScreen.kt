package tech.done.ads.sample.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import tech.done.ads.player.PlayerState
import tech.done.ads.player.media3.ima.AdDisplayContainerView
import tech.done.ads.player.media3.ima.Media3AdsLoader
import tech.done.ads.sample.SampleAdsEventLogger
import tech.done.ads.sample.SampleConfig
import tech.done.ads.sample.SampleCustomAdOverlay

@Composable
fun PlayerScenarioScreen(
    scenario: PlayerScenario,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val scope = remember { MainScope() }
    var playerState by remember { mutableStateOf(PlayerState()) }

    val (exo, loader) = remember(scenario) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(SampleConfig.Urls.CONTENT_VIDEO))
            prepare()
            playWhenReady = true
        }

        val ads = Media3AdsLoader.builder(context)
            .scope(scope)
            .debugLogging(true)
            .build()

        val showBuiltInOverlay = scenario != PlayerScenario.CustomUiVmap
        ads.setShowBuiltInAdOverlay(showBuiltInOverlay)

        val loader: ScenarioLoader =
            object : Media3AdsLoaderBacked {
                override val playerState: StateFlow<PlayerState> = ads.playerState
                override fun addLogger() = ads.addAdSdkEventListener(SampleAdsEventLogger())
                override fun requestAdsFromVMAPXml(xml: String) = ads.requestAdsFromVMAPXml(xml)
                override fun start() = ads.start()
                override fun skipCurrentAd() = ads.skipCurrentAd()
                override fun release() = ads.release()

                override fun attachPlayer(player: ExoPlayer, playerView: PlayerView) {
                    playerView.player = player
                    ads.setPlayer(player)
                    ads.setAdMarkersContainerView(playerView)
                }

                override fun attachContainer(container: AdDisplayContainerView) {
                    ads.setAdDisplayContainer(container)
                }
            }

        loader.addLogger()

        exoPlayer to loader
    }

    DisposableEffect(scenario) {
        onDispose {
            runCatching { loader.release() }
            runCatching { exo.stop() }
            runCatching { exo.clearMediaItems() }
            runCatching { exo.release() }
            runCatching { scope.cancel() }
        }
    }

    LaunchedEffect(loader) {
        loader.playerState.collectLatest { playerState = it }
    }

    LaunchedEffect(scenario, loader) {
        val xml = when (scenario) {
            PlayerScenario.SimpleVast -> {
                val vast = context.readAssetText(SampleConfig.Assets.VAST)
                wrapVastAsVmapPreroll(vast)
            }

            PlayerScenario.SimpleVmap -> context.readAssetText(SampleConfig.Assets.VMAP)
            PlayerScenario.CustomUiVmap -> context.readAssetText(SampleConfig.Assets.VMAP)
            PlayerScenario.SimidVmap -> context.readAssetText(SampleConfig.Assets.VMAP_SIMID)
            PlayerScenario.SimidVmapNoSkip -> context.readAssetText(SampleConfig.Assets.VMAP_SIMID_NO_SKIP)
        }
        loader.requestAdsFromVMAPXml(xml)
        loader.start()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        (loader as? Media3AdsLoaderBacked)?.attachPlayer(exo, this)
                    }
                },
            )

            if (loader is Media3AdsLoaderBacked) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        AdDisplayContainerView(ctx).also { container ->
                            loader.attachContainer(container)
                        }
                    },
                )
            }

            if (scenario == PlayerScenario.CustomUiVmap) {
                SampleCustomAdOverlay(
                    playerState = playerState,
                    onSkip = { loader.skipCurrentAd() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

