package tech.done.ads.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.done.ads.AdSdkLogConfig
import tech.done.ads.network.DefaultNetworkLayer
import tech.done.ads.network.NetworkLayer
import tech.done.ads.parser.impl.VASTPullParser
import tech.done.ads.parser.impl.VMAPPullParser
import tech.done.ads.parser.model.VMAPResponse
import tech.done.ads.player.AdsEventListener
import tech.done.ads.player.AdsEventMulticaster
import tech.done.ads.player.ExternalPlayerAdapter
import tech.done.ads.player.PlayerAdapter
import tech.done.ads.player.PlayerCommandListener
import tech.done.ads.player.PlayerState
import tech.done.ads.scheduler.VMAPScheduler
import tech.done.ads.tracking.RetryingTrackingEngine
import tech.done.ads.tracking.TrackingEngine

class AdsLoader private constructor(
    private val network: NetworkLayer,
    private val tracking: TrackingEngine,
    private val scope: CoroutineScope,
    private val playerAdapter: PlayerAdapter,
    private val externalAdsEventListener: AdsEventListener?,
    debugLogging: Boolean,
) {
    init {
        AdSdkLogConfig.isDebugLoggingEnabled = debugLogging
    }

    class Builder {
        private var network: NetworkLayer? = null
        private var tracking: TrackingEngine? = null
        private var scope: CoroutineScope? = null
        private var playerAdapter: PlayerAdapter? = null
        private var adsEventListener: AdsEventListener? = null
        private var debugLogging: Boolean = false

        fun network(network: NetworkLayer) = apply { this.network = network }
        fun tracking(tracking: TrackingEngine) = apply { this.tracking = tracking }
        fun scope(scope: CoroutineScope) = apply { this.scope = scope }
        fun playerAdapter(playerAdapter: PlayerAdapter) = apply { this.playerAdapter = playerAdapter }
        fun adsEventListener(listener: AdsEventListener?) = apply { this.adsEventListener = listener }
        fun debugLogging(enabled: Boolean) = apply { this.debugLogging = enabled }

        fun build(): AdsLoader {
            val pa = requireNotNull(playerAdapter) { "playerAdapter must be provided" }
            val net = network ?: DefaultNetworkLayer()
            val tr = tracking ?: RetryingTrackingEngine(net)
            val sc = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            return AdsLoader(net, tr, sc, pa, adsEventListener, debugLogging)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun createWithExternalPlayer(
            commands: PlayerCommandListener? = null,
            network: NetworkLayer? = null,
            tracking: TrackingEngine? = null,
            scope: CoroutineScope? = null,
            debugLogging: Boolean = false,
            adsEventListener: AdsEventListener? = null,
        ): ExternalSetup {
            val commandListener = commands ?: object : PlayerCommandListener {
                override fun onPlayAdRequested(
                    mediaUri: String,
                    adSkipOffsetMs: Long?,
                    simidInteractiveCreativeUrl: String?,
                ) {
                }

                override fun onResumeContentRequested() {
                }

                override fun onPauseRequested() {
                }

                override fun onPlayRequested() {
                }

                override fun onSeekingEnabledChanged(enabled: Boolean) {
                }
            }
            val playerAdapter = ExternalPlayerAdapter(commandListener)
            val loader = AdsLoader.builder()
                .playerAdapter(playerAdapter)
                .adsEventListener(adsEventListener)
                .debugLogging(debugLogging)
                .apply {
                    if (network != null) network(network)
                    if (tracking != null) tracking(tracking)
                    if (scope != null) scope(scope)
                }
                .build()
            return ExternalSetup(loader, playerAdapter)
        }
    }

    data class ExternalSetup(
        val adsLoader: AdsLoader,
        val playerAdapter: ExternalPlayerAdapter,
    )

    private val adsEventMulticaster = AdsEventMulticaster().also { m ->
        externalAdsEventListener?.let { m.addListener(it) }
    }

    val playerState: StateFlow<PlayerState> = playerAdapter.state

    val isAdPlaying: StateFlow<Boolean> =
        playerAdapter.state
            .map { it.isInAd }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    private val engine: DefaultAdEngine =
        DefaultAdEngine(
            player = playerAdapter,
            vmapParser = VMAPPullParser(),
            vastParser = VASTPullParser(),
            scheduler = VMAPScheduler(),
            network = network,
            tracking = tracking,
            mainDispatcher = Dispatchers.Main,
            adsEventListener = adsEventMulticaster,
        ).apply { initialize() }

    val engineState: StateFlow<AdEngineState> = engine.stateFlow

    fun addAdSdkEventListener(listener: AdsEventListener) {
        adsEventMulticaster.addListener(listener)
    }

    fun removeAdSdkEventListener(listener: AdsEventListener) {
        adsEventMulticaster.removeListener(listener)
    }

    fun requestAdsFromVMAPXml(vmapXml: String) {
        scope.launch(Dispatchers.Main.immediate) {
            engine.loadVMAP(vmapXml)
        }
    }

    fun requestAds(adTagUri: String, timeoutMs: Long = 10_000L) {
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
                engine.loadVMAP(vmapXml)
            }
        }
    }

    fun start() {
        engine.start()
    }

    fun stop() {
        engine.stop()
    }

    fun skipCurrentAd() {
        playerAdapter.resumeContent()
    }

    fun release() {
        adsEventMulticaster.clear()
        engine.release()
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

    fun parseVMAP(xml: String): VMAPResponse = VMAPPullParser().parse(xml)
}

