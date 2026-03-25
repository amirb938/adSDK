package tech.done.adsdk.core

import tech.done.adsdk.core.internal.AdSdkDebugLog
import tech.done.adsdk.network.NetworkLayer
import tech.done.adsdk.parser.VastParser
import tech.done.adsdk.parser.VmapParser
import tech.done.adsdk.parser.model.Position
import tech.done.adsdk.parser.model.VastAd
import tech.done.adsdk.scheduler.AdScheduler
import tech.done.adsdk.scheduler.AdTimeline
import tech.done.adsdk.scheduler.ScheduledBreak
import tech.done.adsdk.tracking.TrackingEngine
import tech.done.adsdk.tracking.TrackingEvent
import tech.done.adsdk.player.PlayerAdapter
import tech.done.adsdk.player.PlayerListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.math.min

class DefaultAdEngine(
    private val player: PlayerAdapter,
    private val vmapParser: VmapParser,
    private val vastParser: VastParser,
    private val scheduler: AdScheduler,
    private val network: NetworkLayer,
    private val tracking: TrackingEngine,
    private val mainDispatcher: CoroutineDispatcher,
    private val tickIntervalMs: Long = 250L,
    private val bufferTimeoutMs: Long = 15_000L,
    private val maxAdAttempts: Int = 3,
) : AdEngine {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var tickerJob: Job? = null
    private val logTag = "Engine"

    private val _state = MutableStateFlow<AdEngineState>(AdEngineState())
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

    override suspend fun loadVmap(xml: String) {
        AdSdkDebugLog.d(logTag, "loadVmap xmlLength=${xml.length}")
        val parsed = withContext(mainDispatcher) { vmapParser.parse(xml) }
        AdSdkDebugLog.d(logTag, "loadVmap parsed breaks=${parsed.adBreaks.size} version=${parsed.version}")
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
    }

    override fun initialize() {
        player.addListener(enginePlayerListener)
        AdSdkDebugLog.d(logTag, "initialize() playerListener added")
        _state.value = _state.value.copy(phase = AdState.Phase.Initialized)
    }

    override fun start() {
        AdSdkDebugLog.d(logTag, "start() loaded=${_state.value.loaded} timeline=${timeline != null}")
        _state.value = _state.value.copy(phase = AdState.Phase.Running)

        // Preroll is triggered once at start (if loaded).
        scope.launch {
            val tl = timeline ?: return@launch
            if (!prerollFired && tl.preroll.isNotEmpty()) {
                AdSdkDebugLog.d(logTag, "Triggering preroll breaks=${tl.preroll.size}")
                playBreaksSequentially(tl.preroll)
                prerollFired = true
            } else {
                AdSdkDebugLog.d(logTag, "No preroll to trigger (prerollFired=$prerollFired prerollCount=${tl.preroll.size})")
            }
        }

        tickerJob?.cancel()
        tickerJob = scope.launch {
            // Observe content position changes and trigger midrolls precisely once.
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

        // Fallback tick loop for players that don't update position often.
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
        // Choose the earliest due midroll that hasn't fired.
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
            // Ignore empty ad breaks defensively (scheduler already filters).
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
        AdSdkDebugLog.d(logTag, "Entering ad break breakId=${b.breakId} url=$vastTagUrl")
        _state.value = _state.value.copy(inAd = true, currentBreak = b, lastError = null)
        player.setSeekingEnabled(false)

        // If anything fails, resume content.
        val result = runCatching { playAdFromVastTag(vastTagUrl) }
        if (result.isFailure) {
            AdSdkDebugLog.e(logTag, "Ad break failed breakId=${b.breakId}", result.exceptionOrNull())
            tracking.track(TrackingEvent.Error, emptyList())
            _state.value = _state.value.copy(lastError = result.exceptionOrNull())
        } else {
            AdSdkDebugLog.d(logTag, "Ad break finished breakId=${b.breakId}")
        }

        player.setSeekingEnabled(true)
        player.resumeContent()
        _state.value = _state.value.copy(inAd = false, currentBreak = null)
        AdSdkDebugLog.d(logTag, "Exited ad break breakId=${b.breakId}, resumed content")
    }

    private suspend fun playSingleBreakInline(b: ScheduledBreak, vastInlineXml: String) {
        AdSdkDebugLog.d(logTag, "Entering ad break breakId=${b.breakId} inlineVastBytes=${vastInlineXml.length}")
        _state.value = _state.value.copy(inAd = true, currentBreak = b, lastError = null)
        player.setSeekingEnabled(false)

        val result = runCatching { playAdFromVastXml(vastInlineXml) }
        if (result.isFailure) {
            AdSdkDebugLog.e(logTag, "Ad break (inline) failed breakId=${b.breakId}", result.exceptionOrNull())
            tracking.track(TrackingEvent.Error, emptyList())
            _state.value = _state.value.copy(lastError = result.exceptionOrNull())
        } else {
            AdSdkDebugLog.d(logTag, "Ad break (inline) finished breakId=${b.breakId}")
        }

        player.setSeekingEnabled(true)
        player.resumeContent()
        _state.value = _state.value.copy(inAd = false, currentBreak = null)
        AdSdkDebugLog.d(logTag, "Exited ad break (inline) breakId=${b.breakId}, resumed content")
    }

    private suspend fun playAdFromVastTag(vastTagUrl: String) {
        AdSdkDebugLog.d(logTag, "Fetching VAST url=$vastTagUrl")
        val vastXml = fetchWithRetry(vastTagUrl)
        AdSdkDebugLog.d(logTag, "Fetched VAST bytes=${vastXml.length}")
        playAdFromVastXml(vastXml)
    }

    private suspend fun playAdFromVastXml(vastXml: String) {
        val ads = vastParser.parse(vastXml) { url -> fetchWithRetry(url) }
        AdSdkDebugLog.d(logTag, "Parsed VAST ads=${ads.size}")
        val first = ads.firstOrNull { it.mediaFiles.isNotEmpty() } ?: return // empty ad -> ignore
        val dur = first.durationMs
        if (dur != null && dur <= 0L) return // zero-length ad -> skip

        AdSdkDebugLog.d(
            logTag,
            "Selected ad mediaFiles=${first.mediaFiles.size} durationMs=${first.durationMs} trackingKeys=${first.trackingEvents.keys.sorted()}",
        )

        val tracker = VastTrackingSession(
            ad = first,
            track = { event, urls -> tracking.track(event, urls) },
            log = { msg -> AdSdkDebugLog.d(logTag, msg) },
            logError = { msg, t -> AdSdkDebugLog.e(logTag, msg, t) },
            launch = { block -> scope.launch { block() } },
        )

        // Fire initial events once we have selected the ad.
        tracker.fireImpression()
        tracker.fireStart()

        val mediaUri = first.mediaFiles.first().uri
        AdSdkDebugLog.d(logTag, "playAd mediaUri=$mediaUri bufferTimeoutMs=$bufferTimeoutMs")

        // Start playback (non-blocking) then await ad end signal.
        player.playAd(mediaUri)

        // Observe isPlaying changes during ad (pause/resume).
        val playStateJob = scope.launch {
            player.state
                .map { it.isInAd to it.isPlaying }
                .distinctUntilChanged()
                .collect { (inAd, isPlaying) ->
                    if (!inAd) return@collect
                    tracker.onIsPlayingChanged(isPlaying)
                }
        }

        // Wait up to ad duration (+ small padding) if known, otherwise fall back to a reasonable cap.
        val waitMs = (dur?.plus(2_000L) ?: 30_000L).coerceAtLeast(5_000L)
        AdSdkDebugLog.d(logTag, "awaitAdEnd timeoutMs=$waitMs")
        try {
            withTimeout(waitMs) {
                awaitAdEnd(
                    onProgress = { posMs, durationMs -> tracker.onProgress(posMs, durationMs) },
                    onEnded = { scope.launch { tracker.fireComplete() } },
                    onError = { t -> scope.launch { tracker.fireError() }.invokeOnCompletion { /* ignore */ }; throw t },
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

    private class VastTrackingSession(
        private val ad: VastAd,
        private val track: suspend (TrackingEvent, List<String>) -> Unit,
        private val log: (String) -> Unit,
        private val logError: (String, Throwable?) -> Unit,
        private val launch: ((suspend () -> Unit) -> Unit)? = null,
    ) {
        private val fired = HashSet<String>()
        private var lastIsPlaying: Boolean? = null

        // Parse progress offsets once.
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

        suspend fun fireImpression() = fireOnce("impression", TrackingEvent.Impression, "impression")
        suspend fun fireStart() = fireOnce("start", TrackingEvent.Start, "start")
        suspend fun fireComplete() = fireOnce("complete", TrackingEvent.Complete, "complete")
        suspend fun fireError() = fireOnce("error", TrackingEvent.Error, "error")

        fun onIsPlayingChanged(isPlaying: Boolean) {
            val prev = lastIsPlaying
            lastIsPlaying = isPlaying
            if (prev == null) return
            // In-ad pause/resume heuristic based on isPlaying changes.
            if (prev && !isPlaying) {
                launchFireOnce("pause", TrackingEvent.Pause, "pause")
            } else if (!prev && isPlaying) {
                launchFireOnce("resume", TrackingEvent.Resume, "resume")
            }
        }

        fun onProgress(positionMs: Long, durationMs: Long?) {
            val dur = durationMs ?: ad.durationMs
            if (dur != null && dur > 0) {
                if (positionMs >= dur / 4) launchFireOnce("firstquartile", TrackingEvent.FirstQuartile, "firstquartile")
                if (positionMs >= dur / 2) launchFireOnce("midpoint", TrackingEvent.Midpoint, "midpoint")
                if (positionMs >= (dur * 3) / 4) launchFireOnce("thirdquartile", TrackingEvent.ThirdQuartile, "thirdquartile")
            }

            // progress@offset
            for ((ms, urls) in progressOffsetsMs) {
                if (positionMs >= ms) {
                    val key = "progress@$ms"
                    if (fired.add(key)) {
                        launchTrack(TrackingEvent.Progress, urls, key)
                    }
                } else break
            }
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
            // Fire asynchronously; engine playback should not be blocked by tracking.
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
                // If no launcher is provided (shouldn't happen), just skip async fire.
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
            // HH:MM:SS(.mmm)
            val parts = v.split(":")
            if (parts.size != 3) return null
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val secPart = parts[2]
            val secAndMs = secPart.split(".")
            val s = secAndMs[0].toLongOrNull() ?: return null
            val ms = if (secAndMs.size > 1) secAndMs[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
            return (((h * 60 + m) * 60 + s) * 1000L) + ms
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
                // NetworkResponse.body is a public API across modules; avoid smart-cast issues.
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

