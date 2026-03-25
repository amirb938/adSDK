package com.example.adsdk.core

import com.example.adsdk.network.NetworkLayer
import com.example.adsdk.parser.VastParser
import com.example.adsdk.parser.VmapParser
import com.example.adsdk.parser.model.Position
import com.example.adsdk.scheduler.AdScheduler
import com.example.adsdk.scheduler.AdTimeline
import com.example.adsdk.scheduler.ScheduledBreak
import com.example.adsdk.tracking.TrackingEngine
import com.example.adsdk.tracking.TrackingEvent
import com.example.adsdk.player.PlayerAdapter
import com.example.adsdk.player.PlayerListener
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

    private val _state = MutableStateFlow<AdEngineState>(AdEngineState())
    val stateFlow: StateFlow<AdEngineState> = _state.asStateFlow()

    override val state: AdState get() = _state.value

    private var timeline: AdTimeline? = null
    private val firedMidrollIds = mutableSetOf<String>()
    private var prerollFired = false
    private var postrollFired = false

    private val enginePlayerListener = object : PlayerListener {
        override fun onContentEnded() {
            scope.launch {
                val tl = timeline ?: return@launch
                if (!postrollFired && tl.postroll.isNotEmpty()) {
                    playBreaksSequentially(tl.postroll)
                    postrollFired = true
                }
            }
        }

        override fun onAppBackgrounded() {
            scope.launch {
                if (_state.value.inAd) player.pause()
            }
        }

        override fun onAppForegrounded() {
            scope.launch {
                if (_state.value.inAd) player.play()
            }
        }
    }

    override suspend fun loadVmap(xml: String) {
        val parsed = withContext(mainDispatcher) { vmapParser.parse(xml) }
        val built = scheduler.buildTimeline(parsed)
        timeline = built
        firedMidrollIds.clear()
        prerollFired = false
        postrollFired = false
        _state.value = _state.value.copy(loaded = true, lastError = null)
    }

    override fun initialize() {
        player.addListener(enginePlayerListener)
        _state.value = _state.value.copy(phase = AdState.Phase.Initialized)
    }

    override fun start() {
        _state.value = _state.value.copy(phase = AdState.Phase.Running)

        // Preroll is triggered once at start (if loaded).
        scope.launch {
            val tl = timeline ?: return@launch
            if (!prerollFired && tl.preroll.isNotEmpty()) {
                playBreaksSequentially(tl.preroll)
                prerollFired = true
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
        for (b in breaks) {
            // Ignore empty ad breaks defensively (scheduler already filters).
            val tag = b.vastAdTagUri ?: continue
            playSingleBreak(b, tag)
        }
    }

    private suspend fun playSingleBreak(b: ScheduledBreak, vastTagUrl: String) {
        _state.value = _state.value.copy(inAd = true, currentBreak = b, lastError = null)
        player.setSeekingEnabled(false)

        // If anything fails, resume content.
        val result = runCatching { playAdFromVastTag(vastTagUrl) }
        if (result.isFailure) {
            tracking.track(TrackingEvent.Error, emptyList())
            _state.value = _state.value.copy(lastError = result.exceptionOrNull())
        }

        player.setSeekingEnabled(true)
        player.resumeContent()
        _state.value = _state.value.copy(inAd = false, currentBreak = null)
    }

    private suspend fun playAdFromVastTag(vastTagUrl: String) {
        val vastXml = fetchWithRetry(vastTagUrl)
        val ads = vastParser.parse(vastXml) { url -> fetchWithRetry(url) }
        val first = ads.firstOrNull { it.mediaFiles.isNotEmpty() } ?: return // empty ad -> ignore
        val dur = first.durationMs
        if (dur != null && dur <= 0L) return // zero-length ad -> skip

        // Basic tracking hooks (URLs mapping depends on your VAST parser merge output).
        tracking.track(TrackingEvent.Impression, first.trackingEvents["impression"].orEmpty())
        tracking.track(TrackingEvent.Start, first.trackingEvents["start"].orEmpty())

        val mediaUri = first.mediaFiles.first().uri

        withTimeout(bufferTimeoutMs) {
            player.playAd(mediaUri)
        }
    }

    private suspend fun fetchWithRetry(url: String): String {
        var attempt = 1
        var backoffMs = 500L
        var last: Throwable? = null

        while (attempt <= maxAdAttempts) {
            val resp = runCatching {
                withContext(network.dispatcher) { network.get(url, timeoutMs = bufferTimeoutMs) }
            }.onFailure { last = it }.getOrNull()

            if (resp != null && resp.isSuccessful && !resp.body.isNullOrBlank()) {
                val body = resp.body
                if (body != null) return body
            }

            if (attempt == maxAdAttempts) break
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, 4_000L)
            attempt++
        }

        throw last ?: IllegalStateException("Failed to fetch: $url")
    }
}

