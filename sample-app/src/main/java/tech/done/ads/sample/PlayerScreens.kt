package tech.done.ads.sample

import android.content.Context
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
import kotlinx.coroutines.flow.collectLatest
import tech.done.ads.player.PlayerState
import tech.done.ads.player.media3.ima.AdDisplayContainerView
import tech.done.ads.player.media3.ima.Media3AdsLoader

@Composable
fun SimpleVastScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimpleVast,
    )
}

@Composable
fun SimpleVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimpleVmap,
    )
}

@Composable
fun CustomUiVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.CustomUiVmap,
    )
}

@Composable
fun SimidVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimidVmap,
    )
}

@Composable
fun SimidVmapNoSkipScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimidVmapNoSkip,
    )
}

private enum class PlayerScenario {
    SimpleVast,
    SimpleVmap,
    CustomUiVmap,
    SimidVmap,
    SimidVmapNoSkip,
}

@Composable
private fun PlayerScenarioScreen(
    scenario: PlayerScenario,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val scope = remember { MainScope() }
    var playerState by remember { mutableStateOf(PlayerState()) }

    val (exo, adsLoader) = remember(scenario) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            // Keep the same "content" video for all scenarios.
            setMediaItem(
                MediaItem.fromUri(SampleConfig.Urls.CONTENT_VIDEO),
            )
            prepare()
            playWhenReady = true
        }

        val loader = Media3AdsLoader.builder(context)
            .scope(scope)
            .debugLogging(true)
            .build()
        loader.addAdSdkEventListener(SampleAdsEventLogger())

        val showBuiltInOverlay = scenario != PlayerScenario.CustomUiVmap
        loader.setShowBuiltInAdOverlay(showBuiltInOverlay)

        exoPlayer to loader
    }

    DisposableEffect(scenario) {
        onDispose {
            runCatching { adsLoader.release() }
            runCatching { exo.stop() }
            runCatching { exo.clearMediaItems() }
            runCatching { exo.release() }
            runCatching { scope.cancel() }
        }
    }

    LaunchedEffect(adsLoader) {
        adsLoader.playerState.collectLatest { playerState = it }
    }

    LaunchedEffect(scenario, adsLoader) {
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
        adsLoader.requestAdsFromVMAPXml(xml)
        adsLoader.start()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    AdDisplayContainerView(ctx).also { container ->
                        adsLoader.setAdDisplayContainer(container)
                    }
                },
            )

            if (scenario == PlayerScenario.CustomUiVmap) {
                SampleCustomAdOverlay(
                    playerState = playerState,
                    onSkip = { adsLoader.skipCurrentAd() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun Context.readAssetText(name: String): String =
    assets.open(name).bufferedReader().use { it.readText() }

private fun wrapVastAsVmapPreroll(vastXml: String): String {
    val vast = vastXml
        .trimStart()
        .removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        .removePrefix("<?xml version='1.0' encoding='UTF-8'?>")
        .trimStart()
    return """
        <vmap:VMAP xmlns:vmap="http://www.iab.net/videosuite/vmap" version="1.0">
          <vmap:AdBreak breakType="linear" timeOffset="00:00:00" breakId="preroll-1">
            <vmap:AdSource allowMultipleAds="false" followRedirects="true">
              <vmap:VASTAdData>
                $vast
              </vmap:VASTAdData>
            </vmap:AdSource>
          </vmap:AdBreak>
        </vmap:VMAP>
    """.trimIndent()
}

