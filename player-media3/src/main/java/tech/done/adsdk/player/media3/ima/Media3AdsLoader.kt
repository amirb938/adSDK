package tech.done.adsdk.player.media3.ima

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.done.adsdk.core.DefaultAdEngine
import tech.done.adsdk.network.NetworkLayer
import tech.done.adsdk.parser.impl.VastPullParser
import tech.done.adsdk.parser.impl.VmapPullParser
import tech.done.adsdk.scheduler.VmapScheduler
import tech.done.adsdk.tracking.RetryingTrackingEngine
import tech.done.adsdk.tracking.TrackingEngine

/**
 * IMA-like AdsLoader for Media3.
 *
 * Usage:
 * - Create once, keep as a field.
 * - Call [setPlayer] with the app content player.
 * - Call [setPlayerView] to allow SDK to suppress content controllers during ads.
 * - Call [setAdDisplayContainer] for where ad video should render.
 * - Call [requestAdsFromVmapXml] (or adTagUrl variant in future) then [start].
 */
class Media3AdsLoader(
    private val network: NetworkLayer,
    private val tracking: TrackingEngine = RetryingTrackingEngine(network),
    private val scope: CoroutineScope = MainScope(),
) {
    private var contentPlayer: ExoPlayer? = null
    private var contentPlayerView: PlayerView? = null
    private var adDisplayContainer: AdDisplayContainerView? = null

    private var engine: DefaultAdEngine? = null
    private var adapter: Media3ImaLikePlayerAdapter? = null

    fun setPlayer(player: ExoPlayer?) {
        contentPlayer = player
        rebuildIfReady()
    }

    fun setPlayerView(playerView: PlayerView?) {
        contentPlayerView = playerView
        rebuildIfReady()
    }

    fun setAdDisplayContainer(container: AdDisplayContainerView?) {
        adDisplayContainer = container
        rebuildIfReady()
    }

    fun release() {
        adapter?.release()
        adapter = null
        engine?.release()
        engine = null
        contentPlayer = null
        contentPlayerView = null
        adDisplayContainer = null
    }

    fun requestAdsFromVmapXml(vmapXml: String) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setPlayerView/setAdDisplayContainer first.")
        scope.launch(Dispatchers.Main.immediate) {
            e.loadVmap(vmapXml)
        }
    }

    /**
     * IMA-like API: only a single adTagUri is provided.
     *
     * The SDK will fetch it, detect whether it is VMAP or VAST, and then start playback accordingly.
     */
    fun requestAds(adTagUri: String, timeoutMs: Long = 10_000L) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setPlayerView/setAdDisplayContainer first.")
        scope.launch(network.dispatcher) {
            val resp = network.get(adTagUri, timeoutMs = timeoutMs)
            if (!resp.isSuccessful) error("Failed to load adTagUri. code=${resp.code} url=$adTagUri")
            val xml = resp.body.orEmpty()
            val kind = detectXmlKind(xml)
            val vmapXml = when (kind) {
                XmlKind.Vmap -> xml
                XmlKind.Vast -> vastToPrerollVmap(xml)
                XmlKind.Unknown -> error("Unknown ad response. Expected VMAP or VAST. url=$adTagUri")
            }
            withContext(Dispatchers.Main.immediate) {
                e.loadVmap(vmapXml)
            }
        }
    }

    fun start() {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setPlayerView/setAdDisplayContainer first.")
        e.start()
    }

    private fun rebuildIfReady() {
        val player = contentPlayer ?: return
        val view = contentPlayerView ?: return
        val container = adDisplayContainer ?: return

        // Must run on main looper because PlayerView + ExoPlayer must be main-thread bound.
        check(Looper.getMainLooper() == Looper.myLooper()) {
            "Media3AdsLoader must be configured on the main thread."
        }

        adapter?.release()
        adapter = Media3ImaLikePlayerAdapter(
            contentPlayer = player,
            contentPlayerView = view,
            adDisplayContainer = container,
            scope = scope,
        )

        engine?.release()
        engine = DefaultAdEngine(
            player = adapter!!.playerAdapter,
            vmapParser = VmapPullParser(),
            vastParser = VastPullParser(),
            scheduler = VmapScheduler(),
            network = network,
            tracking = tracking,
            mainDispatcher = Dispatchers.Main,
        ).apply { initialize() }
    }

    private enum class XmlKind { Vmap, Vast, Unknown }

    private fun detectXmlKind(xml: String): XmlKind {
        // Find the first element name (skip XML prolog/whitespace/comments).
        val re = Regex("<\\s*([A-Za-z0-9_:-]+)(?=[\\s>/])")
        val m = re.find(xml) ?: return XmlKind.Unknown
        val tag = m.groupValues.getOrNull(1).orEmpty().substringAfter(':')
        return when (tag) {
            "VMAP" -> XmlKind.Vmap
            "VAST" -> XmlKind.Vast
            else -> XmlKind.Unknown
        }
    }

    private fun vastToPrerollVmap(vastXml: String): String {
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
}

