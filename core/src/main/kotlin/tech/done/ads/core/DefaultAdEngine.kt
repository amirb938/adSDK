package tech.done.ads.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tech.done.ads.core.internal.AdSdkDebugLog
import tech.done.ads.network.NetworkLayer
import tech.done.ads.parser.VASTParser
import tech.done.ads.parser.VMAPParser
import tech.done.ads.parser.model.SkipOffset
import tech.done.ads.parser.model.VASTAd
import tech.done.ads.player.AdsEventListener
import tech.done.ads.player.PlayerAdapter
import tech.done.ads.player.PlayerListener
import tech.done.ads.scheduler.AdScheduler
import tech.done.ads.scheduler.AdTimeline
import tech.done.ads.scheduler.ScheduledBreak
import tech.done.ads.tracking.TrackingEngine
import tech.done.ads.tracking.TrackingEvent
import kotlin.math.min

class DefaultAdEngine(
    private val player: PlayerAdapter,
    private val vmapParser: VMAPParser,
    private val vastParser: VASTParser,
    private val scheduler: AdScheduler,
    private val network: NetworkLayer,
    private val tracking: TrackingEngine,
    private val mainDispatcher: CoroutineDispatcher,
    private val tickIntervalMs: Long = 250L,
    private val bufferTimeoutMs: Long = 15_000L,
    private val maxAdAttempts: Int = 3,
    private val adsEventListener: AdsEventListener? = null,
) : AdEngine {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var tickerJob: Job? = null
    private val logTag = "Engine"

    private val _state = MutableStateFlow(AdEngineState())
    val stateFlow: StateFlow<AdEngineState> = _state.asStateFlow()

    override val state: AdState get() = _state.value

    private var timeline: AdTimeline? = null
    private val firedMidrollIds = mutableSetOf<String>()
    private var prerollFired = false
    private var postrollFired = false

    private val enginePlayerListener = object : PlayerListener {
        override fun onContentEnded() {
            AdSdkDebugLog.d(logTag, "onContentEnded inAd=${_state.value.inAd}")
            scope.launch {
                val tl = timeline ?: return@launch
                if (!postrollFired && tl.postroll.isNotEmpty()) {
                    AdSdkDebugLog.d(logTag, "Triggering postroll breaks=${tl.postroll.size}")
                    playBreaksSequentially(tl.postroll)
                    postrollFired = true
                }
            }
        }

        override fun onAppBackgrounded() {
            AdSdkDebugLog.d(logTag, "onAppBackgrounded inAd=${_state.value.inAd}")
            scope.launch {
                if (_state.value.inAd) player.pause()
            }
        }

        override fun onAppForegrounded() {
            AdSdkDebugLog.d(logTag, "onAppForegrounded inAd=${_state.value.inAd}")
            scope.launch {
                if (_state.value.inAd) player.play()
            }
        }
    }

    override suspend fun loadVMAP(xml: String) {
        AdSdkDebugLog.d(logTag, "loadVMAP xmlLength=${xml.length}")
        val parsed = withContext(mainDispatcher) { vmapParser.parse(xml) }
        AdSdkDebugLog.d(
            logTag,
            "loadVMAP parsed breaks=${parsed.adBreaks.size} version=${parsed.version}"
        )
        adsEventListener?.onVMAPParsed(parsed.version, parsed.adBreaks.size)
        val built = scheduler.buildTimeline(parsed)
        AdSdkDebugLog.d(
            logTag,
            "timeline preroll=${built.preroll.size} midroll=${built.midrolls.size} postroll=${built.postroll.size}",
        )
        timeline = built
        firedMidrollIds.clear()
        prerollFired = false
        postrollFired = false
        _state.value = _state.value.copy(loaded = true, lastError = null)

        if (_state.value.phase == AdState.Phase.Running && !prerollFired && built.preroll.isNotEmpty()) {
            scope.launch {
                AdSdkDebugLog.d(
                    logTag,
                    "Triggering preroll after loadVMAP breaks=${built.preroll.size}"
                )
                playBreaksSequentially(built.preroll)
                prerollFired = true
            }
        }
    }

    override fun initialize() {
        player.addListener(enginePlayerListener)
        AdSdkDebugLog.d(logTag, "initialize() playerListener added")
        _state.value = _state.value.copy(phase = AdState.Phase.Initialized)
    }

    override fun start() {
        AdSdkDebugLog.d(
            logTag,
            "start() loaded=${_state.value.loaded} timeline=${timeline != null}"
        )
        _state.value = _state.value.copy(phase = AdState.Phase.Running)

        scope.launch {
            val tl = timeline ?: return@launch
            if (!prerollFired && tl.preroll.isNotEmpty()) {
                AdSdkDebugLog.d(logTag, "Triggering preroll breaks=${tl.preroll.size}")
                playBreaksSequentially(tl.preroll)
                prerollFired = true
            } else {
                AdSdkDebugLog.d(
                    logTag,
                    "No preroll to trigger (prerollFired=$prerollFired prerollCount=${tl.preroll.size})"
                )
            }
        }

        tickerJob?.cancel()
        tickerJob = scope.launch {
            player.state
                .map { it.contentPositionMs }
                .distinctUntilChanged()
                .collect { posMs ->
                    if (_state.value.inAd) return@collect
                    val tl = timeline ?: return@collect
                    AdSdkDebugLog.d(logTag, "contentPosition changed posMs=$posMs")
                    triggerMidrollsIfNeeded(tl, posMs)
                }
        }

        scope.launch {
            while (_state.value.phase == AdState.Phase.Running) {
                delay(tickIntervalMs)
                if (_state.value.inAd) continue
                val tl = timeline ?: continue
                val posMs = player.state.value.contentPositionMs
                triggerMidrollsIfNeeded(tl, posMs)
            }
        }
    }

    override fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(phase = AdState.Phase.Stopped)
    }

    override fun release() {
        try {
            player.removeListener(enginePlayerListener)
        } finally {
            stop()
            _state.value = _state.value.copy(phase = AdState.Phase.Released)
            scope.cancel()
        }
    }

    private suspend fun triggerMidrollsIfNeeded(tl: AdTimeline, posMs: Long) {
        val due = tl.midrolls
            .filter {
                val t = it.triggerTimeMs
                t != null && t <= posMs
            }
            .firstOrNull { !isBreakFired(it) }
            ?: return

        AdSdkDebugLog.d(
            logTag,
            "Midroll due breakId=${due.breakId} triggerTimeMs=${due.triggerTimeMs} posMs=$posMs url=${due.vastAdTagUri}",
        )
        playBreaksSequentially(listOf(due))
        markBreakFired(due)
    }

    private fun isBreakFired(b: ScheduledBreak): Boolean {
        val key = b.breakId ?: "midroll@${b.triggerTimeMs ?: -1}"
        return firedMidrollIds.contains(key)
    }

    private fun markBreakFired(b: ScheduledBreak) {
        val key = b.breakId ?: "midroll@${b.triggerTimeMs ?: -1}"
        firedMidrollIds.add(key)
    }

    private suspend fun playBreaksSequentially(breaks: List<ScheduledBreak>) {
        AdSdkDebugLog.d(logTag, "playBreaksSequentially count=${breaks.size}")
        for (b in breaks) {
            val inline = b.vastInlineXml
            val tag = b.vastAdTagUri
            AdSdkDebugLog.d(
                logTag,
                "playSingleBreak breakId=${b.breakId} position=${b.original.position} triggerTimeMs=${b.triggerTimeMs} tag=$tag inlineBytes=${inline?.length}",
            )
            when {
                !inline.isNullOrBlank() -> playSingleBreakInline(b, inline)
                !tag.isNullOrBlank() -> playSingleBreakUrl(b, tag)
                else -> continue
            }
        }
    }

    private suspend fun playSingleBreakUrl(b: ScheduledBreak, vastTagUrl: String) {
        val bid = b.breakId
        adsEventListener?.onAdBreakLoading(bid, b.triggerTimeMs)
        AdSdkDebugLog.d(logTag, "Entering ad break breakId=$bid url=$vastTagUrl")
        adsEventListener?.onContentPauseRequested(bid)
        _state.value = _state.value.copy(inAd = true, currentBreak = b, lastError = null)
        player.setSeekingEnabled(false)

        val result = runCatching { playAdFromVastTag(vastTagUrl) }
        if (result.isFailure) {
            val err = result.exceptionOrNull()
            AdSdkDebugLog.e(logTag, "Ad break failed breakId=$bid", err)
            adsEventListener?.onAdError(bid, err)
            tracking.track(TrackingEvent.Error, emptyList())
            _state.value = _state.value.copy(lastError = err)
        } else {
            AdSdkDebugLog.d(logTag, "Ad break finished breakId=$bid")
        }

        player.setSeekingEnabled(true)
        player.resumeContent()
        _state.value = _state.value.copy(inAd = false, currentBreak = null)
        adsEventListener?.onContentResumeRequested(bid)
        AdSdkDebugLog.d(logTag, "Exited ad break breakId=$bid, resumed content")
    }

    private suspend fun playSingleBreakInline(b: ScheduledBreak, vastInlineXml: String) {
        val bid = b.breakId
        adsEventListener?.onAdBreakLoading(bid, b.triggerTimeMs)
        AdSdkDebugLog.d(
            logTag,
            "Entering ad break breakId=$bid inlineVASTBytes=${vastInlineXml.length}"
        )
        adsEventListener?.onContentPauseRequested(bid)
        _state.value = _state.value.copy(inAd = true, currentBreak = b, lastError = null)
        player.setSeekingEnabled(false)

        val result = runCatching { playAdFromVastXml(vastInlineXml) }
        if (result.isFailure) {
            val err = result.exceptionOrNull()
            AdSdkDebugLog.e(logTag, "Ad break (inline) failed breakId=$bid", err)
            adsEventListener?.onAdError(bid, err)
            tracking.track(TrackingEvent.Error, emptyList())
            _state.value = _state.value.copy(lastError = err)
        } else {
            AdSdkDebugLog.d(logTag, "Ad break (inline) finished breakId=$bid")
        }

        player.setSeekingEnabled(true)
        player.resumeContent()
        _state.value = _state.value.copy(inAd = false, currentBreak = null)
        adsEventListener?.onContentResumeRequested(bid)
        AdSdkDebugLog.d(logTag, "Exited ad break (inline) breakId=$bid, resumed content")
    }

    private suspend fun playAdFromVastTag(vastTagUrl: String) {
        AdSdkDebugLog.d(logTag, "Fetching VAST url=$vastTagUrl")
        val vastXml = fetchWithRetry(vastTagUrl)
        AdSdkDebugLog.d(logTag, "Fetched VAST bytes=${vastXml.length}")
        playAdFromVastXml(vastXml)
    }

    private suspend fun playAdFromVastXml(vastXml: String) {
        val breakId = _state.value.currentBreak?.breakId
        val ads = vastParser.parse(vastXml) { url -> fetchWithRetry(url) }
        AdSdkDebugLog.d(logTag, "Parsed VAST ads=${ads.size}")
        val first = ads.firstOrNull { it.mediaFiles.isNotEmpty() } ?: return
        val dur = first.durationMs
        if (dur != null && dur <= 0L) return

        AdSdkDebugLog.d(
            logTag,
            "Selected ad mediaFiles=${first.mediaFiles.size} durationMs=${first.durationMs} trackingKeys=${first.trackingEvents.keys.sorted()}",
        )

        val mediaUri = first.mediaFiles.first().uri
        adsEventListener?.onVASTLoaded(breakId, first.durationMs, mediaUri)

        val tracker = VASTTrackingSession(
            ad = first,
            breakId = breakId,
            adEvents = adsEventListener,
            track = { event, urls -> tracking.track(event, urls) },
            log = { msg -> AdSdkDebugLog.d(logTag, msg) },
            logError = { msg, t -> AdSdkDebugLog.e(logTag, msg, t) },
            launch = { block -> scope.launch { block() } },
        )

        tracker.fireImpression()
        adsEventListener?.onAdImpression(breakId)
        tracker.fireStart()

        val skipOffsetMs = resolveSkipOffsetToMs(first.skipOffset, dur)
        AdSdkDebugLog.d(
            logTag,
            "playAd mediaUri=$mediaUri bufferTimeoutMs=$bufferTimeoutMs skipOffsetMs=$skipOffsetMs",
        )

        adsEventListener?.onAdStarted(breakId)
        player.playAd(mediaUri, skipOffsetMs)

        val playStateJob = scope.launch {
            player.state
                .map { it.isInAd to it.isPlaying }
                .distinctUntilChanged()
                .collect { (inAd, isPlaying) ->
                    if (!inAd) return@collect
                    tracker.onIsPlayingChanged(isPlaying)
                }
        }

        val waitMs = (dur?.plus(2_000L) ?: 30_000L).coerceAtLeast(5_000L)
        AdSdkDebugLog.d(logTag, "awaitAdEnd timeoutMs=$waitMs")
        try {
            withTimeout(waitMs) {
                awaitAdEnd(
                    onProgress = { posMs, durationMs ->
                        tracker.onProgress(posMs, durationMs)
                        adsEventListener?.onAdProgress(breakId, posMs, durationMs)
                    },
                    onEnded = {
                        scope.launch {
                            tracker.fireComplete()
                            adsEventListener?.onAdCompleted(breakId)
                        }
                    },
                    onError = { t ->
                        scope.launch { tracker.fireError() }
                        adsEventListener?.onAdError(breakId, t)
                    },
                )
            }
        } finally {
            playStateJob.cancel()
        }
    }

    private suspend fun awaitAdEnd(
        onProgress: (positionMs: Long, durationMs: Long?) -> Unit,
        onEnded: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val done = CompletableDeferred<Unit>()
        val listener = object : PlayerListener {
            override fun onAdEnded() {
                onEnded()
                if (!done.isCompleted) done.complete(Unit)
            }

            override fun onAdProgress(positionMs: Long, durationMs: Long?) {
                onProgress(positionMs, durationMs)
            }

            override fun onPlayerError(throwable: Throwable) {
                onError(throwable)
                if (!done.isCompleted) done.completeExceptionally(throwable)
            }
        }

        player.addListener(listener)
        try {
            done.await()
        } finally {
            player.removeListener(listener)
        }
    }

    private class VASTTrackingSession(
        private val ad: VASTAd,
        private val breakId: String?,
        private val adEvents: AdsEventListener?,
        private val track: suspend (TrackingEvent, List<String>) -> Unit,
        private val log: (String) -> Unit,
        private val logError: (String, Throwable?) -> Unit,
        private val launch: ((suspend () -> Unit) -> Unit)? = null,
    ) {
        private val fired = HashSet<String>()
        private val listenerMilestones = HashSet<String>()
        private var lastIsPlaying: Boolean? = null

        private val progressOffsetsMs: List<Pair<Long, List<String>>> by lazy {
            val out = mutableListOf<Pair<Long, List<String>>>()
            for ((k, urls) in ad.trackingEvents) {
                if (!k.startsWith("progress@", ignoreCase = true)) continue
                val raw = k.substringAfter("@").trim()
                val ms = parseOffsetToMs(raw, ad.durationMs)
                if (ms != null) out += (ms to urls)
            }
            out.sortedBy { it.first }
        }

        suspend fun fireImpression() =
            fireOnce("impression", TrackingEvent.Impression, "impression")

        suspend fun fireStart() = fireOnce("start", TrackingEvent.Start, "start")
        suspend fun fireComplete() = fireOnce("complete", TrackingEvent.Complete, "complete")
        suspend fun fireError() = fireOnce("error", TrackingEvent.Error, "error")

        fun onIsPlayingChanged(isPlaying: Boolean) {
            val prev = lastIsPlaying
            lastIsPlaying = isPlaying
            if (prev == null) return
            if (prev && !isPlaying) {
                adEvents?.onAdPaused(breakId)
                launchFireOnce("pause", TrackingEvent.Pause, "pause")
            } else if (!prev && isPlaying) {
                adEvents?.onAdResumed(breakId)
                launchFireOnce("resume", TrackingEvent.Resume, "resume")
            }
        }

        fun onProgress(positionMs: Long, durationMs: Long?) {
            val dur = durationMs ?: ad.durationMs
            if (dur != null && dur > 0) {
                if (positionMs >= dur / 4) {
                    notifyListenerOnce("fq") { adEvents?.onAdFirstQuartile(breakId) }
                    launchFireOnce("firstquartile", TrackingEvent.FirstQuartile, "firstquartile")
                }
                if (positionMs >= dur / 2) {
                    notifyListenerOnce("mp") { adEvents?.onAdMidpoint(breakId) }
                    launchFireOnce("midpoint", TrackingEvent.Midpoint, "midpoint")
                }
                if (positionMs >= (dur * 3) / 4) {
                    notifyListenerOnce("tq") { adEvents?.onAdThirdQuartile(breakId) }
                    launchFireOnce("thirdquartile", TrackingEvent.ThirdQuartile, "thirdquartile")
                }
            }

            for ((ms, urls) in progressOffsetsMs) {
                if (positionMs >= ms) {
                    val key = "progress@$ms"
                    if (fired.add(key)) {
                        launchTrack(TrackingEvent.Progress, urls, key)
                    }
                } else break
            }
        }

        private fun notifyListenerOnce(id: String, block: () -> Unit) {
            if (listenerMilestones.add(id)) block()
        }

        private fun launchFireOnce(eventKey: String, event: TrackingEvent, mapKey: String) {
            if (fired.contains(eventKey)) return
            val urls = ad.trackingEvents[mapKey].orEmpty()
            if (urls.isEmpty()) return
            fired += eventKey
            launchTrack(event, urls, eventKey)
        }

        private suspend fun fireOnce(eventKey: String, event: TrackingEvent, mapKey: String) {
            if (!fired.add(eventKey)) return
            val urls = ad.trackingEvents[mapKey].orEmpty()
            if (urls.isEmpty()) {
                log("tracking skip event=$eventKey reason=noUrls")
                return
            }
            try {
                log("tracking fire event=$eventKey urls=${urls.size}")
                track(event, urls)
            } catch (t: Throwable) {
                logError("tracking failed event=$eventKey", t)
            }
        }

        private fun launchTrack(event: TrackingEvent, urls: List<String>, eventKey: String) {
            val launcher = launch
            if (launcher != null) {
                launcher {
                    try {
                        log("tracking fire event=$eventKey urls=${urls.size}")
                        track(event, urls)
                    } catch (t: Throwable) {
                        logError("tracking failed event=$eventKey", t)
                    }
                }
            } else {
                log("tracking skip event=$eventKey reason=noLauncher")
            }
        }

        private fun parseOffsetToMs(raw: String, durationMs: Long?): Long? {
            val v = raw.trim()
            if (v.endsWith("%")) {
                val pct = v.removeSuffix("%").trim().toIntOrNull() ?: return null
                val dur = durationMs ?: return null
                return (dur * pct) / 100L
            }
            val parts = v.split(":")
            if (parts.size != 3) return null
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val secPart = parts[2]
            val secAndMs = secPart.split(".")
            val s = secAndMs[0].toLongOrNull() ?: return null
            val ms = if (secAndMs.size > 1) secAndMs[1].padEnd(3, '0').take(3).toLongOrNull()
                ?: 0L else 0L
            return (((h * 60 + m) * 60 + s) * 1000L) + ms
        }
    }

    private fun resolveSkipOffsetToMs(skip: SkipOffset?, durationMs: Long?): Long? {
        return when (skip) {
            is SkipOffset.TimeMs -> skip.value.coerceAtLeast(0L)
            is SkipOffset.Percent -> {
                val d = durationMs ?: return null
                if (d <= 0L) return null
                (d * skip.value.toLong()) / 100L
            }

            null -> null
        }
    }

    private suspend fun fetchWithRetry(url: String): String {
        var attempt = 1
        var backoffMs = 500L
        var last: Throwable? = null

        while (attempt <= maxAdAttempts) {
            AdSdkDebugLog.d(logTag, "fetch attempt=$attempt url=$url timeoutMs=$bufferTimeoutMs")
            val resp = runCatching {
                withContext(network.dispatcher) { network.get(url, timeoutMs = bufferTimeoutMs) }
            }.onFailure { last = it }.getOrNull()

            AdSdkDebugLog.d(
                logTag,
                "fetch response attempt=$attempt url=$url code=${resp?.code} bodyBytes=${resp?.body?.length} ok=${resp?.isSuccessful}",
            )
            if (resp != null && resp.isSuccessful && !resp.body.isNullOrBlank()) {
                return resp.body.orEmpty()
            }

            if (attempt == maxAdAttempts) break
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, 4_000L)
            attempt++
        }

        AdSdkDebugLog.e(logTag, "fetchWithRetry failed url=$url attempts=$maxAdAttempts", last)
        throw last ?: IllegalStateException("Failed to fetch: $url")
    }
}
