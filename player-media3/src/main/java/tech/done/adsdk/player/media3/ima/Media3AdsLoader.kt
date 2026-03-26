package tech.done.adsdk.player.media3.ima

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.done.adsdk.core.DefaultAdEngine
import tech.done.adsdk.network.NetworkLayer
import tech.done.adsdk.player.media3.network.SampleNetworkLayer
import tech.done.adsdk.parser.impl.VastPullParser
import tech.done.adsdk.parser.impl.VmapPullParser
import tech.done.adsdk.parser.model.VmapResponse
import tech.done.adsdk.scheduler.VmapScheduler
import tech.done.adsdk.tracking.RetryingTrackingEngine
import tech.done.adsdk.tracking.TrackingEngine

/**
 * IMA-like AdsLoader for Media3.
 *
 * Usage:
 * - Create once, keep as a field.
 * - Call [setPlayer] with the app content player.
 * - Call [setAdDisplayContainer] for where ad video should render.
 * - Optional: call [setContentUi] to let the host suppress its own controls during ads.
 * - Optional: call [setAdMarkersContainerView] if the host wants "yellow ad markers" on a timebar.
 * - Optional: call [setVideoSurfaceView] if the SDK/OMSDK needs the content surface dimensions.
 * - Call [requestAdsFromVmapXml] (or adTagUrl variant in future) then [start].
 */
class Media3AdsLoader(
    private val network: NetworkLayer,
    private val tracking: TrackingEngine = RetryingTrackingEngine(network),
    private val scope: CoroutineScope = MainScope(),
) {
    constructor(
        context: Context,
        network: NetworkLayer = SampleNetworkLayer(context),
        tracking: TrackingEngine = RetryingTrackingEngine(network),
        scope: CoroutineScope = MainScope(),
    ) : this(
        network = network,
        tracking = tracking,
        scope = scope,
    )

    /**
     * Optional callbacks to let the host suppress its own content UI during ads.
     *
     * This intentionally does NOT depend on Media3's PlayerView. Hosts can implement it however
     * they render controls (Compose, custom Views, etc).
     */
    interface ContentUi {
        fun onAdStarted()
        fun onAdEnded()
    }

    private var contentPlayer: ExoPlayer? = null
    private var adDisplayContainer: AdDisplayContainerView? = null
    private var contentUi: ContentUi? = null
    private var adMarkersContainerView: View? = null
    private var videoSurfaceView: View? = null

    private var engine: DefaultAdEngine? = null
    private var adapter: Media3ImaLikePlayerAdapter? = null

    fun setPlayer(player: ExoPlayer?) {
        contentPlayer = player
        rebuildIfReady()
    }

    fun setAdDisplayContainer(container: AdDisplayContainerView?) {
        adDisplayContainer = container
        rebuildIfReady()
    }

    /**
     * Optional hook for hosts to hide/disable their content controls during ads.
     *
     * This replaces the old `setPlayerView(...)` dependency.
     */
    fun setContentUi(contentUi: ContentUi?) {
        this.contentUi = contentUi
        rebuildIfReady()
    }

    /**
     * Optional root view that contains a Media3 TimeBar (usually `R.id.exo_progress`).
     *
     * If provided, the loader will try (best-effort) to apply "yellow ad markers" like IMA.
     * This can be a `PlayerView`, a custom controls layout, or any ViewGroup containing the timebar.
     */
    fun setAdMarkersContainerView(view: View?) {
        adMarkersContainerView = view
    }

    /**
     * Optional view that represents the content video surface (e.g., a TextureView inside a FrameLayout).
     *
     * Use this if the SDK needs to query surface dimensions/position (e.g., for VPAID/OMSDK).
     */
    fun setVideoSurfaceView(view: View?) {
        videoSurfaceView = view
    }

    fun release() {
        adapter?.release()
        adapter = null
        engine?.release()
        engine = null
        contentPlayer = null
        adDisplayContainer = null
        contentUi = null
        adMarkersContainerView = null
        videoSurfaceView = null
    }

    fun requestAdsFromVmapXml(vmapXml: String) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setAdDisplayContainer first.")
        scope.launch(Dispatchers.Main.immediate) {
            applyAdMarkersFromVmapXmlOrClear(vmapXml)
            e.loadVmap(vmapXml)
        }
    }

    /**
     * IMA-like API: only a single adTagUri is provided.
     *
     * The SDK will fetch it, detect whether it is VMAP or VAST, and then start playback accordingly.
     */
    fun requestAds(adTagUri: String, timeoutMs: Long = 10_000L) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setAdDisplayContainer first.")
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
                applyAdMarkersFromVmapXmlOrClear(vmapXml)
                e.loadVmap(vmapXml)
            }
        }
    }

    fun start() {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setAdDisplayContainer first.")
        e.start()
    }

    private fun rebuildIfReady() {
        val player = contentPlayer ?: return
        val container = adDisplayContainer ?: return

        // Must run on main looper because ExoPlayer must be main-thread bound.
        check(Looper.getMainLooper() == Looper.myLooper()) {
            "Media3AdsLoader must be configured on the main thread."
        }

        adapter?.release()
        adapter = Media3ImaLikePlayerAdapter(
            contentPlayer = player,
            adDisplayContainer = container,
            scope = scope,
            contentUi = contentUi?.let { host ->
                object : Media3ImaLikePlayerAdapter.ContentUi {
                    override fun onAdStarted() = host.onAdStarted()
                    override fun onAdEnded() = host.onAdEnded()
                }
            },
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

    /**
     * Enables "yellow ad markers" on a Media3 timebar (like IMA).
     *
     * This is best-effort and depends on the concrete time bar implementation.
     */
    private fun applyAdMarkersFromVmapXmlOrClear(vmapXml: String) {
        val root = adMarkersContainerView ?: return
        val parsed: VmapResponse = runCatching { VmapPullParser().parse(vmapXml) }.getOrNull()
            ?: run {
                applyAdMarkers(root, longArrayOf())
                return
            }

        val timeline = VmapScheduler().buildTimeline(parsed)
        val markersMs = timeline.midrolls
            .mapNotNull { it.triggerTimeMs }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toLongArray()

        applyAdMarkers(root, markersMs)
    }

    private fun applyAdMarkers(rootView: View, adGroupTimesMs: LongArray) {
        // Media3 UI uses a timebar with id exo_progress in most skins.
        val timeBar = rootView.findViewById<View?>(androidx.media3.ui.R.id.exo_progress) ?: return

        // Try common signatures via reflection (API varies across media3 versions).
        val played = BooleanArray(adGroupTimesMs.size) { false }
        val cls = timeBar.javaClass

        runCatching {
            cls.getMethod(
                "setAdGroupTimesMs",
                LongArray::class.java,
                BooleanArray::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(timeBar, adGroupTimesMs, played, adGroupTimesMs.size)
            return
        }

        runCatching {
            cls.getMethod(
                "setAdGroupTimesMs",
                LongArray::class.java,
                BooleanArray::class.java,
            ).invoke(timeBar, adGroupTimesMs, played)
            return
        }

        // Optional: marker colors (best-effort).
        runCatching {
            cls.getMethod("setAdMarkerColor", Int::class.javaPrimitiveType).invoke(timeBar, 0xFFFFC107.toInt()) // amber
        }
        runCatching {
            cls.getMethod("setPlayedAdMarkerColor", Int::class.javaPrimitiveType).invoke(timeBar, 0xFFFFC107.toInt())
        }
    }

    /**
     * Helper for integrations that need the content surface size without a PlayerView.
     *
     * Returns the last known laid-out size (0 if not laid out yet).
     */
    fun getVideoSurfaceSizePx(): Pair<Int, Int> {
        val v = videoSurfaceView ?: return 0 to 0
        return v.width to v.height
    }
}

