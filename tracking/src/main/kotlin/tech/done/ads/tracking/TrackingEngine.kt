package tech.done.ads.tracking

import kotlinx.coroutines.CoroutineDispatcher

interface TrackingEngine {
    val dispatcher: CoroutineDispatcher

    suspend fun track(event: TrackingEvent, urls: List<String>)
}

enum class TrackingEvent {
    Impression,
    Start,
    FirstQuartile,
    Midpoint,
    ThirdQuartile,
    Progress,
    Pause,
    Resume,
    Complete,
    Error,
}

