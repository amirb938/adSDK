package tech.done.adsdk.scheduler

import tech.done.adsdk.parser.model.AdBreak
import tech.done.adsdk.parser.model.VmapResponse

interface AdScheduler {
    fun buildTimeline(vmap: VmapResponse): AdTimeline
}

data class AdTimeline(
    val preroll: List<ScheduledBreak> = emptyList(),
    val midrolls: List<ScheduledBreak> = emptyList(),
    val postroll: List<ScheduledBreak> = emptyList(),
)

data class ScheduledBreak(
    val breakId: String?,
    val original: AdBreak,
    val triggerTimeMs: Long?, // null for postroll (END) and preroll (0) may be 0
    val vastAdTagUri: String?,
)

