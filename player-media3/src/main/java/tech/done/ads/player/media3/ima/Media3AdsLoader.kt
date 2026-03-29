package tech.done.ads.player.media3.ima

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.done.ads.AdSdkLogConfig
import tech.done.ads.player.AdsEventListener
import tech.done.ads.player.AdsEventMulticaster
import tech.done.ads.player.PlayerListener
import tech.done.ads.player.PlayerState
import tech.done.ads.core.DefaultAdEngine
import tech.done.ads.network.NetworkLayer
import tech.done.ads.player.media3.network.SampleNetworkLayer
import tech.done.ads.parser.impl.VASTPullParser
import tech.done.ads.parser.impl.VMAPPullParser
import tech.done.ads.parser.model.VMAPResponse
import tech.done.ads.scheduler.VMAPScheduler
import tech.done.ads.tracking.RetryingTrackingEngine
import tech.done.ads.tracking.TrackingEngine
import tech.done.ads.player.media3.ima.internal.AdPlaybackSignal
import java.util.concurrent.CopyOnWriteArraySet

class Media3AdsLoader(
    private val network: NetworkLayer,
    private val tracking: TrackingEngine = RetryingTrackingEngine(network),
    private val scope: CoroutineScope = MainScope(),
    debugLogging: Boolean = false,
) {
    init {
        AdSdkLogConfig.isDebugLoggingEnabled = debugLogging
    }

    constructor(
        context: Context,
        network: NetworkLayer = SampleNetworkLayer(context),
        tracking: TrackingEngine = RetryingTrackingEngine(network),
        scope: CoroutineScope = MainScope(),
        debugLogging: Boolean = false,
    ) : this(
        network = network,
        tracking = tracking,
        scope = scope,
        debugLogging = debugLogging,
    )

    interface ContentUi {
        fun onAdStarted()
        fun onAdEnded()
    }

    interface AdPlaybackListener {
        fun onAdStarted()
        fun onAdEnded()
        fun onAdError(t: Throwable?)
    }

    private val adSdkEventMulticaster = AdsEventMulticaster()

    private var contentPlayer: ExoPlayer? = null
    private var adDisplayContainer: AdDisplayContainerView? = null
    private var contentUi: ContentUi? = null
    private var adMarkersContainerView: View? = null
    private var videoSurfaceView: View? = null

    private var engine: DefaultAdEngine? = null
    private var adapter: Media3ImaLikePlayerAdapter? = null
    private var uiConfig: AdSdkUiConfig? = null
    private var showBuiltInAdOverlay: Boolean = true

    private val _isAdPlaying = MutableStateFlow(false)
    val isAdPlaying: StateFlow<Boolean> = _isAdPlaying.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val adPlaybackListeners = CopyOnWriteArraySet<AdPlaybackListener>()
    private var adPlaybackSignal = AdPlaybackSignal()
    private var observeJob: Job? = null
    private var adapterPlayerListener: PlayerListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addAdSdkEventListener(listener: AdsEventListener) {
        adSdkEventMulticaster.addListener(listener)
    }

    fun removeAdSdkEventListener(listener: AdsEventListener) {
        adSdkEventMulticaster.removeListener(listener)
    }

    fun addListener(listener: AdPlaybackListener) {
        adPlaybackListeners += listener
    }

    fun removeListener(listener: AdPlaybackListener) {
        adPlaybackListeners -= listener
    }

    fun setPlayer(player: ExoPlayer?) {
        contentPlayer = player
        rebuildIfReady()
    }

    fun setAdDisplayContainer(container: AdDisplayContainerView?) {
        adDisplayContainer = container
        rebuildIfReady()
    }

    fun setContentUi(contentUi: ContentUi?) {
        this.contentUi = contentUi
        rebuildIfReady()
    }

    fun setAdMarkersContainerView(view: View?) {
        adMarkersContainerView = view
    }

    fun setVideoSurfaceView(view: View?) {
        videoSurfaceView = view
    }

    fun setUiConfig(config: AdSdkUiConfig) {
        uiConfig = config
        rebuildIfReady()
    }

    fun setShowBuiltInAdOverlay(show: Boolean) {
        showBuiltInAdOverlay = show
        rebuildIfReady()
    }

    fun skipCurrentAd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { skipCurrentAd() }
            return
        }
        adapter?.playerAdapter?.resumeContent()
    }

    fun release() {
        val finalEvents = adPlaybackSignal.onReleased()
        if (Looper.getMainLooper() == Looper.myLooper()) {
            applyAdPlaybackEvents(finalEvents)
            adPlaybackListeners.clear()
            adSdkEventMulticaster.clear()
        } else {
            mainHandler.post {
                applyAdPlaybackEvents(finalEvents)
                adPlaybackListeners.clear()
                adSdkEventMulticaster.clear()
            }
        }

        runCatching { observeJob?.cancel() }
        observeJob = null
        adapterPlayerListener?.let { l ->
            runCatching { adapter?.playerAdapter?.removeListener(l) }
        }
        adapterPlayerListener = null

        adapter?.release()
        adapter = null
        engine?.release()
        engine = null
        _playerState.value = PlayerState()
        contentPlayer = null
        adDisplayContainer = null
        contentUi = null
        adMarkersContainerView = null
        videoSurfaceView = null
    }

    fun requestAdsFromVMAPXml(vmapXml: String) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setAdDisplayContainer first.")
        scope.launch(Dispatchers.Main.immediate) {
            applyAdMarkersFromVMAPXmlOrClear(vmapXml)
            e.loadVMAP(vmapXml)
        }
    }

    @Deprecated("Use requestAdsFromVMAPXml", ReplaceWith("requestAdsFromVMAPXml(vmapXml)"))
    fun requestAdsFromVmapXml(vmapXml: String) = requestAdsFromVMAPXml(vmapXml)

    fun requestAds(adTagUri: String, timeoutMs: Long = 10_000L) {
        val e = engine ?: error("Media3AdsLoader is not ready. Call setPlayer/setAdDisplayContainer first.")
        scope.launch(network.dispatcher) {
            val resp = network.get(adTagUri, timeoutMs = timeoutMs)
            if (!resp.isSuccessful) error("Failed to load adTagUri. code=${resp.code} url=$adTagUri")
            val xml = resp.body.orEmpty()
            val kind = detectXmlKind(xml)
            val vmapXml = when (kind) {
                XmlKind.VMAP -> xml
                XmlKind.VAST -> wrapVASTAsVMAPPreroll(xml)
                XmlKind.Unknown -> error("Unknown ad response. Expected VMAP or VAST. url=$adTagUri")
            }
            withContext(Dispatchers.Main.immediate) {
                applyAdMarkersFromVMAPXmlOrClear(vmapXml)
                e.loadVMAP(vmapXml)
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

        check(Looper.getMainLooper() == Looper.myLooper()) {
            "Media3AdsLoader must be configured on the main thread."
        }

        applyAdPlaybackEvents(adPlaybackSignal.onReleased())

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
            uiConfig = uiConfig,
            showBuiltInAdOverlay = showBuiltInAdOverlay,
        )

        runCatching { observeJob?.cancel() }
        observeJob = null

        adapterPlayerListener?.let { l ->
            runCatching { adapter?.playerAdapter?.removeListener(l) }
        }
        adapterPlayerListener = object : PlayerListener {
            override fun onPlayerError(throwable: Throwable) {
                scope.launch(Dispatchers.Main.immediate) {
                    applyAdPlaybackEvents(adPlaybackSignal.onAdError(throwable))
                }
            }
        }.also { adapter!!.playerAdapter.addListener(it) }

        observeJob = scope.launch(Dispatchers.Main.immediate) {
            adapter!!.state.collectLatest { s ->
                _playerState.value = s
                applyAdPlaybackEvents(
                    adPlaybackSignal.onPlayerState(
                        inAd = s.isInAd,
                        isPlaying = s.isPlaying,
                    ),
                )
            }
        }

        engine?.release()
        engine = DefaultAdEngine(
            player = adapter!!.playerAdapter,
            vmapParser = VMAPPullParser(),
            vastParser = VASTPullParser(),
            scheduler = VMAPScheduler(),
            network = network,
            tracking = tracking,
            mainDispatcher = Dispatchers.Main,
            adsEventListener = adSdkEventMulticaster,
        ).apply { initialize() }
    }

    private fun applyAdPlaybackEvents(events: List<AdPlaybackSignal.Event>) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            scope.launch(Dispatchers.Main.immediate) { applyAdPlaybackEvents(events) }
            return
        }

        for (e in events) {
            when (e) {
                is AdPlaybackSignal.Event.Started -> {
                    _isAdPlaying.value = true
                    adPlaybackListeners.forEach { it.onAdStarted() }
                }
                is AdPlaybackSignal.Event.Ended -> {
                    _isAdPlaying.value = false
                    adPlaybackListeners.forEach { it.onAdEnded() }
                }
                is AdPlaybackSignal.Event.Error -> {
                    adPlaybackListeners.forEach { it.onAdError(e.throwable) }
                }
            }
        }
    }

    private enum class XmlKind { VMAP, VAST, Unknown }

    private fun detectXmlKind(xml: String): XmlKind {
        val re = Regex("<\\s*([A-Za-z0-9_:-]+)(?=[\\s>/])")
        val m = re.find(xml) ?: return XmlKind.Unknown
        val tag = m.groupValues.getOrNull(1).orEmpty().substringAfter(':')
        return when (tag) {
            "VMAP" -> XmlKind.VMAP
            "VAST" -> XmlKind.VAST
            else -> XmlKind.Unknown
        }
    }

    private fun wrapVASTAsVMAPPreroll(vastXml: String): String {
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

    private fun applyAdMarkersFromVMAPXmlOrClear(vmapXml: String) {
        val root = adMarkersContainerView ?: return
        val parsed: VMAPResponse = runCatching { VMAPPullParser().parse(vmapXml) }.getOrNull()
            ?: run {
                applyAdMarkers(root, longArrayOf())
                return
            }

        val timeline = VMAPScheduler().buildTimeline(parsed)
        val markersMs = timeline.midrolls
            .mapNotNull { it.triggerTimeMs }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toLongArray()

        applyAdMarkers(root, markersMs)
    }

    private fun applyAdMarkers(rootView: View, adGroupTimesMs: LongArray) {
        val timeBar = rootView.findViewById<View?>(androidx.media3.ui.R.id.exo_progress) ?: return

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

        runCatching {
            cls.getMethod("setAdMarkerColor", Int::class.javaPrimitiveType).invoke(timeBar, 0xFFFFC107.toInt())
        }
        runCatching {
            cls.getMethod("setPlayedAdMarkerColor", Int::class.javaPrimitiveType).invoke(timeBar, 0xFFFFC107.toInt())
        }
    }

    fun getVideoSurfaceSizePx(): Pair<Int, Int> {
        val v = videoSurfaceView ?: return 0 to 0
        return v.width to v.height
    }
}
